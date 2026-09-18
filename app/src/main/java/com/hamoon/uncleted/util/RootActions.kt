package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.crypto.StrongBoxSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootActions {

    private const val TAG = "RootActions"

    private const val ADB_BASE = "/data/adb"
    private const val MODULES_DIR = "$ADB_BASE/modules"
    private const val SERVICE_DIR = "$ADB_BASE/service.d"
    private const val POST_MOUNT_DIR = "$ADB_BASE/post-mount.d"
    private const val BACKUP_DIR = "$ADB_BASE/uncleted"

    enum class WipeLevel {
        STANDARD_WIPE,
        FAST_USERDATA,
        SYSTEM_DESTRUCTION,
        OS_SUICIDE,
        NUCLEAR_WINTER
    }

    suspend fun performSecureWipePlus(context: Context) {
        executeWipeProtocol(context, WipeLevel.FAST_USERDATA)
    }

    suspend fun rebootDevice(context: Context) {
        Log.w(TAG, "ROOT: Hardware reboot requested.")
        EventLogger.log(context, "ROOT: Hardware reboot command executed.")
        val result = RootExecutor.run("reboot")
        if (!result.isSuccess) {
            RootExecutor.run("/system/bin/reboot")
        }
    }

    suspend fun executeWipeProtocol(context: Context, level: WipeLevel): Unit = withContext(Dispatchers.IO) {
        Log.e(TAG, "ROOT: Executing destruction protocol - Level: $level")
        EventLogger.log(context, "ROOT: Executing destruction level: $level")

        // 1. Isolate radio and network interfaces
        blockAllNetworkTraffic(context)

        // 2. Destroy discrete Titan M2/StrongBox silicon master key
        StrongBoxSecurityManager.executeMasterKeySuicide(context)

        when (level) {
            WipeLevel.STANDARD_WIPE -> {
                EmergencyDestructionEngine.stageRecoveryWipeCommand()
                val rebootResult = RootExecutor.run("reboot recovery")
                if (!rebootResult.isSuccess) {
                    EmergencyDestructionEngine.triggerPlatformRecoveryWipe(context, "Root_Standard_Wipe")
                }
            }

            WipeLevel.FAST_USERDATA -> {
                // Sub-millisecond cryptographic metadata zeroing and Vold key shredding
                EmergencyDestructionEngine.evictAndZeroEncryptionKeys()
                EmergencyDestructionEngine.stageRecoveryWipeCommand()

                val rebootResult = RootExecutor.run("reboot recovery")
                if (!rebootResult.isSuccess) {
                    val platformSuccess = EmergencyDestructionEngine.triggerPlatformRecoveryWipe(context, "Root_Secure_Wipe")
                    if (!platformSuccess) {
                        EmergencyDestructionEngine.executeKernelRebootFallback()
                    }
                }
            }

            WipeLevel.SYSTEM_DESTRUCTION, WipeLevel.OS_SUICIDE -> {
                // Cryptographically shred data and zero boot/ramdisk partitions
                EmergencyDestructionEngine.evictAndZeroEncryptionKeys()

                val bootPartitions = listOf("boot", "boot_a", "boot_b", "vendor_boot", "vendor_boot_a", "vendor_boot_b", "init_boot")
                for (part in bootPartitions) {
                    val path = EmergencyDestructionEngine.findPartitionBlockPath(part)
                    if (path != null) {
                        RootExecutor.run("dd if=/dev/zero of=$path bs=1048576 count=8 conv=fsync", logErrors = false)
                    }
                }

                RootExecutor.run("sync", logErrors = false)
                RootExecutor.run("reboot", logErrors = false)
                EmergencyDestructionEngine.executeKernelRebootFallback()
            }

            WipeLevel.NUCLEAR_WINTER -> {
                // Erase encryption metadata and wipe partition headers across critical partitions
                EmergencyDestructionEngine.evictAndZeroEncryptionKeys()

                val allPartitions = listOf("boot", "boot_a", "boot_b", "vendor_boot", "init_boot", "recovery", "vbmeta", "vbmeta_system", "misc", "userdata", "metadata")
                for (part in allPartitions) {
                    val path = EmergencyDestructionEngine.findPartitionBlockPath(part)
                    if (path != null) {
                        RootExecutor.run("dd if=/dev/zero of=$path bs=4096 count=1024 conv=fsync", logErrors = false)
                    }
                }

                RootExecutor.run("sync", logErrors = false)
                EmergencyDestructionEngine.stageRecoveryWipeCommand()
                RootExecutor.run("reboot bootloader", logErrors = false)

                // Force unconditional hardware reboot via SysRq trigger
                RootExecutor.run("echo 1 > /proc/sys/kernel/sysrq", logErrors = false)
                RootExecutor.run("echo c > /proc/sysrq-trigger", logErrors = false)
            }
        }
        Unit
    }

    suspend fun convertToSystemApp(context: Context): Boolean = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(context.packageName, 0)
        val sourceApk = appInfo.sourceDir
        val pkgName = context.packageName

        val provider = RootChecker.getRootProvider()
        Log.i(TAG, "ROOT: Starting universal systemless integration ($provider) for $pkgName (v8.0.1)")

        val permissionsXmlPath = "${context.filesDir.parent}/privapp-permissions-uncleted.xml"
        val permissionsXmlContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <permissions>
                <privapp-permissions package="$pkgName">
                    <permission name="android.permission.MASTER_CLEAR"/>
                    <permission name="android.permission.REBOOT"/>
                    <permission name="android.permission.WRITE_SECURE_SETTINGS"/>
                    <permission name="android.permission.INTERACT_ACROSS_USERS_FULL"/>
                    <permission name="android.permission.INTERACT_ACROSS_USERS"/>
                    <permission name="android.permission.MANAGE_USERS"/>
                    <permission name="android.permission.STATUS_BAR_SERVICE"/>
                    <permission name="android.permission.PACKAGE_USAGE_STATS"/>
                    <permission name="android.permission.MANAGE_USB"/>
                    <permission name="android.permission.MANAGE_DEVICE_ADMINS"/>
                </privapp-permissions>
            </permissions>
        """.trimIndent()

        File(permissionsXmlPath).writeText(permissionsXmlContent)

        val moduleId = "uncleted_privapp"
        val modulePath = "$MODULES_DIR/$moduleId"
        val targetPrivAppDir = "$modulePath/system/priv-app/UncleTed"
        val targetLibDir = "$targetPrivAppDir/lib/arm64"
        val targetEtcDir = "$modulePath/system/etc/permissions"
        val bootScriptPath = "$SERVICE_DIR/uncleted_boot.sh"
        val postMountScriptPath = "$POST_MOUNT_DIR/uncleted_boot.sh"

        // Boot script: mounts overlayfs if metamodule is missing and only purges /data/app if /system/priv-app is confirmed active
        val serviceScriptContent = """
            #!/system/bin/sh
            export PATH="/system/bin:/system/xbin:/vendor/bin:${'$'}PATH"
            (
                LOG="/data/adb/uncleted/boot.log"
                mkdir -p /data/adb/uncleted
                echo "[${'$'}(date)] Uncle Ted boot service active (v8.0.1)" > "${'$'}LOG"

                # Early-boot fallback mount for KernelSU without metamodule
                if [ ! -f "/system/priv-app/UncleTed/UncleTed.apk" ] && [ -f "$modulePath/system/priv-app/UncleTed/UncleTed.apk" ]; then
                    echo "[${'$'}(date)] /system/priv-app not mounted. Attempting overlayfs fallback..." >> "${'$'}LOG"
                    mount -t overlay overlay -o lowerdir=$modulePath/system/priv-app:/system/priv-app /system/priv-app 2>/dev/null || true
                fi

                while [ "${'$'}(getprop sys.boot_completed)" != "1" ]; do
                    sleep 2
                done

                WAIT_SECS=0
                while [ "${'$'}(getprop sys.user.0.ce_available)" != "true" ] && [ ! -d "/data/user/0" ]; do
                    if [ "${'$'}(getprop vold.decrypt)" = "trigger_restart_framework" ] || [ ${'$'}WAIT_SECS -ge 45 ]; then
                        break
                    fi
                    sleep 2
                    WAIT_SECS=${'$'}((WAIT_SECS + 2))
                done

                sleep 3

                # Only purge /data/app user-space duplicates IF /system/priv-app mount is verified active!
                if [ -f "/system/priv-app/UncleTed/UncleTed.apk" ]; then
                    echo "[${'$'}(date)] /system/priv-app active. Purging /data/app user override..." >> "${'$'}LOG"
                    find /data/app -type d -name "*$pkgName*" -exec rm -rf {} + 2>/dev/null || true
                    cmd package install-existing --user 0 "$pkgName" >> "${'$'}LOG" 2>&1 || pm install-existing --user 0 "$pkgName" >> "${'$'}LOG" 2>&1 || true
                    pm enable --user 0 "$pkgName" >/dev/null 2>&1 || true
                else
                    echo "[${'$'}(date)] WARNING: /system/priv-app not mounted! Retaining /data/app to avoid app deletion." >> "${'$'}LOG"
                fi

                if command -v ksud >/dev/null 2>&1; then
                    ksud profile set $pkgName --allow-su true >/dev/null 2>&1 || true
                    ksud profile set $pkgName allow.su true >/dev/null 2>&1 || true
                fi
                if command -v apd >/dev/null 2>&1; then
                    apd profile set $pkgName --allow-su true >/dev/null 2>&1 || true
                    apd profile set $pkgName allow.su true >/dev/null 2>&1 || true
                fi
            ) &
        """.trimIndent()

        val tempBootScript = File(context.cacheDir, "uncleted_boot.sh")
        tempBootScript.writeText(serviceScriptContent)

        val commands = listOf(
            "mkdir -p $targetPrivAppDir",
            "mkdir -p $targetLibDir",
            "mkdir -p $targetEtcDir",
            "mkdir -p $SERVICE_DIR",
            "mkdir -p $POST_MOUNT_DIR",
            "mkdir -p $BACKUP_DIR",
            "cp -f \"$sourceApk\" \"$targetPrivAppDir/UncleTed.apk\"",
            "cp -f \"$sourceApk\" \"$BACKUP_DIR/UncleTed.apk\"",
            "cp -f \"$permissionsXmlPath\" \"$targetEtcDir/privapp-permissions-uncleted.xml\"",
            // Extract native ARM64 libraries from APK to priv-app lib directory
            "unzip -j -o \"$sourceApk\" \"lib/arm64-v8a/*\" -d \"$targetLibDir\" 2>/dev/null || true",
            "chmod 755 $targetPrivAppDir",
            "chmod 644 $targetPrivAppDir/UncleTed.apk",
            "chmod 755 $targetPrivAppDir/lib 2>/dev/null || true",
            "chmod 755 $targetLibDir 2>/dev/null || true",
            "chmod 644 $targetLibDir/*.so 2>/dev/null || true",
            "chmod 644 $BACKUP_DIR/UncleTed.apk",
            "chmod 755 $targetEtcDir",
            "chmod 644 $targetEtcDir/privapp-permissions-uncleted.xml",
            "chown -R 0:0 $modulePath",
            "chcon -R u:object_r:system_file:s0 $modulePath/system",
            "echo 'id=$moduleId' > $modulePath/module.prop",
            "echo 'name=UncleTed System Priv-App & Hook' >> $modulePath/module.prop",
            "echo 'version=v8.0.1' >> $modulePath/module.prop",
            "echo 'versionCode=8' >> $modulePath/module.prop",
            "echo 'author=Hamoon Soleimani' >> $modulePath/module.prop",
            "echo 'description=Universal systemless integration into /system/priv-app.' >> $modulePath/module.prop",
            "cp -f \"${tempBootScript.absolutePath}\" \"$bootScriptPath\"",
            "chmod 755 $bootScriptPath",
            "chown 0:0 $bootScriptPath",
            "cp -f \"${tempBootScript.absolutePath}\" \"$postMountScriptPath\"",
            "chmod 755 $postMountScriptPath",
            "chown 0:0 $postMountScriptPath",
            // DO NOT delete /data/app here! The boot script will delete it upon reboot once /system/priv-app is confirmed mounted.
            "sync"
        )

        val result = RootExecutor.runMultiple(commands)
        File(permissionsXmlPath).delete()
        tempBootScript.delete()

        if (result.all { it.isSuccess }) {
            EventLogger.log(context, "ROOT: Universal Priv-App module configured ($provider). Reboot required.")
            rebootDevice(context)
            return@withContext true
        }

        Log.e(TAG, "Failed creating universal overlay module.")
        return@withContext false
    }

    suspend fun toggleUnkillableService(context: Context, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val pkgName = context.packageName
        val serviceName = "$pkgName.services.MonitoringService"
        val scriptName = "99uncleted_daemon.sh"
        val targetPath = "$SERVICE_DIR/$scriptName"

        if (!enable) {
            RootExecutor.run("rm -f $targetPath")
            return@withContext true
        }

        val hasServiceDir = RootExecutor.run("ls -d $SERVICE_DIR").isSuccess
        if (!hasServiceDir) {
            RootExecutor.run("mkdir -p $SERVICE_DIR")
        }

        val scriptContent = """
            #!/system/bin/sh
            sleep 20
            while true; do
                if pm list packages --user 0 2>/dev/null | grep -q $pkgName; then
                    if ! pgrep -f $pkgName > /dev/null; then
                        am start-foreground-service -n $pkgName/$serviceName --es REASON "PERSISTENCE_DAEMON"
                    fi
                fi
                sleep 15
            done
        """.trimIndent()

        val tempScript = File(context.cacheDir, scriptName)
        tempScript.writeText(scriptContent)

        val commands = listOf(
            "cp -f ${tempScript.absolutePath} $targetPath",
            "chmod 755 $targetPath",
            "chown 0:0 $targetPath",
            "rm -f ${tempScript.absolutePath}"
        )

        val result = RootExecutor.runMultiple(commands)
        return@withContext result.all { it.isSuccess }
    }

    suspend fun toggleProcessHiding(context: Context, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val packageName = context.packageName
        val provider = RootChecker.getRootProvider()

        when (provider) {
            RootChecker.RootProvider.MAGISK -> {
                val magiskCmd = if (enable) "magisk --denylist add $packageName" else "magisk --denylist rm $packageName"
                var result = RootExecutor.run(magiskCmd)
                if (!result.isSuccess) {
                    val hideCmd = if (enable) "magiskhide add $packageName" else "magiskhide rm $packageName"
                    result = RootExecutor.run(hideCmd)
                }
                return@withContext result.isSuccess
            }
            RootChecker.RootProvider.KERNEL_SU, RootChecker.RootProvider.APATCH -> {
                return@withContext true
            }
            else -> {
                val genericResult = RootExecutor.run(if (enable) "magiskhide add $packageName" else "magiskhide rm $packageName")
                return@withContext genericResult.isSuccess
            }
        }
    }

    suspend fun blockAllNetworkTraffic(context: Context) {
        RadioIsolationManager.isolateAllCommunications(context)
    }

    suspend fun takeStealthScreenshot(context: Context): File? = withContext(Dispatchers.IO) {
        val tempDir = "/data/local/tmp"
        val fileName = "sc_${System.currentTimeMillis()}.png"
        val tempFile = "$tempDir/$fileName"
        val finalFile = File(context.filesDir, fileName)

        val capResult = RootExecutor.run("screencap -p \"$tempFile\"")
        if (capResult.isSuccess) {
            val mvResult = RootExecutor.run("mv \"$tempFile\" \"${finalFile.absolutePath}\"")
            if (mvResult.isSuccess) {
                RootExecutor.run("chmod 600 \"${finalFile.absolutePath}\"")
                return@withContext finalFile
            }
        }
        return@withContext null
    }

    suspend fun setMockLocationConfig(context: Context, enable: Boolean) {
        val value = if (enable) "1" else "0"
        RootExecutor.run("settings put secure mock_location $value")
        val pkg = context.packageName
        val opMode = if (enable) "allow" else "deny"
        RootExecutor.run("appops set $pkg android:mock_location $opMode")
    }

    suspend fun suppressPrivacyIndicators(suppress: Boolean = true) {
        if (suppress) {
            Log.d(TAG, "Privacy indicator suppression requested without killing cameraserver.")
        }
    }
}

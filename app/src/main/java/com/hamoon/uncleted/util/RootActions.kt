package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootActions {

    private const val TAG = "RootActions"

    // Unified module directory used by Magisk, KernelSU, KernelSU-Next, and APatch
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
                val recoveryCommandFile = "/cache/recovery/command"
                RootExecutor.run("mkdir -p /cache/recovery")
                RootExecutor.run("echo '--wipe_data' > $recoveryCommandFile")
                RootExecutor.run("chmod 644 $recoveryCommandFile")
                RootExecutor.run("sync")

                val rebootResult = RootExecutor.run("reboot recovery")
                if (!rebootResult.isSuccess) {
                    EmergencyDestructionEngine.triggerPlatformRecoveryWipe(context, "Root_Standard_Wipe")
                }
            }

            WipeLevel.FAST_USERDATA -> {
                EmergencyDestructionEngine.evictAndZeroEncryptionKeys()

                val recoveryCommandFile = "/cache/recovery/command"
                RootExecutor.run("mkdir -p /cache/recovery")
                RootExecutor.run("echo '--wipe_data' > $recoveryCommandFile")
                RootExecutor.run("chmod 644 $recoveryCommandFile")
                RootExecutor.run("sync")

                val rebootResult = RootExecutor.run("reboot recovery")
                if (!rebootResult.isSuccess) {
                    EmergencyDestructionEngine.triggerPlatformRecoveryWipe(context, "Root_Secure_Wipe")
                }
            }

            WipeLevel.SYSTEM_DESTRUCTION -> {
                EmergencyDestructionEngine.evictAndZeroEncryptionKeys()

                val bootPartitions = listOf("boot", "boot_a", "boot_b", "vendor_boot", "vendor_boot_a", "vendor_boot_b", "init_boot")
                for (part in bootPartitions) {
                    val path = EmergencyDestructionEngine.findPartitionBlockPath(part)
                    if (path != null) {
                        RootExecutor.run("dd if=/dev/zero of=$path bs=1048576 count=8 conv=fsync")
                    }
                }

                RootExecutor.run("sync")
                RootExecutor.run("reboot")
            }

            WipeLevel.NUCLEAR_WINTER -> {
                EmergencyDestructionEngine.destroyPrimaryBlockHeaders()

                val allPartitions = listOf("boot", "recovery", "vbmeta", "vbmeta_system", "misc", "userdata", "metadata")
                for (part in allPartitions) {
                    val path = EmergencyDestructionEngine.findPartitionBlockPath(part)
                    if (path != null) {
                        RootExecutor.run("dd if=/dev/zero of=$path bs=4096 count=1024 conv=fsync")
                    }
                }

                RootExecutor.run("sync")
                RootExecutor.run("reboot bootloader")

                RootExecutor.run("echo 1 > /proc/sys/kernel/sysrq")
                RootExecutor.run("echo c > /proc/sysrq-trigger")
            }
        }
        Unit
    }

    /**
     * Universal Systemless Priv-App Converter:
     * Builds an overlay module compliant with Magisk, KernelSU, KernelSU-Next, and APatch.
     * Enforces dual-installation, proper SELinux contexts, and app profile whitelisting.
     */
    suspend fun convertToSystemApp(context: Context): Boolean = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(context.packageName, 0)
        val sourceApk = appInfo.sourceDir
        val pkgName = context.packageName

        val provider = RootChecker.getRootProvider()
        Log.i(TAG, "ROOT: Starting universal systemless integration ($provider) for $pkgName")

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
                </privapp-permissions>
            </permissions>
        """.trimIndent()

        File(permissionsXmlPath).writeText(permissionsXmlContent)

        val moduleId = "uncleted_privapp"
        val modulePath = "$MODULES_DIR/$moduleId"
        val targetPrivAppDir = "$modulePath/system/priv-app/UncleTed"
        val targetEtcDir = "$modulePath/system/etc/permissions"

        val serviceScriptContent = """
            #!/system/bin/sh
            until [ "$(getprop sys.boot_completed)" = "1" ]; do
                sleep 3
            done
            APK_PATH="$targetPrivAppDir/UncleTed.apk"
            if [ -f "${'$'}APK_PATH" ]; then
                pm install -r -d -g "${'$'}APK_PATH" >/dev/null 2>&1 || true
            fi
            if command -v ksud >/dev/null 2>&1; then
                ksud profile set $pkgName --allow-su >/dev/null 2>&1 || true
                ksud profile set $pkgName allow.su true >/dev/null 2>&1 || true
            fi
        """.trimIndent()

        val serviceScriptPath = "$modulePath/service.sh"

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
            "chcon -R u:object_r:system_file:s0 $modulePath/system",
            "echo 'id=$moduleId' > $modulePath/module.prop",
            "echo 'name=UncleTed System Priv-App & Hook' >> $modulePath/module.prop",
            "echo 'version=v3.0.1' >> $modulePath/module.prop",
            "echo 'versionCode=3' >> $modulePath/module.prop",
            "echo 'author=Hamoon Soleimani' >> $modulePath/module.prop",
            "echo 'description=Systemless integration into /system/priv-app with dual-install out-of-the-box support.' >> $modulePath/module.prop",
            "cat << 'EOF' > $serviceScriptPath\n$serviceScriptContent\nEOF",
            "chmod 755 $serviceScriptPath",
            "chcon u:object_r:system_file:s0 $serviceScriptPath",
            // Dual-install into data/app to eliminate KernelSU namespace isolation failures
            "pm install -r -d -g \"$sourceApk\" || true"
        )

        val result = RootExecutor.runMultiple(commands)
        File(permissionsXmlPath).delete()

        if (result.all { it.isSuccess }) {
            EventLogger.log(context, "ROOT: Universal Priv-App module configured ($provider). Reboot required.")
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

    suspend fun toggleProcessHiding(context: Context, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val packageName = context.packageName
        val provider = RootChecker.getRootProvider()

        Log.i(TAG, "Configuring process hiding under provider: $provider")

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
            RootChecker.RootProvider.KERNEL_SU -> {
                Log.i(TAG, "KernelSU active: App profile is enforced natively.")
                return@withContext true
            }
            RootChecker.RootProvider.APATCH -> {
                Log.i(TAG, "APatch active: App isolation is enforced natively via SuperKey profile.")
                return@withContext true
            }
            else -> {
                val genericResult = RootExecutor.run(if (enable) "magiskhide add $packageName" else "magiskhide rm $packageName")
                return@withContext genericResult.isSuccess
            }
        }
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

        val avbState = RootExecutor.run("getprop ro.boot.avb_version").output.firstOrNull() ?: ""
        val verifiedBootState = RootExecutor.run("getprop ro.boot.verifiedbootstate").output.firstOrNull() ?: ""

        if (avbState.isNotEmpty() && verifiedBootState != "orange") {
            Log.e(TAG, "BLOCKED: Modifying recovery partition directly on AVB 2.0 locked state will brick device.")
            EventLogger.log(context, "SECURITY: Flashing aborted. Device has active AVB 2.0 protection.")
            return@withContext false
        }

        val tempScriptPath = "/data/local/tmp/loader.sh"
        val recoveryPartition = EmergencyDestructionEngine.findPartitionBlockPath("recovery")
            ?: return@withContext false

        if (RootExecutor.run("curl -L -o $tempScriptPath '$loaderUrl'").isSuccess) {
            EventLogger.log(context, "ROOT: Staging loader to recovery partition.")
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
            Log.d(TAG, "Privacy indicator suppression requested without killing cameraserver.")
        }
    }
}
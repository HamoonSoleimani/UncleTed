package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BootloaderHardeningHelper {

    private const val TAG = "BootloaderHardening"

    suspend fun deployEarlyBootUsbKillScript(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!RootChecker.isDeviceRooted()) {
            Log.w(TAG, "Root required to stage early-boot post-mount scripts.")
            return@withContext false
        }

        val postMountDir = "/data/adb/post-mount.d"
        val scriptContent = """
            #!/system/bin/sh
            # Uncle Ted Early-Boot Hardware USB Cutoff
            # Executed in Stage-2 post-mount before Zygote/adbd starts
            setprop sys.usb.config none
            setprop sys.usb.state none
            for udc in /sys/class/udc/*; do
                echo '' > "${'$'}udc/state" 2>/dev/null || true
            done
            for dwc in /sys/devices/platform/soc/*.dwc3/mode; do
                echo 'none' > "${'$'}dwc" 2>/dev/null || true
            done
        """.trimIndent()

        val tempScript = File(context.cacheDir, "00_uncleted_early_usb_kill.sh")
        tempScript.writeText(scriptContent)

        val installCmds = listOf(
            "mkdir -p $postMountDir",
            "cp -f ${tempScript.absolutePath} $postMountDir/00_uncleted_early_usb_kill.sh",
            "chmod 755 $postMountDir/00_uncleted_early_usb_kill.sh",
            "chown 0:0 $postMountDir/00_uncleted_early_usb_kill.sh",
            "rm -f ${tempScript.absolutePath}"
        )

        val result = RootExecutor.runMultiple(installCmds, logErrors = false)
        val success = result.all { it.isSuccess }
        if (success) {
            EventLogger.log(context, "INIT: Stage-2 early boot USB PHY kill script deployed to /data/adb/post-mount.d/.")
        }
        return@withContext success
    }

    suspend fun stageRecoveryBoobyTrap(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!RootChecker.isDeviceRooted()) return@withContext false

        val bcbCmd = listOf(
            "mkdir -p /cache/recovery",
            "echo '--wipe_data\n--reason=UncleTed_Recovery_BoobyTrap' > /cache/recovery/command",
            "chmod 644 /cache/recovery/command",
            "chown 0:0 /cache/recovery/command"
        )
        val result = RootExecutor.runMultiple(bcbCmd, logErrors = false)
        val success = result.all { it.isSuccess }
        if (success) {
            EventLogger.log(context, "RECOVERY: Staged BCB wipe trigger in /cache/recovery/command.")
        }
        return@withContext success
    }

    suspend fun exportAvbSigningInstructions(context: Context): String = withContext(Dispatchers.IO) {
        val outDir = File(context.filesDir, "avb_instructions")
        if (!outDir.exists()) outDir.mkdirs()

        val instructionsFile = File(outDir, "AVB_CUSTOM_RELOCK_GUIDE.txt")
        instructionsFile.writeText(
            """
            ====================================================================
            AUTHENTIC AVB 2.0 HARDWARE ROOT-OF-TRUST LOCKING
            ====================================================================
            1. Extract the public key digest from your private OS signing key:
               avbtool extract_public_key --key custom_key.pem --output pkmd.bin

            2. Flash key digest to device hardware non-volatile storage:
               fastboot flashing set-installed-pkg-key pkmd.bin

            3. Re-lock bootloader and enforce verified boot:
               fastboot flashing lock

            4. Verify state on boot:
               adb shell getprop ro.boot.verifiedbootstate
               (Result must return 'green')
            ====================================================================
            """.trimIndent()
        )
        return@withContext instructionsFile.absolutePath
    }
}
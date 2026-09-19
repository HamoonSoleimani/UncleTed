package com.hamoon.uncleted.util

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BootloaderHardeningHelper {

    private const val TAG = "BootloaderHardening"

    enum class PlatformSecurityModel {
        SAMSUNG_KNOX,
        AOSP_AVB_CUSTOM_LOCKABLE,
        GENERIC_UNSUPPORTED
    }

    data class DeviceAvbDiagnostics(
        val platform: PlatformSecurityModel,
        val manufacturer: String,
        val model: String,
        val isCustomAvbSupported: Boolean,
        val warningMessage: String
    )

    fun isSamsungDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("samsung") || brand.contains("samsung")
    }

    fun getDeviceDiagnostics(): DeviceAvbDiagnostics {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL

        return if (isSamsungDevice()) {
            DeviceAvbDiagnostics(
                platform = PlatformSecurityModel.SAMSUNG_KNOX,
                manufacturer = manufacturer,
                model = model,
                isCustomAvbSupported = false,
                warningMessage = "Samsung devices do not support AOSP Fastboot or user-enrolled AVB 2.0 root-of-trust keys. " +
                        "Flashing custom binaries permanently trips the hardware Knox eFuse (0x1). Attempting to re-lock the " +
                        "bootloader while running modified firmware (Magisk/KernelSU/TWRP) will trigger 'SECURE CHECK FAIL: vbmeta' " +
                        "and cause a soft-brick requiring a full stock firmware re-flash via Odin."
            )
        } else {
            val isPixelOrSupported = manufacturer.contains("google", ignoreCase = true) ||
                    manufacturer.contains("fairphone", ignoreCase = true) ||
                    manufacturer.contains("oneplus", ignoreCase = true)

            DeviceAvbDiagnostics(
                platform = if (isPixelOrSupported) PlatformSecurityModel.AOSP_AVB_CUSTOM_LOCKABLE else PlatformSecurityModel.GENERIC_UNSUPPORTED,
                manufacturer = manufacturer,
                model = model,
                isCustomAvbSupported = isPixelOrSupported,
                warningMessage = if (isPixelOrSupported) {
                    "Device supports AOSP Fastboot key enrollment via 'fastboot flashing set-installed-pkg-key'. " +
                            "You can lock the bootloader while maintaining a custom root of trust."
                } else {
                    "Device vendor may not support user-set custom root-of-trust keys. Verify vendor fastboot capabilities before attempting lock."
                }
            )
        }
    }

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

    suspend fun exportAvbSigningInstructions(context: Context, forceGenericAosp: Boolean = false): String = withContext(Dispatchers.IO) {
        val outDir = File(context.filesDir, "avb_instructions")
        if (!outDir.exists()) outDir.mkdirs()

        val isSamsung = isSamsungDevice()
        val instructionsFile: File
        val content: String

        if (isSamsung && !forceGenericAosp) {
            instructionsFile = File(outDir, "SAMSUNG_KNOX_SECURITY_ADVISORY.txt")
            content = """
            ====================================================================
            UNCLE TED HARDWARE ADVISORY: SAMSUNG KNOX & BOOTLOADER LOCKING
            ====================================================================
            DEVICE DETECTED: ${Build.MANUFACTURER} ${Build.MODEL}
            
            1. FASTBOOT INCOMPATIBILITY:
               Samsung devices do not utilize standard AOSP Fastboot mode.
               Samsung firmware interfaces exclusively via proprietary Download Mode
               (Odin / Loke / S-Boot). Fastboot commands will not execute.

            2. HARDWARE KNOX EFUSE (0x1) CONSEQUENCE:
               Flashing custom kernels (KernelSU, Magisk) or custom recoveries (TWRP)
               physically and permanently blows a microscopic hardware fuse inside the SoC.
               Once tripped, hardware Knox state cannot be restored to 0x0.

            3. DO NOT ATTEMPT TO RELOCK THE BOOTLOADER ON CUSTOM FIRMWARE:
               Attempting to relock a Samsung device containing modified partitions 
               (boot, vbmeta, recovery) will trigger an immediate secure boot panic:
                 >> "SECURE CHECK FAIL: vbmeta" or "SW REV CHECK FAIL"
               The device will enter a bootloop and will not boot Android until full
               stock Samsung firmware is reflashed via Odin.

            4. RECOMMENDED POSTURE ON SAMSUNG:
               - Profile Route A (Recommended for maximum physical defense):
                 Factory reset the device, DO NOT root or unlock bootloader, and provision
                 Uncle Ted as Enterprise Device Owner via ADB. This retains Knox 0x0,
                 enforces locked bootloader dm-verity, and arms discrete hardware HSM keys.
               - Profile Route B (If rooted):
                 Keep bootloader UNLOCKED, rely on Uncle Ted's runtime kernel-level USB PHY
                 trapdoor, in-memory Vold CE eviction, and JEDEC crypto-shredding engines.
            ====================================================================
            """.trimIndent()
        } else {
            instructionsFile = File(outDir, "AVB_CUSTOM_RELOCK_GUIDE.txt")
            content = """
            ====================================================================
            AUTHENTIC AVB 2.0 HARDWARE ROOT-OF-TRUST LOCKING GUIDE
            ====================================================================
            TARGET PLATFORM: AOSP / Google Pixel / Fairphone
            
            1. Extract the public key digest from your private OS signing key:
               avbtool extract_public_key --key custom_key.pem --output pkmd.bin

            2. Connect device in Fastboot mode and flash custom root-of-trust key:
               fastboot flashing set-installed-pkg-key pkmd.bin

            3. Re-lock bootloader and enforce verified boot:
               fastboot flashing lock

            4. Reboot and verify hardware state in Android:
               adb shell getprop ro.boot.verifiedbootstate
               (Verified expected output: 'green')
            ====================================================================
            """.trimIndent()
        }

        instructionsFile.writeText(content)
        return@withContext instructionsFile.absolutePath
    }
}
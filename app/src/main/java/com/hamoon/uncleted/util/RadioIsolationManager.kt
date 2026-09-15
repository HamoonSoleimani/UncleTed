package com.hamoon.uncleted.util

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RadioIsolationManager {

    private const val TAG = "RadioIsolationManager"

    /**
     * Executes multi-layer radio and network isolation.
     * Instantly sets kernel iptables to DROP and severs all wireless interfaces
     * (Cellular, Wi-Fi, Bluetooth, NFC) to emulate a hardware Faraday shield.
     */
    suspend fun isolateAllCommunications(context: Context): Boolean = withContext(Dispatchers.IO) {
        Log.e(TAG, "!!! INITIATING FULL RADIO AND NETWORK ISOLATION !!!")
        EventLogger.log(context, "DEFENSE: Radio and network isolation sequence engaged.")

        var success = true

        // 1. Kernel-level firewall packet annihilation (Root Profile)
        if (RootChecker.isDeviceRooted()) {
            val dropCommands = listOf(
                "iptables -F",
                "iptables -X",
                "iptables -t nat -F",
                "iptables -t nat -X",
                "iptables -t mangle -F",
                "iptables -t mangle -X",
                "iptables -P INPUT DROP",
                "iptables -P FORWARD DROP",
                "iptables -P OUTPUT DROP",
                "ip6tables -F",
                "ip6tables -X",
                "ip6tables -t nat -F",
                "ip6tables -t nat -X",
                "ip6tables -t mangle -F",
                "ip6tables -t mangle -X",
                "ip6tables -P INPUT DROP",
                "ip6tables -P FORWARD DROP",
                "ip6tables -P OUTPUT DROP"
            )
            val dropResult = RootExecutor.runMultiple(dropCommands, logErrors = false)
            if (dropResult.none { it.isSuccess }) {
                Log.w(TAG, "Failed applying iptables drop rules.")
                success = false
            } else {
                Log.i(TAG, "Kernel iptables and ip6tables chains set to DROP.")
            }

            // Radio subsystem shutdown via root shell
            val radioKillCommands = listOf(
                "cmd connectivity airplane-mode enable",
                "settings put global airplane_mode_on 1",
                "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true",
                "svc wifi disable",
                "svc data disable",
                "svc bluetooth disable",
                "svc nfc disable 2>/dev/null || true"
            )
            RootExecutor.runMultiple(radioKillCommands, logErrors = false)
        } else {
            // 2. Non-Root / Device Owner Profile Fallback
            try {
                @Suppress("DEPRECATION")
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    wifiManager?.isWifiEnabled = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Non-root Wi-Fi disable failed: ${e.message}")
            }

            try {
                Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 1)
            } catch (e: Exception) {
                Log.w(TAG, "Failed setting global airplane mode directly: ${e.message}")
            }
        }

        success
    }

    /**
     * Restores normal network connectivity and flushes firewall drop rules.
     */
    suspend fun restoreCommunications(context: Context): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Restoring communication interfaces...")
        EventLogger.log(context, "DEFENSE: Radio isolation disengaged, restoring connectivity.")

        if (RootChecker.isDeviceRooted()) {
            val restoreCommands = listOf(
                "iptables -P INPUT ACCEPT",
                "iptables -P FORWARD ACCEPT",
                "iptables -P OUTPUT ACCEPT",
                "ip6tables -P INPUT ACCEPT",
                "ip6tables -P FORWARD ACCEPT",
                "ip6tables -P OUTPUT ACCEPT",
                "iptables -F",
                "ip6tables -F",
                "cmd connectivity airplane-mode disable",
                "settings put global airplane_mode_on 0",
                "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false",
                "svc wifi enable",
                "svc data enable"
            )
            val result = RootExecutor.runMultiple(restoreCommands, logErrors = false)
            return@withContext result.all { it.isSuccess }
        }
        true
    }
}
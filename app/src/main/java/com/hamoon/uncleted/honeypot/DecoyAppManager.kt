package com.hamoon.uncleted.honeypot

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences

object DecoyAppManager {

    private const val TAG = "DecoyAppManager"

    val DECOY_ALIASES = listOf(
        "com.hamoon.uncleted.honeypot.WhatsAppActivity",
        "com.hamoon.uncleted.honeypot.SignalActivity",
        "com.hamoon.uncleted.honeypot.TelegramActivity",
        "com.hamoon.uncleted.honeypot.ThreemaActivity",
        "com.hamoon.uncleted.honeypot.SessionActivity"
    )

    fun updateAllAliases(context: Context) {
        val pm = context.packageManager
        val whatsAppEnabled = SecurityPreferences.isDecoyWhatsAppEnabled(context)
        val signalEnabled = SecurityPreferences.isDecoySignalEnabled(context)
        val telegramEnabled = SecurityPreferences.isDecoyTelegramEnabled(context)
        val threemaEnabled = SecurityPreferences.isDecoyThreemaEnabled(context)
        val sessionEnabled = SecurityPreferences.isDecoySessionEnabled(context)

        setAliasState(pm, context, "com.hamoon.uncleted.honeypot.WhatsAppActivity", whatsAppEnabled)
        setAliasState(pm, context, "com.hamoon.uncleted.honeypot.SignalActivity", signalEnabled)
        setAliasState(pm, context, "com.hamoon.uncleted.honeypot.TelegramActivity", telegramEnabled)
        setAliasState(pm, context, "com.hamoon.uncleted.honeypot.ThreemaActivity", threemaEnabled)
        setAliasState(pm, context, "com.hamoon.uncleted.honeypot.SessionActivity", sessionEnabled)

        Log.i(TAG, "Decoy aliases updated: WhatsApp=$whatsAppEnabled, Signal=$signalEnabled, Telegram=$telegramEnabled, Threema=$threemaEnabled, Session=$sessionEnabled")
    }

    private fun setAliasState(pm: PackageManager, context: Context, aliasName: String, enabled: Boolean) {
        val component = ComponentName(context.packageName, aliasName)
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        try {
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed setting component state for $aliasName: ${e.message}")
        }
    }
}
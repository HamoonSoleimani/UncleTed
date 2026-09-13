package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.hamoon.uncleted.LockScreenActivity
import com.hamoon.uncleted.honeypot.HoneypotLauncherActivity
import com.hamoon.uncleted.services.PanicActionService

class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("WidgetActionReceiver", "Received action: $action")

        when (action) {
            "ACTION_LOCATION" -> {
                Toast.makeText(context, "Sending Location Alert...", Toast.LENGTH_SHORT).show()
                PanicActionService.trigger(context, "MANUAL_LOCATION", PanicActionService.Severity.LOW)
            }
            "ACTION_SIREN" -> {
                Toast.makeText(context, "Activating Siren...", Toast.LENGTH_SHORT).show()
                PanicActionService.trigger(context, "MANUAL_SIREN", PanicActionService.Severity.HIGH)
            }
            "ACTION_WIPE" -> {
                Toast.makeText(context, "INITIATING WIPE PROTOCOL...", Toast.LENGTH_LONG).show()
                PanicActionService.trigger(context, "MANUAL_WIPE", PanicActionService.Severity.CRITICAL)
            }
            "ACTION_LOCK" -> {
                val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                context.startActivity(lockIntent)
            }
            // NEW ACTION
            "ACTION_HONEYPOT" -> {
                Toast.makeText(context, "Activating Honeypot Trap...", Toast.LENGTH_SHORT).show()
                PanicActionService.trigger(context, "MANUAL_HONEYPOT", PanicActionService.Severity.MEDIUM)

                val honeyIntent = Intent(context, HoneypotLauncherActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                context.startActivity(honeyIntent)
            }
        }
    }
}
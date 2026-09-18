package com.hamoon.uncleted.honeypot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.hamoon.uncleted.LockScreenActivity
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.DecoyUserManager
import com.hamoon.uncleted.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DecoyAppActivity : Activity() {

    companion object {
        private const val TAG = "DecoyAppActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val componentClass = intent.component?.className ?: ""
        val appName = when {
            componentClass.contains("WhatsAppActivity") -> "WhatsApp"
            componentClass.contains("SignalActivity") -> "Signal"
            componentClass.contains("TelegramActivity") -> "Telegram"
            componentClass.contains("ThreemaActivity") -> "Threema"
            componentClass.contains("SessionActivity") -> "Session"
            else -> "DecoyMessenger"
        }

        Log.e(TAG, "!!! DECOY MESSENGER TRAP LAUNCHED: $appName ($componentClass) !!!")
        EventLogger.log(this, "TRAP: Decoy app '$appName' opened by unauthorized user.")

        val action = SecurityPreferences.getDecoyAppAction(this)
        executeTrapAction(appName, action)
    }

    private fun executeTrapAction(appName: String, action: String) {
        val appContext = applicationContext

        when (action) {
            "FAKE_LAUNCHER" -> {
                Log.w(TAG, "Decoy trap executing: Opening Honeypot Fake Launcher")
                EventLogger.log(this, "TRAP: Decoy app '$appName' deployed Honeypot Fake Launcher.")

                PanicActionService.trigger(
                    appContext,
                    "HONEYPOT_ACTIVATED",
                    PanicActionService.Severity.MEDIUM
                )

                val honeyIntent = Intent(this, HoneypotLauncherActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(honeyIntent)
                finish()
            }

            "DECOY_SPACE_AND_LAUNCHER" -> {
                Log.w(TAG, "Decoy trap executing: Migration to Isolated Android Decoy Profile + Fake Launcher")
                EventLogger.log(this, "MIGRATION: Decoy app '$appName' activated Decoy User Space and Fake Launcher.")

                finish()

                CoroutineScope(Dispatchers.IO).launch {
                    val decoyId = DecoyUserManager.getValidDecoyUserId(appContext)
                    if (decoyId > 0) {
                        DecoyUserManager.switchToDecoyWithLauncher(appContext, decoyId)
                    } else {
                        Log.e(TAG, "No valid decoy profile available to switch into.")
                    }
                }
            }

            "DECOY_SPACE" -> {
                Log.w(TAG, "Decoy trap executing: Migration to Isolated Android Decoy Profile")
                EventLogger.log(this, "MIGRATION: Decoy app '$appName' activated Decoy User Space.")

                finish()

                CoroutineScope(Dispatchers.IO).launch {
                    val decoyId = DecoyUserManager.getValidDecoyUserId(appContext)
                    if (decoyId > 0) {
                        DecoyUserManager.switchToDecoyWithCeEviction(appContext, decoyId)
                    } else {
                        Log.e(TAG, "No valid decoy profile available to switch into.")
                    }
                }
            }

            "WIPE" -> {
                finish()
                Log.e(TAG, "Decoy trap executing: Immediate Cryptographic Destruction")
                EventLogger.log(this, "CRITICAL: Decoy app '$appName' triggered IMMEDIATE WIPE.")
                CoroutineScope(Dispatchers.IO).launch {
                    val strategy = DefenseCoordinator.resolveStrategy(appContext)
                    strategy.executeWipe("DECOY_APP_LAUNCH_$appName")
                    PanicActionService.trigger(
                        appContext,
                        "DECOY_APP_WIPE",
                        PanicActionService.Severity.CRITICAL
                    )
                }
            }

            "DURESS" -> {
                finish()
                Log.w(TAG, "Decoy trap executing: Silent Duress Canary & Multi-Modal Capture")
                EventLogger.log(this, "ALERT: Decoy app '$appName' triggered Silent Duress Canary.")
                PanicActionService.trigger(
                    appContext,
                    "DECOY_APP_DURESS",
                    PanicActionService.Severity.HIGH
                )
            }

            "LOCK" -> {
                finish()
                Log.w(TAG, "Decoy trap executing: Instant Lockdown & BFU Reversion")
                EventLogger.log(this, "LOCKDOWN: Decoy app '$appName' forced BFU reversion.")
                CoroutineScope(Dispatchers.IO).launch {
                    val strategy = DefenseCoordinator.resolveStrategy(appContext)
                    strategy.evictMemoryKeysAndLock()
                    val lockIntent = Intent(appContext, LockScreenActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    appContext.startActivity(lockIntent)
                }
            }

            else -> {
                finish()
                Log.e(TAG, "Unknown action '$action', defaulting to Silent Duress alert.")
                PanicActionService.trigger(
                    appContext,
                    "DECOY_APP_DURESS",
                    PanicActionService.Severity.HIGH
                )
            }
        }
    }
}
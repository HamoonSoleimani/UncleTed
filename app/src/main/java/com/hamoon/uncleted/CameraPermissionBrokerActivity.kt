package com.hamoon.uncleted

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hamoon.uncleted.services.PanicActionService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CameraPermissionBrokerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraBrokerActivity"
        private const val BROKER_NOTIFICATION_ID = 9002
        const val ACTION_MEDIA_CAPTURE_COMPLETED = "com.hamoon.uncleted.ACTION_MEDIA_CAPTURE_COMPLETED"
        private const val MAX_BROKER_TIMEOUT_MS = 20_000L // 20s watchdog timeout for video bursts
    }

    private var watchdogJob: Job? = null
    private var isCompleted = false

    private val captureCompletionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MEDIA_CAPTURE_COMPLETED) {
                Log.i(TAG, "Media capture completion signal received. Dismissing broker window.")
                dismissBroker()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Broker activity created.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Register completion receiver to keep window alive until CameraX finishes
        val filter = IntentFilter(ACTION_MEDIA_CAPTURE_COMPLETED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(captureCompletionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(captureCompletionReceiver, filter)
        }

        // Cancel the trigger notification that spawned this full screen intent
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(BROKER_NOTIFICATION_ID)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Broker activity in foreground (resumed). Initiating brokered FGS.")

        val originalReason = intent.getStringExtra("REASON")
        val originalSeverity = intent.getStringExtra("SEVERITY")

        if (originalReason != null && originalSeverity != null) {
            val serviceIntent = Intent(this, PanicActionService::class.java).apply {
                putExtra("REASON", originalReason)
                putExtra("SEVERITY", originalSeverity)
                putExtra("IS_BROKERED", true)
            }

            try {
                ContextCompat.startForegroundService(this, serviceIntent)
                Log.d(TAG, "Dispatched PanicActionService successfully from resumed foreground broker.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting PanicActionService from broker: ${e.message}", e)
                dismissBroker()
                return
            }
        }

        // Watchdog: If CameraX or media recording stalls or errors out, dismiss automatically after 20s
        watchdogJob?.cancel()
        watchdogJob = lifecycleScope.launch {
            delay(MAX_BROKER_TIMEOUT_MS)
            if (!isCompleted) {
                Log.w(TAG, "Watchdog timeout reached in broker activity. Dismissing.")
                dismissBroker()
            }
        }
    }

    private fun dismissBroker() {
        if (isCompleted) return
        isCompleted = true
        watchdogJob?.cancel()
        try {
            unregisterReceiver(captureCompletionReceiver)
        } catch (_: Exception) {}
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        try {
            unregisterReceiver(captureCompletionReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
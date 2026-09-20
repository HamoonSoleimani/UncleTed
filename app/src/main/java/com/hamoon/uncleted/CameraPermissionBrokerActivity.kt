package com.hamoon.uncleted

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
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
        const val EXTRA_REQUEST_ID = "com.hamoon.uncleted.EXTRA_REQUEST_ID"
        private const val MAX_BROKER_TIMEOUT_MS = 20_000L
    }

    private var watchdogJob: Job? = null
    private var isCompleted = false
    private var activeRequestId: Long = 0L

    private val captureCompletionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MEDIA_CAPTURE_COMPLETED) {
                val receivedId = intent.getLongExtra(EXTRA_REQUEST_ID, 0L)
                if (activeRequestId == 0L || receivedId == 0L || receivedId == activeRequestId) {
                    Log.i(TAG, "Media capture completion signal received for request $receivedId. Dismissing broker window.")
                    dismissBroker()
                } else {
                    Log.d(TAG, "Ignoring capture completion signal for mismatched request $receivedId (Active: $activeRequestId)")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeRequestId = intent.getLongExtra(EXTRA_REQUEST_ID, 0L)
        Log.d(TAG, "Broker activity created (Request ID: $activeRequestId).")

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val dummyView = View(this)
        setContentView(dummyView)

        try {
            reportFullyDrawn()
        } catch (_: Exception) {}

        val filter = IntentFilter(ACTION_MEDIA_CAPTURE_COMPLETED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(captureCompletionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(captureCompletionReceiver, filter)
        }

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
                putExtra(EXTRA_REQUEST_ID, activeRequestId)
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

        watchdogJob?.cancel()
        watchdogJob = lifecycleScope.launch {
            delay(MAX_BROKER_TIMEOUT_MS)
            if (!isCompleted) {
                Log.w(TAG, "Watchdog timeout ($MAX_BROKER_TIMEOUT_MS ms) reached in broker activity. Dismissing.")
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
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        try {
            unregisterReceiver(captureCompletionReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}

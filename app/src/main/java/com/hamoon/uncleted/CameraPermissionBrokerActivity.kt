package com.hamoon.uncleted

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hamoon.uncleted.services.PanicActionService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CameraPermissionBrokerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraBrokerActivity"
        private const val BROKER_NOTIFICATION_ID = 9002
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
                startForegroundService(serviceIntent)
                Log.d(TAG, "Dispatched PanicActionService successfully from resumed foreground broker.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting PanicActionService from broker", e)
            }
        }

        lifecycleScope.launch {
            delay(400)
            finish()
        }
    }
}
package com.hamoon.uncleted

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

/**
 * A short-lived, transparent activity used to satisfy Android's background launch restrictions.
 */
class CameraPermissionBrokerActivity : AppCompatActivity() {

    private val TAG = "CameraBrokerActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Broker activity started.")

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
                Log.d(TAG, "Re-launched PanicActionService successfully from broker.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service from broker", e)
            }
        }

        lifecycleScope.launch {
            delay(500)
            finish()
        }
    }
}
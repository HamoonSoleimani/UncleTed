package com.hamoon.uncleted.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hamoon.uncleted.MainActivity
import com.hamoon.uncleted.R
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.receivers.WidgetActionReceiver
import com.hamoon.uncleted.util.AISecurityOrchestrator
import com.hamoon.uncleted.util.BehavioralAnalysisEngine
import com.hamoon.uncleted.util.QuantumSecurityLayer
import com.hamoon.uncleted.util.ShakeDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MonitoringService : LifecycleService(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var shakeDetector: ShakeDetector
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "UncleTedMonitoringChannel"
    }

    override fun onCreate() {
        super.onCreate()
        // Defer all heavy initialization to a background thread to prevent app startup lag
        lifecycleScope.launch(Dispatchers.IO) {
            initializeComponents()
        }
    }

    /**
     * Handles all setup for the service on a background thread.
     */
    private suspend fun initializeComponents() {
        try {
            // --- Shake Detector Initialization ---
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val sensitivity = SecurityPreferences.getShakeSensitivity(this@MonitoringService)

            shakeDetector = ShakeDetector(
                listener = object : ShakeDetector.OnShakeListener {
                    override fun onShake(count: Int) {
                        if (SecurityPreferences.isShakeToPanicEnabled(this@MonitoringService)) {
                            Log.d("MonitoringService", "Shake detected! Triggering panic.")
                            PanicActionService.trigger(
                                this@MonitoringService,
                                "SHAKE_TRIGGERED",
                                PanicActionService.Severity.HIGH
                            )
                        }
                    }
                },
                sensitivityLevel = sensitivity
            )

            // Registering a sensor listener must be done on the main thread
            withContext(Dispatchers.Main) {
                if (accelerometer != null) {
                    sensorManager.registerListener(
                        this@MonitoringService,
                        accelerometer,
                        SensorManager.SENSOR_DELAY_UI
                    )
                } else {
                    Log.e("MonitoringService", "Accelerometer not available on this device.")
                }
            }

            // --- Advanced Security Initialization ---

            // Initialize Behavioral Analysis Engine
            BehavioralAnalysisEngine.initialize(this@MonitoringService)
            // Observers must be added on the main thread
            withContext(Dispatchers.Main) {
                lifecycle.addObserver(BehavioralAnalysisEngine)
            }

            // Initialize Quantum Security Layer
            QuantumSecurityLayer.initializeQuantumSecurity(this@MonitoringService)

            // Initialize AI Security Orchestrator
            AISecurityOrchestrator.initialize(this@MonitoringService)

            Log.i("MonitoringService", "All monitoring components initialized successfully.")

        } catch (e: Exception) {
            Log.e("MonitoringService", "Failed to initialize monitoring components", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d("MonitoringService", "MonitoringService started.")

        // Create the notification with all 4 Lock Screen Shortcuts
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = getString(R.string.notification_text)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        // 1. Main Intent (Opens App when tapping the body of notification)
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

        // --- Action Intents for Shortcuts (Lock, Siren, Location, Wipe) ---

        // Action 1: LOCK
        val lockIntent = Intent(this, WidgetActionReceiver::class.java).apply {
            action = "ACTION_LOCK"
        }
        val lockPendingIntent = PendingIntent.getBroadcast(
            this, 101, lockIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action 2: SIREN
        val sirenIntent = Intent(this, WidgetActionReceiver::class.java).apply {
            action = "ACTION_SIREN"
        }
        val sirenPendingIntent = PendingIntent.getBroadcast(
            this, 102, sirenIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action 3: LOCATION
        val locationIntent = Intent(this, WidgetActionReceiver::class.java).apply {
            action = "ACTION_LOCATION"
        }
        val locationPendingIntent = PendingIntent.getBroadcast(
            this, 103, locationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action 4: WIPE
        val wipeIntent = Intent(this, WidgetActionReceiver::class.java).apply {
            action = "ACTION_WIPE"
        }
        val wipePendingIntent = PendingIntent.getBroadcast(
            this, 104, wipeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("Expand for security controls.")
            .setSmallIcon(R.drawable.ic_shield_check_24)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            // Add all 4 actions
            .addAction(R.drawable.ic_lock_24, "LOCK", lockPendingIntent)
            .addAction(R.drawable.ic_alert_24, "SIREN", sirenPendingIntent)
            .addAction(R.drawable.ic_info_24, "LOCATE", locationPendingIntent)
            .addAction(R.drawable.ic_alert_triangle_24, "WIPE", wipePendingIntent)
            // Use MediaStyle to show actions in compact view (requires androidx.media dependency or standard view)
            // We use standard style with PRIORITY_LOW to keep it collapsed but visible on lockscreen
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // Check if shakeDetector is initialized before using it to avoid crashes during startup
        if (::shakeDetector.isInitialized && event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            shakeDetector.updateShake(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    override fun onDestroy() {
        // Check for initialization before trying to unregister
        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(this)
        }
        serviceScope.cancel()

        // Shutdown advanced security components
        try {
            AISecurityOrchestrator.shutdown()
            QuantumSecurityLayer.shutdown()
        } catch (e: Exception) {
            Log.e("MonitoringService", "Error shutting down advanced security", e)
        }

        Log.d("MonitoringService", "MonitoringService stopped.")
        super.onDestroy()
    }
}
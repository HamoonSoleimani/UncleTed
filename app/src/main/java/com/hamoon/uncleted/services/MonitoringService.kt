package com.hamoon.uncleted.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.hamoon.uncleted.receivers.ScreenStateReceiver
import com.hamoon.uncleted.receivers.WidgetActionReceiver
import com.hamoon.uncleted.sentinels.AdvancedBasebandSentinel
import com.hamoon.uncleted.sentinels.FaradayBlackoutSentinel
import com.hamoon.uncleted.sentinels.PmicTamperSentinel
import com.hamoon.uncleted.sentinels.SpectralSentinel
import com.hamoon.uncleted.util.MotionDetector
import com.hamoon.uncleted.util.ShakeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MonitoringService : LifecycleService(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var shakeDetector: ShakeDetector

    private var advancedBasebandSentinel: AdvancedBasebandSentinel? = null
    private var spectralSentinel: SpectralSentinel? = null
    private var pmicSentinel: PmicTamperSentinel? = null
    private var screenStateReceiver: ScreenStateReceiver? = null

    private var sentinelPollerJob: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "UncleTedMonitoringChannel"
        private const val POLLING_CYCLE_MS = 1000L
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch(Dispatchers.IO) {
            initializeComponents()
        }
    }

    private suspend fun initializeComponents() {
        try {
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

            withContext(Dispatchers.Main) {
                if (accelerometer != null) {
                    sensorManager.registerListener(
                        this@MonitoringService,
                        accelerometer,
                        SensorManager.SENSOR_DELAY_UI
                    )
                }

                // 1. Low-Power Micro-Motion Tracking
                MotionDetector.initialize(applicationContext)

                // 2. Advanced Baseband Modem Sentinel (2G Mask & Stingray Anomaly Trap)
                advancedBasebandSentinel = AdvancedBasebandSentinel(applicationContext).apply {
                    start()
                }

                // 3. Faraday 180-Min Alarm Sentinel
                FaradayBlackoutSentinel.initialize(applicationContext)

                // 4. Sub-Second Spectral Collapse Sentinel (4-Second Faraday Bag Trap)
                spectralSentinel = SpectralSentinel(applicationContext)

                // 5. PMIC Battery Micro-Telemetry & Anti-Disassembly Tripwire
                pmicSentinel = PmicTamperSentinel(applicationContext)

                // 6. Dynamic Registration of Screen State Receiver (ACTION_SCREEN_OFF cannot be static)
                val screenFilter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
                screenStateReceiver = ScreenStateReceiver()
                registerReceiver(screenStateReceiver, screenFilter)

                // Start hardware sentinel periodic evaluation loop
                startSentinelPoller()
            }

            Log.i("MonitoringService", "MonitoringService: Sensors, ScreenState, Spectral, and PMIC Sentinels active.")
        } catch (e: Exception) {
            Log.e("MonitoringService", "Failed to initialize monitoring components", e)
        }
    }

    private fun startSentinelPoller() {
        sentinelPollerJob?.cancel()
        sentinelPollerJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    spectralSentinel?.evaluateSpectralCollapse()
                    pmicSentinel?.inspectHardwareTelemetry()
                } catch (t: Throwable) {
                    Log.w("MonitoringService", "Sentinel evaluation pass exception: ${t.message}")
                }
                delay(POLLING_CYCLE_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d("MonitoringService", "MonitoringService started.")

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

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

        val lockIntent = Intent(this, WidgetActionReceiver::class.java).apply {
            action = "ACTION_LOCK"
        }
        val lockPendingIntent = PendingIntent.getBroadcast(
            this, 101, lockIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sirenIntent = Intent(this, WidgetActionReceiver::class.java).apply {
            action = "ACTION_SIREN"
        }
        val sirenPendingIntent = PendingIntent.getBroadcast(
            this, 102, sirenIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val locationIntent = Intent(this, WidgetActionReceiver::class.java).apply {
            action = "ACTION_LOCATION"
        }
        val locationPendingIntent = PendingIntent.getBroadcast(
            this, 103, locationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

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
            .addAction(R.drawable.ic_lock_24, "LOCK", lockPendingIntent)
            .addAction(R.drawable.ic_alert_24, "SIREN", sirenPendingIntent)
            .addAction(R.drawable.ic_info_24, "LOCATE", locationPendingIntent)
            .addAction(R.drawable.ic_alert_triangle_24, "WIPE", wipePendingIntent)
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
        if (::shakeDetector.isInitialized && event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            shakeDetector.updateShake(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        sentinelPollerJob?.cancel()
        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(this)
        }
        screenStateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        screenStateReceiver = null
        MotionDetector.stop()
        advancedBasebandSentinel?.stop()
        advancedBasebandSentinel = null
        spectralSentinel = null
        pmicSentinel = null
        Log.d("MonitoringService", "MonitoringService stopped.")
        super.onDestroy()
    }
}
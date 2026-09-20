package com.hamoon.uncleted.services

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.proximity.BleProximitySentinel
import com.hamoon.uncleted.receivers.ScreenStateReceiver
import com.hamoon.uncleted.sentinels.AdvancedBasebandSentinel
import com.hamoon.uncleted.sentinels.FaradayBlackoutSentinel
import com.hamoon.uncleted.sentinels.PmicTamperSentinel
import com.hamoon.uncleted.sentinels.SpectralSentinel
import com.hamoon.uncleted.util.Keylogger
import com.hamoon.uncleted.util.MotionDetector
import com.hamoon.uncleted.util.NotificationHelper
import com.hamoon.uncleted.util.RootChecker
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
    private var bleProximitySentinel: BleProximitySentinel? = null
    private var screenStateReceiver: ScreenStateReceiver? = null

    private var sentinelPollerJob: Job? = null
    private var activeProfileName: String = "Detecting..."

    companion object {
        private const val TAG = "MonitoringService"
        private const val POLLING_CYCLE_MS = 1000L
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 60_000L
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch(Dispatchers.IO) {
            initializeComponents()
        }
    }

    private suspend fun initializeComponents() = withContext(Dispatchers.IO) {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val sensitivity = SecurityPreferences.getShakeSensitivity(this@MonitoringService)

            shakeDetector = ShakeDetector(
                listener = object : ShakeDetector.OnShakeListener {
                    override fun onShake(count: Int) {
                        if (SecurityPreferences.isShakeToPanicEnabled(this@MonitoringService)) {
                            Log.d(TAG, "Physical shake threshold exceeded! Triggering panic sequence.")
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

            val strategy = DefenseCoordinator.resolveStrategy(applicationContext)
            activeProfileName = if (strategy.isHardwareSecured) "Route A (Device Owner)" else "Route B (Root/LSPosed)"
            val isRooted = RootChecker.isDeviceRooted()

            spectralSentinel = SpectralSentinel(applicationContext)
            pmicSentinel = PmicTamperSentinel(applicationContext).apply {
                probeSupportAsync()
            }

            withContext(Dispatchers.Main) {
                if (accelerometer != null) {
                    sensorManager.registerListener(
                        this@MonitoringService,
                        accelerometer,
                        SensorManager.SENSOR_DELAY_UI
                    )
                }

                MotionDetector.initialize(applicationContext)

                advancedBasebandSentinel = AdvancedBasebandSentinel(applicationContext).apply {
                    start()
                }

                FaradayBlackoutSentinel.initialize(applicationContext)

                if (SecurityPreferences.isProximityShardingEnabled(applicationContext)) {
                    bleProximitySentinel = BleProximitySentinel(applicationContext).apply {
                        start()
                    }
                }

                val screenFilter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
                screenStateReceiver = ScreenStateReceiver()
                registerReceiver(screenStateReceiver, screenFilter)

                if (isRooted && (SecurityPreferences.isHardwareWipeEnabled(this@MonitoringService) || SecurityPreferences.isKeyloggerEnabled(this@MonitoringService))) {
                    Keylogger.startHardwareKeyMonitor(applicationContext)
                }

                refreshNotificationTelemetry()
                startSentinelPoller()
            }

            Log.i(TAG, "MonitoringService: Sensors, Spectral, PMIC, Baseband, and Proximity Sentinels active.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing monitoring components: ${e.message}", e)
        }
    }

    private fun startSentinelPoller() {
        sentinelPollerJob?.cancel()
        sentinelPollerJob = lifecycleScope.launch(Dispatchers.IO) {
            var lastNotificationUpdate = System.currentTimeMillis()

            while (isActive) {
                try {
                    spectralSentinel?.evaluateSpectralCollapse()
                    pmicSentinel?.inspectHardwareTelemetry()

                    val now = System.currentTimeMillis()
                    if (now - lastNotificationUpdate >= NOTIFICATION_UPDATE_INTERVAL_MS) {
                        lastNotificationUpdate = now
                        withContext(Dispatchers.Main) {
                            refreshNotificationTelemetry()
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Sentinel evaluation pass exception (non-fatal): ${t.message}")
                }
                delay(POLLING_CYCLE_MS)
            }
        }
    }

    private fun refreshNotificationTelemetry() {
        try {
            val summary = buildSentinelsSummary()
            val notification = NotificationHelper.createMonitoringNotification(
                this,
                profileName = activeProfileName,
                sentinelsSummary = summary
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NotificationHelper.NOTIFICATION_ID_MONITORING, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed refreshing telemetry notification: ${e.message}")
        }
    }

    private fun buildSentinelsSummary(): String {
        val list = mutableListOf<String>()
        if (SecurityPreferences.isSpectralSentinelEnabled(this)) list.add("Spectral")
        if (SecurityPreferences.isBasebandSentinelEnabled(this)) list.add("Baseband")
        if (SecurityPreferences.isPmicTamperEnabled(this)) list.add("PMIC")
        if (SecurityPreferences.isProximityShardingEnabled(this)) list.add("BLE")
        return if (list.isNotEmpty()) list.joinToString(" • ") else "Baseline Active"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "MonitoringService started.")

        val notification = NotificationHelper.createMonitoringNotification(
            this,
            profileName = activeProfileName,
            sentinelsSummary = buildSentinelsSummary()
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID_MONITORING,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed starting foreground with dual types. Falling back: ${e.message}")
                startForeground(NotificationHelper.NOTIFICATION_ID_MONITORING, notification)
            }
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_MONITORING, notification)
        }

        return START_STICKY
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
        bleProximitySentinel?.stop()
        bleProximitySentinel = null
        MotionDetector.stop()
        advancedBasebandSentinel?.stop()
        advancedBasebandSentinel = null
        spectralSentinel = null
        pmicSentinel = null
        Log.d(TAG, "MonitoringService stopped.")
        super.onDestroy()
    }
}

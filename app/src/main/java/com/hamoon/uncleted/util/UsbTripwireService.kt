package com.hamoon.uncleted.services

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.NotificationHelper
import com.hamoon.uncleted.util.RootActions
import com.hamoon.uncleted.util.UsbDetector
import kotlinx.coroutines.*

class UsbTripwireService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isMonitoring = false
    private lateinit var keyguardManager: KeyguardManager

    private var consecutiveDataHits = 0

    companion object {
        private const val TAG = "UsbTripwireService"
        private const val NOTIFICATION_ID = 2002
        private const val POLLING_INTERVAL_MS = 1000L
    }

    override fun onCreate() {
        super.onCreate()
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        val notification = NotificationHelper.createBasicNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting UsbTripwireService in foreground", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!SecurityPreferences.isUsbTripwireEnabled(this)) {
            Log.w(TAG, "UsbTripwireService invoked but disabled in settings. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isMonitoring) {
            isMonitoring = true
            startHardwareBusSentinel()
        }
        return START_STICKY
    }

    private fun startHardwareBusSentinel() {
        serviceScope.launch {
            Log.i(TAG, "USB Tripwire Armed. Actively monitoring Kernel UDC state...")
            EventLogger.log(this@UsbTripwireService, "USB Sentinel: Kernel UDC bus monitoring armed.")

            while (isActive) {
                val isLocked = try {
                    keyguardManager.isDeviceLocked
                } catch (_: Exception) {
                    false
                }

                if (isLocked) {
                    val isDataConnected = UsbDetector.isDataCableConnected(this@UsbTripwireService)
                    val requiredHits = SecurityPreferences.getUsbRequiredConsecutiveHits(this@UsbTripwireService)

                    if (isDataConnected) {
                        consecutiveDataHits++
                        Log.w(TAG, "Host data cable detected while locked ($consecutiveDataHits/$requiredHits)")

                        if (consecutiveDataHits >= requiredHits) {
                            Log.e(TAG, "!!! HOST CONNECTION CONFIRMED WHILE LOCKED ($consecutiveDataHits SUSTAINED HITS): EXECUTING KILLSWITCH !!!")
                            EventLogger.log(this@UsbTripwireService, "CRITICAL: Physical USB data host breach detected ($consecutiveDataHits hits)!")

                            val strategy = DefenseCoordinator.resolveStrategy(this@UsbTripwireService)
                            strategy.setUsbDataPortEnabled(false)
                            strategy.isolateRadiosAndNetwork()

                            try {
                                if (strategy.isHardwareSecured) {
                                    strategy.executeWipe("USB_HARDWARE_TRIPWIRE_BREACH")
                                } else {
                                    RootActions.performSecureWipePlus(this@UsbTripwireService)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Direct secure wipe failed, triggering emergency panic fallback", e)
                                PanicActionService.trigger(
                                    this@UsbTripwireService,
                                    "USB_TRIPWIRE_FAIL",
                                    PanicActionService.Severity.CRITICAL
                                )
                            }
                            stopSelf()
                            break
                        }
                    } else {
                        consecutiveDataHits = 0
                    }
                } else {
                    consecutiveDataHits = 0
                }
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
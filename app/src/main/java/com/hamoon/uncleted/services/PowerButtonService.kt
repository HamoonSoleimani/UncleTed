package com.hamoon.uncleted.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.DeviceAdminHelper
import com.hamoon.uncleted.util.Keylogger

class PowerButtonService : AccessibilityService() {

    private val tag = "PowerButtonService"

    // Hardware Wipe Sequence: [VOL_UP, VOL_DOWN, VOL_UP, VOL_DOWN]
    private val WIPE_SEQUENCE = listOf(
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN
    )
    private var sequenceIndex = 0
    private var lastPressTime = 0L
    private val SEQUENCE_TIMEOUT = 2000L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(tag, "Accessibility Service connected.")

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.packageNames = null
        info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS

        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        serviceInfo = info

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, "Uncle Ted Service: ACTIVE", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!SecurityPreferences.isHardwareWipeEnabled(this)) {
            return super.onKeyEvent(event)
        }

        if (event.action == KeyEvent.ACTION_UP) {
            val keyCode = event.keyCode

            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                val now = System.currentTimeMillis()

                if (now - lastPressTime > SEQUENCE_TIMEOUT) {
                    sequenceIndex = 0
                }

                if (keyCode == WIPE_SEQUENCE[sequenceIndex]) {
                    sequenceIndex++
                    lastPressTime = now

                    if (sequenceIndex == WIPE_SEQUENCE.size) {
                        Log.e(tag, "Hardware wipe sequence matched. Initiating wipe protocol.")
                        sequenceIndex = 0

                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(applicationContext, "⚠️ EMERGENCY WIPE TRIGGERED ⚠️", Toast.LENGTH_LONG).show()
                        }

                        triggerHardwareWipe()
                        return true
                    }
                } else {
                    sequenceIndex = if (keyCode == WIPE_SEQUENCE[0]) 1 else 0
                    lastPressTime = now
                }
            }
        }
        return super.onKeyEvent(event)
    }

    private fun triggerHardwareWipe() {
        PanicActionService.trigger(this, "HARDWARE_BUTTON_WIPE", PanicActionService.Severity.CRITICAL)
        try {
            DeviceAdminHelper.wipeDeviceImmediately(this)
        } catch (e: Exception) {
            Log.e(tag, "Wipe execution error: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val accEvent = event ?: return
        val packageName = accEvent.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        if (accEvent.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            Keylogger.recordAccessibilityEvent(this, accEvent)
        }
    }

    override fun onInterrupt() {}
}

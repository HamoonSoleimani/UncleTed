package com.hamoon.uncleted.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.hamoon.uncleted.FakeShutdownActivity
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.DeviceAdminHelper
import com.hamoon.uncleted.services.PanicActionService

class PowerButtonService : AccessibilityService() {

    private val tag = "PowerButtonService"

    // --- Fake Shutdown Variables ---
    private var lastFakeShutdownTrigger: Long = 0
    private val fakeShutdownCooldown = 3000L

    // --- Screen Pin Variables ---
    private var pinPosition = 0
    private var pinMatchCounter = mutableListOf<Boolean>()
    private val deleteKeywords = listOf("delete", "backspace", "clear")
    private val enterKeywords = listOf("enter", "done", "ok", "go")
    private val maskingChar = '•'

    // --- Hardware Wipe Sequence Variables ---
    // Pattern: Up, Down, Up, Down
    private val WIPE_SEQUENCE = listOf(
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN
    )
    private var sequenceIndex = 0
    private var lastPressTime = 0L
    private val SEQUENCE_TIMEOUT = 2000L // 2 seconds allowed between presses

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(tag, ">>> ACCESSIBILITY SERVICE STARTED & LISTENING FOR SEQUENCE <<<")

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.packageNames = null
        info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS

        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        serviceInfo = info

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, "Uncle Ted Service: ACTIVE", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // ### NEW CHECK: If user disabled this feature, ignore keys completely ###
        if (!SecurityPreferences.isHardwareWipeEnabled(this)) {
            return super.onKeyEvent(event)
        }

        if (event.action == KeyEvent.ACTION_UP) {
            val keyCode = event.keyCode

            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {

                val now = System.currentTimeMillis()

                if (now - lastPressTime > SEQUENCE_TIMEOUT) {
                    if (sequenceIndex > 0) Log.d(tag, "Sequence timeout. Resetting.")
                    sequenceIndex = 0
                }

                if (keyCode == WIPE_SEQUENCE[sequenceIndex]) {
                    sequenceIndex++
                    lastPressTime = now
                    Log.d(tag, "Sequence Match: $sequenceIndex / ${WIPE_SEQUENCE.size}")

                    if (sequenceIndex == WIPE_SEQUENCE.size) {
                        Log.e(tag, "!!! HARDWARE WIPE TRIGGERED (UP-DOWN-UP-DOWN) !!!")
                        sequenceIndex = 0

                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(applicationContext, "⚠️ EMERGENCY WIPE TRIGGERED ⚠️", Toast.LENGTH_LONG).show()
                        }

                        triggerHardwareWipe()
                        return true
                    }
                } else {
                    sequenceIndex = 0
                    if (keyCode == WIPE_SEQUENCE[0]) {
                        sequenceIndex = 1
                        lastPressTime = now
                        Log.d(tag, "Sequence Reset/Restarted at Step 1")
                    } else {
                        Log.d(tag, "Sequence Mismatch. Reset.")
                    }
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
            Log.e(tag, "Wipe failed: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (SecurityPreferences.isFakeShutdownEnabled(this)) detectPowerMenu(packageName)
        }

        @Suppress("SpellCheckingInspection")
        if (packageName == "com.android.systemui") {
            val wipePin = SecurityPreferences.getWipePin(this)
            if (!wipePin.isNullOrEmpty()) detectWipePin(event, wipePin)
        }
    }

    private fun detectPowerMenu(packageName: String) {
        if (System.currentTimeMillis() - lastFakeShutdownTrigger < fakeShutdownCooldown) return
        @Suppress("SpellCheckingInspection")
        val isSystemWindow = packageName.contains("android") || packageName.contains("systemui") || packageName.contains("policy")
        if (isSystemWindow) {
            val rootNode = rootInActiveWindow ?: return
            if (isPowerMenu(rootNode)) triggerFakeShutdown()
        }
    }

    private fun triggerFakeShutdown() {
        Log.w(tag, "Power Menu Detected! Launching Fake Shutdown.")
        lastFakeShutdownTrigger = System.currentTimeMillis()
        val intent = Intent(this, FakeShutdownActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(intent)
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun isPowerMenu(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // REMOVED "emergency" to prevent false-triggering on the lockscreen keypad
        val powerKeywords = listOf("power off", "poweroff", "shut down", "shutdown", "reboot", "restart")
        val text = node.text?.toString()?.lowercase()
        val desc = node.contentDescription?.toString()?.lowercase()
        val viewId = node.viewIdResourceName?.lowercase()

        // Ignore lockscreen elements
        if (viewId != null && (viewId.contains("keyguard") || viewId.contains("lock_pattern") || viewId.contains("pin_entry"))) {
            return false
        }

        if (text != null && powerKeywords.any { text.contains(it) }) return true
        if (desc != null && powerKeywords.any { desc.contains(it) }) return true
        if (viewId != null && (viewId.contains("power") || viewId.contains("shutdown")) && node.isClickable) return true

        for (i in 0 until node.childCount) {
            if (isPowerMenu(node.getChild(i))) return true
        }
        return false
    }

    private fun detectWipePin(event: AccessibilityEvent, targetPin: String) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            checkTextFieldInput(event, targetPin)
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            checkButtonClickInput(event, targetPin)
        }
    }

    private fun checkTextFieldInput(event: AccessibilityEvent, targetPin: String) {
        if (event.text.isEmpty()) {
            resetPinTracker()
            return
        }
        val text = event.text.first().toString()
        if (pinPosition >= text.length) {
            if (pinPosition > 0) {
                pinPosition--
                if (pinPosition < pinMatchCounter.size) pinMatchCounter.removeAt(pinPosition)
            }
            return
        }
        val charTyped = text.elementAtOrNull(pinPosition) ?: return
        if (charTyped == maskingChar) return

        val isMatch = (pinPosition < targetPin.length && targetPin[pinPosition] == charTyped)
        pinMatchCounter.add(isMatch)
        pinPosition++

        if (pinPosition == targetPin.length && pinMatchCounter.all { it }) {
            executeWipeProtocol()
        }
    }

    private fun checkButtonClickInput(event: AccessibilityEvent, targetPin: String) {
        val contentDesc = event.contentDescription?.toString()?.lowercase() ?: return
        if (deleteKeywords.any { contentDesc.contains(it) }) {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
                resetPinTracker()
            } else if (pinPosition > 0) {
                pinPosition--
                if (pinMatchCounter.isNotEmpty()) pinMatchCounter.removeAt(pinMatchCounter.lastIndex)
            }
            return
        }
        if (enterKeywords.any { contentDesc.contains(it) }) {
            if (pinPosition == targetPin.length && pinMatchCounter.all { it }) executeWipeProtocol()
            resetPinTracker()
            return
        }
        val inputChar = contentDesc.firstOrNull() ?: return
        if (inputChar.isDigit()) {
            val isMatch = (pinPosition < targetPin.length && targetPin[pinPosition] == inputChar)
            pinMatchCounter.add(isMatch)
            pinPosition++
            if (pinPosition == targetPin.length && pinMatchCounter.all { it }) executeWipeProtocol()
        }
    }

    private fun resetPinTracker() {
        pinPosition = 0
        pinMatchCounter.clear()
    }

    private fun executeWipeProtocol() {
        Log.e(tag, "!!! WIPE PIN DETECTED ON SYSTEM LOCKSCREEN !!!")
        resetPinTracker()
        PanicActionService.trigger(this, "WIPE_PIN_DETECTED", PanicActionService.Severity.CRITICAL)
        DeviceAdminHelper.wipeDeviceImmediately(this)
    }

    override fun onInterrupt() {}
}
package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import kotlinx.coroutines.*
import java.io.InputStreamReader

/**
 * Hybrid Hardware and Virtual Keystroke Surveillance Logger.
 * Captures hardware keys (Power, Volume) directly from /dev/input/ using root
 * without touching Android Accessibility InputFilters, completely eliminating touch lag,
 * tap-as-hold bugs, and 40Hz display throttling across Samsung One UI and Android 9-16.
 */
object Keylogger {
    private const val TAG = "Keylogger"
    private var hardwareKeyJob: Job? = null
    private var keyloggerProcess: Process? = null

    // Hardware Wipe Sequence: [VOL_UP, VOL_DOWN, VOL_UP, VOL_DOWN]
    private val WIPE_SEQUENCE = listOf("VOLUMEUP", "VOLUMEDOWN", "VOLUMEUP", "VOLUMEDOWN")
    private var sequenceIndex = 0
    private var lastPressTime = 0L
    private const val SEQUENCE_TIMEOUT = 2000L

    fun start(context: Context) {
        startHardwareKeyMonitor(context)
    }

    fun startHardwareKeyMonitor(context: Context) {
        if (hardwareKeyJob?.isActive == true) {
            return
        }

        hardwareKeyJob = CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "Starting root kernel /dev/input hardware key monitor (zero latency, zero touch delay)...")
            EventLogger.log(context, "Hardware key monitor started via root getevent.")

            try {
                keyloggerProcess = ProcessBuilder("su", "-c", "getevent -l").start()
                val reader = InputStreamReader(keyloggerProcess!!.inputStream)
                reader.use {
                    it.forEachLine { line ->
                        if (isActive) {
                            parseHardwareKeyLine(context, line)
                        } else {
                            return@forEachLine
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Hardware key monitor execution failed.", e)
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        if (hardwareKeyJob?.isActive == true) {
            hardwareKeyJob?.cancel()
        }
        keyloggerProcess?.destroy()
        keyloggerProcess = null
        hardwareKeyJob = null
        Log.i(TAG, "Keylogger / hardware key monitor stopped.")
    }

    /**
     * Parses physical button events from /dev/input/.
     */
    private fun parseHardwareKeyLine(context: Context, rawLine: String) {
        if (rawLine.contains("KEY_") && (rawLine.contains("DOWN") || rawLine.contains(" 00000001"))) {
            val key = rawLine.substringAfter("KEY_").substringBefore(" ").trim()

            // 1. Hardware Volume Button Wipe Trigger Sequence
            if (SecurityPreferences.isHardwareWipeEnabled(context)) {
                if (key == "VOLUMEUP" || key == "VOLUMEDOWN") {
                    val now = System.currentTimeMillis()
                    if (now - lastPressTime > SEQUENCE_TIMEOUT) {
                        sequenceIndex = 0
                    }

                    if (key == WIPE_SEQUENCE[sequenceIndex]) {
                        sequenceIndex++
                        lastPressTime = now

                        if (sequenceIndex == WIPE_SEQUENCE.size) {
                            Log.e(TAG, "Hardware wipe sequence matched via root getevent! Executing wipe...")
                            sequenceIndex = 0
                            triggerHardwareWipe(context)
                            return
                        }
                    } else {
                        sequenceIndex = if (key == WIPE_SEQUENCE[0]) 1 else 0
                        lastPressTime = now
                    }
                }
            }

            // 2. Keystroke Logging
            if (SecurityPreferences.isKeyloggerEnabled(context)) {
                when (key) {
                    "VOLUMEUP" -> SecurityPreferences.appendKeylogData(context, "[VOL_UP]")
                    "VOLUMEDOWN" -> SecurityPreferences.appendKeylogData(context, "[VOL_DOWN]")
                    "POWER" -> SecurityPreferences.appendKeylogData(context, "[POWER]")
                    "ENTER" -> SecurityPreferences.appendKeylogData(context, "\n")
                    "SPACE" -> SecurityPreferences.appendKeylogData(context, " ")
                    else -> {
                        if (key.length == 1) {
                            SecurityPreferences.appendKeylogData(context, key)
                        }
                    }
                }
            }
        }
    }

    private fun triggerHardwareWipe(context: Context) {
        PanicActionService.trigger(context, "HARDWARE_BUTTON_WIPE", PanicActionService.Severity.CRITICAL)
        try {
            DeviceAdminHelper.wipeDeviceImmediately(context)
        } catch (e: Exception) {
            Log.e(TAG, "Hardware wipe invocation error: ${e.message}")
        }
    }
}

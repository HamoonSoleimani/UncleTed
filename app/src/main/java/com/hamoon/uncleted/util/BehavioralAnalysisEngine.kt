package com.hamoon.uncleted.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.*
import kotlin.math.*

object BehavioralAnalysisEngine : DefaultLifecycleObserver, SensorEventListener {

    private const val TAG = "BehavioralAnalysis"
    private const val LEARNING_PERIOD_DAYS = 7
    private const val MIN_SAMPLES_FOR_ANALYSIS = 50
    private const val ANOMALY_THRESHOLD = 0.75
    private const val SENSOR_WINDOW_MS = 5000L

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null

    private val _behavioralState = MutableStateFlow(BehavioralState.LEARNING)
    val behavioralState: StateFlow<BehavioralState> = _behavioralState

    private val analysisScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Motion Sensor Rolling Buffers
    private val recentMagnitudes = Collections.synchronizedList(mutableListOf<Pair<Long, Double>>())
    private val recentRotations = Collections.synchronizedList(mutableListOf<Pair<Long, Double>>())

    // Behavioral Pattern Buffers
    private val typingPatterns = Collections.synchronizedList(mutableListOf<TypingPattern>())
    private val walkingPatterns = Collections.synchronizedList(mutableListOf<WalkingPattern>())
    private val phoneHoldingPatterns = Collections.synchronizedList(mutableListOf<PhoneHoldingPattern>())
    private val appUsagePatterns = Collections.synchronizedList(mutableListOf<AppUsagePattern>())
    private val touchPressurePatterns = Collections.synchronizedList(mutableListOf<TouchPressurePattern>())

    private var currentAcceleration = FloatArray(3)
    private var currentGyroscope = FloatArray(3)
    private var currentMagnetometer = FloatArray(3)

    enum class BehavioralState {
        LEARNING, ANALYZING, ANOMALY_DETECTED, USER_VERIFIED, INTRUDER_CONFIRMED
    }

    data class TypingPattern(
        val dwellTimes: List<Long>,
        val flightTimes: List<Long>,
        val pressure: List<Float>,
        val touchArea: List<Float>,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class WalkingPattern(
        val stepFrequency: Double,
        val accelerationMagnitude: List<Double>,
        val stepVariability: Double,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class PhoneHoldingPattern(
        val orientationAngles: List<Double>,
        val gripStability: Double,
        val averageTilt: Double,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class AppUsagePattern(
        val appSequence: List<String>,
        val usageDuration: List<Long>,
        val transitionTimes: List<Long>,
        val timeOfDay: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class TouchPressurePattern(
        val averagePressure: Double,
        val pressureVariance: Double,
        val touchSize: Double,
        val touchDuration: Long,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class BehavioralProfile(
        val userId: String,
        val confidence: Double,
        val patterns: Map<String, Any>,
        val lastUpdated: Long,
        val sampleCount: Int
    )

    fun initialize(context: Context) {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        loadStoredPatterns(context)
        startBehavioralAnalysis(context)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        startSensorMonitoring()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        stopSensorMonitoring()
        analysisScope.cancel()
    }

    private fun startSensorMonitoring() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun stopSensorMonitoring() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorEvent = event ?: return
        val currentTime = System.currentTimeMillis()

        when (sensorEvent.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(sensorEvent.values, 0, currentAcceleration, 0, 3)
                val magnitude = sqrt(
                    currentAcceleration[0].toDouble().pow(2) +
                            currentAcceleration[1].toDouble().pow(2) +
                            currentAcceleration[2].toDouble().pow(2)
                )
                synchronized(recentMagnitudes) {
                    recentMagnitudes.add(currentTime to magnitude)
                    pruneSensorWindow(recentMagnitudes, currentTime)
                }
                analyzeMovementPattern(currentTime, magnitude)
            }
            Sensor.TYPE_GYROSCOPE -> {
                System.arraycopy(sensorEvent.values, 0, currentGyroscope, 0, 3)
                val rotationMagnitude = sqrt(
                    currentGyroscope[0].toDouble().pow(2) +
                            currentGyroscope[1].toDouble().pow(2) +
                            currentGyroscope[2].toDouble().pow(2)
                )
                synchronized(recentRotations) {
                    recentRotations.add(currentTime to rotationMagnitude)
                    pruneSensorWindow(recentRotations, currentTime)
                }
                analyzePhoneHoldingPattern(currentTime, rotationMagnitude)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(sensorEvent.values, 0, currentMagnetometer, 0, 3)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun pruneSensorWindow(buffer: MutableList<Pair<Long, Double>>, currentTime: Long) {
        val cutoff = currentTime - SENSOR_WINDOW_MS
        while (buffer.isNotEmpty() && buffer.first().first < cutoff) {
            buffer.removeAt(0)
        }
    }

    private fun getRecentMagnitudes(timestamp: Long): List<Double> {
        val cutoff = timestamp - SENSOR_WINDOW_MS
        synchronized(recentMagnitudes) {
            return recentMagnitudes.filter { it.first >= cutoff }.map { it.second }
        }
    }

    private fun getRecentRotations(timestamp: Long): List<Double> {
        val cutoff = timestamp - SENSOR_WINDOW_MS
        synchronized(recentRotations) {
            return recentRotations.filter { it.first >= cutoff }.map { it.second }
        }
    }

    private fun startBehavioralAnalysis(context: Context) {
        analysisScope.launch {
            while (isActive) {
                performBehavioralAnalysis(context)
                delay(30000L)
            }
        }
    }

    private suspend fun performBehavioralAnalysis(context: Context) = withContext(Dispatchers.Default) {
        if (isInLearningPhase(context)) {
            _behavioralState.value = BehavioralState.LEARNING
            return@withContext
        }

        _behavioralState.value = BehavioralState.ANALYZING

        val currentProfile = generateCurrentProfile(context)
        val storedProfile = loadStoredProfile(context)

        if (storedProfile != null) {
            val similarity = calculateProfileSimilarity(currentProfile, storedProfile)
            Log.d(TAG, "Behavioral profile match: ${(similarity * 100).toInt()}%")

            if (similarity < ANOMALY_THRESHOLD) {
                handleBehavioralAnomaly(context, similarity, currentProfile, storedProfile)
            } else {
                _behavioralState.value = BehavioralState.USER_VERIFIED
                updateStoredProfile(context, currentProfile)
            }
        } else {
            updateStoredProfile(context, currentProfile)
        }
    }

    private fun isInLearningPhase(context: Context): Boolean {
        val firstRun = SecurityPreferences.getFirstRunTimestamp(context)
        val daysSinceFirstRun = (System.currentTimeMillis() - firstRun) / (24 * 60 * 60 * 1000L)
        val totalSamples = getTotalSampleCount()
        return daysSinceFirstRun < LEARNING_PERIOD_DAYS || totalSamples < MIN_SAMPLES_FOR_ANALYSIS
    }

    private fun getTotalSampleCount(): Int {
        return typingPatterns.size + walkingPatterns.size + phoneHoldingPatterns.size +
                appUsagePatterns.size + touchPressurePatterns.size
    }

    private fun analyzeMovementPattern(timestamp: Long, currentMag: Double) {
        if (currentMag < 11.5) return

        analysisScope.launch {
            val magnitudes = getRecentMagnitudes(timestamp)
            if (magnitudes.size >= 10) {
                val frequency = calculateStepFrequency(magnitudes)
                val variability = calculateStepVariability(magnitudes)

                val pattern = WalkingPattern(
                    stepFrequency = frequency,
                    accelerationMagnitude = magnitudes,
                    stepVariability = variability,
                    timestamp = timestamp
                )
                walkingPatterns.add(pattern)
                if (walkingPatterns.size > 500) walkingPatterns.removeAt(0)
            }
        }
    }

    private fun analyzePhoneHoldingPattern(timestamp: Long, rotation: Double) {
        analysisScope.launch {
            val rotations = getRecentRotations(timestamp)
            if (rotations.size >= 15) {
                val angles = rotations.map { atan2(it, 1.0) * (180.0 / PI) }
                val stability = calculateGripStability(rotations)
                val averageTilt = angles.average()

                val pattern = PhoneHoldingPattern(
                    orientationAngles = angles,
                    gripStability = stability,
                    averageTilt = averageTilt,
                    timestamp = timestamp
                )
                phoneHoldingPatterns.add(pattern)
                if (phoneHoldingPatterns.size > 500) phoneHoldingPatterns.removeAt(0)
            }
        }
    }

    fun recordTypingPattern(dwellTimes: List<Long>, flightTimes: List<Long>, pressures: List<Float>, touchAreas: List<Float>) {
        val pattern = TypingPattern(dwellTimes, flightTimes, pressures, touchAreas)
        typingPatterns.add(pattern)
        if (typingPatterns.size > 200) typingPatterns.removeAt(0)
    }

    fun recordAppUsagePattern(apps: List<String>, durations: List<Long>, transitions: List<Long>) {
        val timeOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val pattern = AppUsagePattern(apps, durations, transitions, timeOfDay)
        appUsagePatterns.add(pattern)
        if (appUsagePatterns.size > 100) appUsagePatterns.removeAt(0)
    }

    fun recordTouchPressurePattern(pressure: Double, variance: Double, size: Double, duration: Long) {
        val pattern = TouchPressurePattern(pressure, variance, size, duration)
        touchPressurePatterns.add(pattern)
        if (touchPressurePatterns.size > 300) touchPressurePatterns.removeAt(0)
    }

    private fun generateCurrentProfile(context: Context): BehavioralProfile {
        val patterns = mutableMapOf<String, Any>()

        if (typingPatterns.isNotEmpty()) patterns["typing"] = analyzeTypingPatterns()
        if (walkingPatterns.isNotEmpty()) patterns["walking"] = analyzeWalkingPatterns()
        if (phoneHoldingPatterns.isNotEmpty()) patterns["holding"] = analyzeHoldingPatterns()
        if (appUsagePatterns.isNotEmpty()) patterns["app_usage"] = analyzeAppUsagePatterns()
        if (touchPressurePatterns.isNotEmpty()) patterns["touch"] = analyzeTouchPatterns()

        return BehavioralProfile(
            userId = "primary_user",
            confidence = calculateProfileConfidence(context),
            patterns = patterns,
            lastUpdated = System.currentTimeMillis(),
            sampleCount = getTotalSampleCount()
        )
    }

    private fun analyzeTypingPatterns(): Map<String, Double> {
        val snapshot = synchronized(typingPatterns) { typingPatterns.takeLast(50) }
        val avgDwell = snapshot.flatMap { it.dwellTimes }.average()
        val avgFlight = snapshot.flatMap { it.flightTimes }.average()
        val avgPress = snapshot.flatMap { it.pressure }.average()
        val avgArea = snapshot.flatMap { it.touchArea }.average()

        return mapOf(
            "avg_dwell_time" to if (avgDwell.isNaN()) 0.0 else avgDwell,
            "avg_flight_time" to if (avgFlight.isNaN()) 0.0 else avgFlight,
            "avg_pressure" to if (avgPress.isNaN()) 0.0 else avgPress,
            "avg_touch_area" to if (avgArea.isNaN()) 0.0 else avgArea
        )
    }

    private fun analyzeWalkingPatterns(): Map<String, Double> {
        val snapshot = synchronized(walkingPatterns) { walkingPatterns.takeLast(30) }
        val avgFreq = snapshot.map { it.stepFrequency }.average()
        val avgVar = snapshot.map { it.stepVariability }.average()
        return mapOf(
            "avg_step_frequency" to if (avgFreq.isNaN()) 0.0 else avgFreq,
            "avg_variability" to if (avgVar.isNaN()) 0.0 else avgVar
        )
    }

    private fun analyzeHoldingPatterns(): Map<String, Double> {
        val snapshot = synchronized(phoneHoldingPatterns) { phoneHoldingPatterns.takeLast(30) }
        val avgStab = snapshot.map { it.gripStability }.average()
        val avgTilt = snapshot.map { it.averageTilt }.average()
        return mapOf(
            "avg_grip_stability" to if (avgStab.isNaN()) 0.0 else avgStab,
            "avg_tilt" to if (avgTilt.isNaN()) 0.0 else avgTilt
        )
    }

    private fun analyzeAppUsagePatterns(): Map<String, Double> {
        val snapshot = synchronized(appUsagePatterns) { appUsagePatterns.takeLast(20) }
        val avgDur = snapshot.flatMap { it.usageDuration }.average()
        val avgTrans = snapshot.flatMap { it.transitionTimes }.average()
        return mapOf(
            "avg_session_duration" to if (avgDur.isNaN()) 0.0 else avgDur,
            "avg_transition_time" to if (avgTrans.isNaN()) 0.0 else avgTrans
        )
    }

    private fun analyzeTouchPatterns(): Map<String, Double> {
        val snapshot = synchronized(touchPressurePatterns) { touchPressurePatterns.takeLast(50) }
        val avgP = snapshot.map { it.averagePressure }.average()
        val avgS = snapshot.map { it.touchSize }.average()
        val avgD = snapshot.map { it.touchDuration.toDouble() }.average()
        return mapOf(
            "avg_pressure" to if (avgP.isNaN()) 0.0 else avgP,
            "avg_touch_size" to if (avgS.isNaN()) 0.0 else avgS,
            "avg_touch_duration" to if (avgD.isNaN()) 0.0 else avgD
        )
    }

    private fun calculateProfileConfidence(context: Context): Double {
        val count = getTotalSampleCount()
        val days = (System.currentTimeMillis() - SecurityPreferences.getFirstRunTimestamp(context)) / (24 * 60 * 60 * 1000L)
        val sampleConf = min(count.toDouble() / MIN_SAMPLES_FOR_ANALYSIS, 1.0)
        val timeConf = min(days.toDouble() / LEARNING_PERIOD_DAYS, 1.0)
        return (sampleConf + timeConf) / 2.0
    }

    private fun calculateProfileSimilarity(current: BehavioralProfile, stored: BehavioralProfile): Double {
        var totalSim = 0.0
        var count = 0

        for ((key, currentVal) in current.patterns) {
            val storedVal = stored.patterns[key]
            if (currentVal is Map<*, *> && storedVal is Map<*, *>) {
                totalSim += calculatePatternSimilarity(currentVal, storedVal)
                count++
            }
        }
        return if (count > 0) totalSim / count else 1.0
    }

    private fun calculatePatternSimilarity(p1: Map<*, *>, p2: Map<*, *>): Double {
        val commonKeys = p1.keys.intersect(p2.keys)
        if (commonKeys.isEmpty()) return 0.0

        var sum = 0.0
        for (k in commonKeys) {
            val v1 = (p1[k] as? Number)?.toDouble() ?: continue
            val v2 = (p2[k] as? Number)?.toDouble() ?: continue
            val denom = max(abs(v1), abs(v2)).coerceAtLeast(0.001)
            sum += 1.0 - (abs(v1 - v2) / denom).coerceAtMost(1.0)
        }
        return sum / commonKeys.size
    }

    private suspend fun handleBehavioralAnomaly(
        context: Context,
        similarity: Double,
        current: BehavioralProfile,
        stored: BehavioralProfile
    ) = withContext(Dispatchers.Main) {
        _behavioralState.value = BehavioralState.ANOMALY_DETECTED

        val severity = when {
            similarity < 0.35 -> PanicActionService.Severity.CRITICAL
            similarity < 0.55 -> PanicActionService.Severity.HIGH
            else -> PanicActionService.Severity.MEDIUM
        }

        Log.w(TAG, "Behavioral anomaly confirmed! Similarity: ${(similarity * 100).toInt()}%")
        EventLogger.log(context, "ANOMALY: Behavioral profile match dropped to ${(similarity * 100).toInt()}%")

        storeBehavioralAnomaly(context, similarity, current, stored)
        PanicActionService.trigger(context, "BEHAVIORAL_ANOMALY", severity)
    }

    private fun storeBehavioralAnomaly(context: Context, sim: Double, cur: BehavioralProfile, prev: BehavioralProfile) {
        val anomalyJson = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("similarity", sim)
            put("current", JSONObject(cur.patterns))
            put("baseline", JSONObject(prev.patterns))
        }
        SecurityPreferences.addBehavioralAnomaly(context, anomalyJson.toString())
    }

    private fun loadStoredPatterns(context: Context) {
        val profileJson = SecurityPreferences.getBehavioralProfile(context)
        if (profileJson.isNotEmpty()) {
            try {
                val json = JSONObject(profileJson)
                Log.d(TAG, "Loaded baseline behavioral profile for user: ${json.optString("userId")}")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing behavioral profile from storage", e)
            }
        }
    }

    private fun loadStoredProfile(context: Context): BehavioralProfile? {
        val jsonStr = SecurityPreferences.getBehavioralProfile(context)
        if (jsonStr.isEmpty()) return null
        return try {
            val json = JSONObject(jsonStr)
            val patternsObj = json.getJSONObject("patterns")
            val map = mutableMapOf<String, Any>()
            val keys = patternsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val subObj = patternsObj.getJSONObject(k)
                val subMap = mutableMapOf<String, Double>()
                val subKeys = subObj.keys()
                while (subKeys.hasNext()) {
                    val sk = subKeys.next()
                    subMap[sk] = subObj.getDouble(sk)
                }
                map[k] = subMap
            }
            BehavioralProfile(
                userId = json.getString("userId"),
                confidence = json.getDouble("confidence"),
                patterns = map,
                lastUpdated = json.getLong("lastUpdated"),
                sampleCount = json.getInt("sampleCount")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing behavioral profile", e)
            null
        }
    }

    private fun updateStoredProfile(context: Context, profile: BehavioralProfile) {
        val json = JSONObject().apply {
            put("userId", profile.userId)
            put("confidence", profile.confidence)
            put("lastUpdated", profile.lastUpdated)
            put("sampleCount", profile.sampleCount)

            val pObj = JSONObject()
            for ((k, v) in profile.patterns) {
                if (v is Map<*, *>) {
                    pObj.put(k, JSONObject(v))
                }
            }
            put("patterns", pObj)
        }
        SecurityPreferences.setBehavioralProfile(context, json.toString())
    }

    private fun calculateStepFrequency(magnitudes: List<Double>): Double {
        if (magnitudes.size < 2) return 0.0
        var stepPeaks = 0
        for (i in 1 until magnitudes.size - 1) {
            if (magnitudes[i] > 11.5 && magnitudes[i] > magnitudes[i - 1] && magnitudes[i] > magnitudes[i + 1]) {
                stepPeaks++
            }
        }
        val durationSec = (SENSOR_WINDOW_MS / 1000.0)
        return stepPeaks / durationSec
    }

    private fun calculateStepVariability(magnitudes: List<Double>): Double {
        return calculateVariance(magnitudes)
    }

    private fun calculateGripStability(rotations: List<Double>): Double {
        val v = calculateVariance(rotations)
        return (1.0 - min(v, 1.0)).coerceAtLeast(0.0)
    }

    private fun calculateVariance(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return values.map { (it - mean).pow(2) }.average()
    }
}

private fun SecurityPreferences.getFirstRunTimestamp(context: Context): Long =
    getInstance(context).getLong("FIRST_RUN_TIMESTAMP", System.currentTimeMillis()).also {
        if (!getInstance(context).contains("FIRST_RUN_TIMESTAMP")) {
            getInstance(context).edit().putLong("FIRST_RUN_TIMESTAMP", it).apply()
        }
    }

private fun SecurityPreferences.getBehavioralProfile(context: Context): String =
    getInstance(context).getString("BEHAVIORAL_PROFILE", "") ?: ""

private fun SecurityPreferences.setBehavioralProfile(context: Context, profile: String) =
    getInstance(context).edit().putString("BEHAVIORAL_PROFILE", profile).apply()

private fun SecurityPreferences.addBehavioralAnomaly(context: Context, anomaly: String) {
    val prefs = getInstance(context)
    val anomalies = prefs.getStringSet("BEHAVIORAL_ANOMALIES", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    anomalies.add(anomaly)
    if (anomalies.size > 50) {
        val trimmed = anomalies.toList().takeLast(50).toSet()
        prefs.edit().putStringSet("BEHAVIORAL_ANOMALIES", trimmed).apply()
    } else {
        prefs.edit().putStringSet("BEHAVIORAL_ANOMALIES", anomalies).apply()
    }
}
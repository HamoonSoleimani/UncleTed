package com.hamoon.uncleted.crypto

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.NativeSecurityBridge
import com.hamoon.uncleted.util.RootChecker
import com.hamoon.uncleted.util.RootExecutor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import java.nio.ByteBuffer
import java.security.SecureRandom

object EphemeralKeyDecayEngine : SensorEventListener {

    private const val TAG = "EphemeralDecayEngine"
    private const val KEY_SIZE_BYTES = 32
    private const val DEFAULT_HEARTBEAT_INTERVAL_MS = 1500L
    private const val DEFAULT_MAX_MISSED_HEARTBEATS = 3

    data class DecayStatus(
        val isRunning: Boolean = false,
        val isKeyLive: Boolean = false,
        val missedHeartbeats: Int = 0,
        val maxAllowedMisses: Int = DEFAULT_MAX_MISSED_HEARTBEATS,
        val lastHeartbeatEpoch: Long = 0L,
        val entropyPoolSize: Int = 0
    )

    private val _decayStatusFlow = MutableStateFlow(DecayStatus())
    val decayStatusFlow: StateFlow<DecayStatus> = _decayStatusFlow.asStateFlow()

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatLoopJob: Job? = null

    @Volatile
    private var ephemeralMasterKey: ByteArray? = null

    private var sensorJitterValue: Float = 0.0f
    private var lastPulseTimestamp: Long = 0L

    private var sensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null
    private val lock = Any()

    fun initialize(context: Context) {
        synchronized(lock) {
            if (sensorManager == null) {
                sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                accelSensor?.let {
                    sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
                }
            }
        }
    }

    @Synchronized
    fun provisionEphemeralSeed(context: Context, initialSeed: ByteArray? = null): Boolean {
        synchronized(lock) {
            purgeEphemeralKey(context)

            val seed = ByteArray(KEY_SIZE_BYTES)
            if (initialSeed != null && initialSeed.size == KEY_SIZE_BYTES) {
                System.arraycopy(initialSeed, 0, seed, 0, KEY_SIZE_BYTES)
            } else {
                SecureRandom().nextBytes(seed)
            }

            NativeSecurityBridge.pinMemory(seed)
            ephemeralMasterKey = seed
            lastPulseTimestamp = SystemClock.elapsedRealtime()

            startHeartbeatDecayCycle(context)
            Log.i(TAG, "Fail-Closed Ephemeral Key provisioned and locked in RAM.")
            EventLogger.log(context, "CRYPTO: Ephemeral fail-closed master key buffer initialized.")
            return true
        }
    }

    private fun startHeartbeatDecayCycle(context: Context) {
        heartbeatLoopJob?.cancel()
        heartbeatLoopJob = engineScope.launch {
            var consecutiveMisses = 0
            val maxAllowedMisses = SecurityPreferences.getDeviceProtectedPrefs(context)
                .getInt("EPHEMERAL_MAX_MISSED_HEARTBEATS", DEFAULT_MAX_MISSED_HEARTBEATS)
            val interval = SecurityPreferences.getDeviceProtectedPrefs(context)
                .getLong("EPHEMERAL_HEARTBEAT_INTERVAL_MS", DEFAULT_HEARTBEAT_INTERVAL_MS)

            while (isActive) {
                delay(interval)
                val now = SystemClock.elapsedRealtime()
                val delta = now - lastPulseTimestamp

                if (delta > (interval * 1.8f)) {
                    consecutiveMisses++
                    Log.w(TAG, "Heartbeat decay warning: missed pulse ($consecutiveMisses/$maxAllowedMisses).")
                } else {
                    consecutiveMisses = 0
                }

                _decayStatusFlow.value = DecayStatus(
                    isRunning = true,
                    isKeyLive = ephemeralMasterKey != null,
                    missedHeartbeats = consecutiveMisses,
                    maxAllowedMisses = maxAllowedMisses,
                    lastHeartbeatEpoch = lastPulseTimestamp,
                    entropyPoolSize = 64
                )

                if (consecutiveMisses >= maxAllowedMisses) {
                    Log.e(TAG, "FATAL: Ephemeral decay threshold reached. Evaporating keys & locking device.")
                    EventLogger.log(context, "CRITICAL: Ephemeral decay pulse expired. Triggering key evaporation.")
                    purgeEphemeralKey(context)
                    break
                }

                rollEphemeralKey(context)
            }
        }
    }

    fun submitHeartbeat(context: Context) {
        synchronized(lock) {
            lastPulseTimestamp = SystemClock.elapsedRealtime()
        }
    }

    private fun rollEphemeralKey(context: Context) {
        synchronized(lock) {
            val currentKey = ephemeralMasterKey ?: return

            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val cellNetworkType = telephony?.networkType ?: 0
            val uptime = SystemClock.elapsedRealtimeNanos()

            val dynamicEntropy = ByteBuffer.allocate(32)
                .putFloat(sensorJitterValue)
                .putInt(cellNetworkType)
                .putLong(uptime)
                .putLong(System.currentTimeMillis())
                .array()

            val hmac = HMac(SHA256Digest())
            hmac.init(KeyParameter(currentKey))
            hmac.update(dynamicEntropy, 0, dynamicEntropy.size)

            val nextKey = ByteArray(KEY_SIZE_BYTES)
            hmac.doFinal(nextKey, 0)

            NativeSecurityBridge.pinMemory(nextKey)
            NativeSecurityBridge.zeroByteArray(currentKey)
            NativeSecurityBridge.unpinMemory(currentKey)

            ephemeralMasterKey = nextKey
        }
    }

    @Synchronized
    fun getActiveKey(): ByteArray? {
        synchronized(lock) {
            val key = ephemeralMasterKey ?: return null
            val copy = ByteArray(key.size)
            System.arraycopy(key, 0, copy, 0, key.size)
            NativeSecurityBridge.pinMemory(copy)
            return copy
        }
    }

    @Synchronized
    fun purgeEphemeralKey(context: Context? = null) {
        synchronized(lock) {
            ephemeralMasterKey?.let {
                NativeSecurityBridge.zeroByteArray(it)
                NativeSecurityBridge.unpinMemory(it)
                ephemeralMasterKey = null
                Log.w(TAG, "Ephemeral key sanitized and purged from physical RAM.")
            }
            heartbeatLoopJob?.cancel()
            heartbeatLoopJob = null

            _decayStatusFlow.value = DecayStatus(
                isRunning = false,
                isKeyLive = false,
                missedHeartbeats = 0,
                lastHeartbeatEpoch = 0L
            )

            if (context != null) {
                engineScope.launch {
                    evictPlatformFbeKeys(context)
                }
            }
        }
    }

    private suspend fun evictPlatformFbeKeys(context: Context) {
        Log.e(TAG, "Evicting memory caches and locking device...")

        if (RootChecker.isDeviceRooted()) {
            val fbeEvictionCommands = listOf(
                "sync",
                "echo 3 > /proc/sys/vm/drop_caches",
                "input keyevent 26"
            )
            RootExecutor.runMultiple(fbeEvictionCommands, logErrors = false)
        }

        try {
            val strategy = DefenseCoordinator.resolveStrategy(context)
            strategy.evictMemoryKeysAndLock()
        } catch (e: Exception) {
            Log.e(TAG, "Strategy lock failed: ${e.message}")
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            try { dpm?.lockNow() } catch (_: Exception) {}
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            sensorJitterValue = (x * 31.0f) + (y * 17.0f) + (z * 11.0f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
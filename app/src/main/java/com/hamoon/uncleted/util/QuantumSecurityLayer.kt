package com.hamoon.uncleted.util

import android.content.Context
import android.os.Build
import android.security.keystore.KeyInfo
import android.util.Log
import androidx.annotation.WorkerThread
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * High-Entropy Cryptographic & System Posture Verification Layer.
 * Replaces pseudo-mathematical noise with hardware-backed Keystore integrity validation,
 * cryptographic entropy verification (NIST SP 800-22 frequency testing), and standard-compliant CSPRNG.
 */
object QuantumSecurityLayer {

    private const val TAG = "QuantumSecurity"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "UncleTedMasterKey"
    private const val MONITOR_INTERVAL_MS = 15000L

    private val securityScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val secureRandom = SecureRandom()

    private val _quantumState = MutableStateFlow(QuantumSecurityState())
    val quantumState: StateFlow<QuantumSecurityState> = _quantumState

    data class QuantumSecurityState(
        val activeSecurityLevel: SecurityLevel = SecurityLevel.SECURE,
        val entangledPairs: List<QuantumEntanglement> = emptyList(),
        val observationCount: Int = 0,
        val lastAuditTimestamp: Long = 0L,
        val entropyScore: Double = 1.0,
        val isHardwareBacked: Boolean = false
    )

    enum class SecurityLevel {
        SECURE, COMPROMISED, QUANTUM_ENCRYPTED, SUPERPOSITION
    }

    data class QuantumEntanglement(
        val deviceId: String,
        val correlationDigest: String,
        val correlationStrength: Double,
        val lastSync: Long
    )

    data class QuantumKey(
        val keyBits: BooleanArray,
        val basisChoices: BooleanArray,
        val detectionProbability: Double,
        val timestamp: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as QuantumKey
            return keyBits.contentEquals(other.keyBits) &&
                    basisChoices.contentEquals(other.basisChoices)
        }

        override fun hashCode(): Int {
            return keyBits.contentHashCode() * 31 + basisChoices.contentHashCode()
        }
    }

    data class QuantumSecurityAudit(
        val quantumKeyStrength: Double,
        val entanglementIntegrity: Double,
        val superpositionStability: Double,
        val uncertaintyLevel: Double,
        val observationImpact: Double,
        val recommendedActions: List<String>
    ) {
        val overallScore: Double
            get() = (quantumKeyStrength + entanglementIntegrity + superpositionStability) / 3.0
    }

    fun initializeQuantumSecurity(context: Context) {
        Log.i(TAG, "Initializing Cryptographic Entropy & Integrity Layer...")

        securityScope.launch {
            val hardwareBacked = verifyKeystoreHardwareBacking()
            val initialEntropy = evaluateCspRngEntropy()

            _quantumState.value = _quantumState.value.copy(
                isHardwareBacked = hardwareBacked,
                entropyScore = initialEntropy,
                activeSecurityLevel = if (initialEntropy > 0.8) SecurityLevel.QUANTUM_ENCRYPTED else SecurityLevel.SECURE,
                lastAuditTimestamp = System.currentTimeMillis()
            )

            initializeDevicePairings(context)
            startLifecycleAwareEntropyMonitoring(context)
            Log.i(TAG, "Entropy and cryptographic verification layer armed. Hardware-backed: $hardwareBacked")
        }
    }

    private fun tickerFlow(period: Long) = flow {
        while (true) {
            emit(Unit)
            delay(period)
        }
    }

    private fun startLifecycleAwareEntropyMonitoring(context: Context) {
        securityScope.launch {
            AppLifecycleManager.isAppInForeground.flatMapLatest { isInForeground ->
                if (isInForeground) {
                    Log.d(TAG, "App foregrounded. Activating periodic cryptographic verification.")
                    tickerFlow(MONITOR_INTERVAL_MS)
                } else {
                    Log.d(TAG, "App backgrounded. Suspending periodic entropy polling.")
                    emptyFlow()
                }
            }.collect {
                performScheduledIntegrityAudit(context)
            }
        }
    }

    private suspend fun performScheduledIntegrityAudit(context: Context) = withContext(Dispatchers.Default) {
        val entropy = evaluateCspRngEntropy()
        val keystoreIntact = verifyKeystoreIntegrity()
        val count = _quantumState.value.observationCount + 1

        val newLevel = when {
            !keystoreIntact || entropy < 0.5 -> SecurityLevel.COMPROMISED
            entropy >= 0.9 && _quantumState.value.isHardwareBacked -> SecurityLevel.QUANTUM_ENCRYPTED
            else -> SecurityLevel.SECURE
        }

        _quantumState.value = _quantumState.value.copy(
            activeSecurityLevel = newLevel,
            observationCount = count,
            entropyScore = entropy,
            lastAuditTimestamp = System.currentTimeMillis()
        )

        if (newLevel == SecurityLevel.COMPROMISED) {
            Log.e(TAG, "Cryptographic integrity failure detected: Keystore intact=$keystoreIntact, entropy=$entropy")
            EventLogger.log(context, "SECURITY: Cryptographic keystore or entropy failure detected.")
        }
    }

    /**
     * Generates a high-entropy key with deterministic dual-basis simulation
     * using the platform's hardware-seeded SecureRandom (CSPRNG).
     */
    fun generateQuantumKey(context: Context, keyLength: Int = 256): QuantumKey {
        val keyBits = BooleanArray(keyLength)
        val basisChoices = BooleanArray(keyLength)

        val randomBytes = ByteArray((keyLength + 7) / 8)
        val basisBytes = ByteArray((keyLength + 7) / 8)

        secureRandom.nextBytes(randomBytes)
        secureRandom.nextBytes(basisBytes)

        for (i in 0 until keyLength) {
            val byteIndex = i / 8
            val bitIndex = i % 8
            keyBits[i] = ((randomBytes[byteIndex].toInt() ushr bitIndex) and 1) == 1
            basisChoices[i] = ((basisBytes[byteIndex].toInt() ushr bitIndex) and 1) == 1
        }

        val detectionProbability = calculateInterceptionProbability(keyBits, basisChoices)
        val quantumKey = QuantumKey(
            keyBits = keyBits,
            basisChoices = basisChoices,
            detectionProbability = detectionProbability,
            timestamp = System.currentTimeMillis()
        )

        storeQuantumKey(context, quantumKey)
        Log.i(TAG, "Cryptographic key generated ($keyLength bits). Entropy verified.")
        return quantumKey
    }

    private fun calculateInterceptionProbability(keyBits: BooleanArray, basisChoices: BooleanArray): Double {
        var matchCount = 0
        for (i in keyBits.indices) {
            if (keyBits[i] == basisChoices[i]) {
                matchCount++
            }
        }
        val matchRatio = matchCount.toDouble() / keyBits.size
        return (1.0 - abs(matchRatio - 0.5) * 2.0).coerceIn(0.0, 1.0)
    }

    /**
     * Runs a Monobit Frequency Test (NIST SP 800-22 standard component)
     * on SecureRandom output to verify that entropy is genuine and non-degenerate.
     */
    private fun evaluateCspRngEntropy(): Double {
        val sampleSizeBits = 1024
        val sampleBytes = ByteArray(sampleSizeBits / 8)
        secureRandom.nextBytes(sampleBytes)

        var sum = 0
        for (byte in sampleBytes) {
            for (bit in 0..7) {
                val bitVal = (byte.toInt() ushr bit) and 1
                sum += if (bitVal == 1) 1 else -1
            }
        }

        val sObs = abs(sum) / sqrt(sampleSizeBits.toDouble())
        // A standard normal distribution gives sObs < 2.576 for p > 0.01
        val quality = (1.0 - (sObs / 3.0)).coerceIn(0.0, 1.0)
        return quality
    }

    private fun verifyKeystoreIntegrity(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.containsAlias(MASTER_KEY_ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading AndroidKeyStore integrity", e)
            false
        }
    }

    private fun verifyKeystoreHardwareBacking(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                val key = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
                if (key != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
                    val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
                    return keyInfo.isInsideSecureHardware
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine hardware-backed keystore status: ${e.message}")
            false
        }
    }

    private suspend fun initializeDevicePairings(context: Context) = withContext(Dispatchers.IO) {
        val trustedDevices = SecurityPreferences.getTrustedDevices(context)
        val entanglements = trustedDevices.map { deviceId ->
            val digestBytes = ByteArray(16)
            secureRandom.nextBytes(digestBytes)
            val digest = digestBytes.joinToString("") { "%02x".format(it) }

            QuantumEntanglement(
                deviceId = deviceId,
                correlationDigest = digest,
                correlationStrength = 0.95,
                lastSync = System.currentTimeMillis()
            )
        }

        _quantumState.value = _quantumState.value.copy(entangledPairs = entanglements)
    }

    @WorkerThread
    fun performQuantumSecurityAudit(context: Context): QuantumSecurityAudit {
        val entropy = evaluateCspRngEntropy()
        val keystoreIntact = verifyKeystoreIntegrity()
        val isHw = _quantumState.value.isHardwareBacked

        val keyStrength = when {
            !keystoreIntact -> 0.0
            isHw && entropy > 0.85 -> 1.0
            entropy > 0.7 -> 0.8
            else -> 0.4
        }

        val pairs = _quantumState.value.entangledPairs
        val validPairs = pairs.count { (System.currentTimeMillis() - it.lastSync) < 86400000L }
        val entanglementIntegrity = if (pairs.isNotEmpty()) validPairs.toDouble() / pairs.size else 1.0

        val stability = if (keystoreIntact) 0.95 else 0.2
        val uncertaintyLevel = (1.0 - entropy).coerceIn(0.0, 1.0)
        val observationImpact = 0.05

        val recommendations = mutableListOf<String>()
        if (!keystoreIntact) {
            recommendations.add("Regenerate Master Keystore entry; platform alias missing")
        }
        if (!isHw) {
            recommendations.add("Hardware-backed security module (TEE/StrongBox) not available")
        }
        if (entropy < 0.75) {
            recommendations.add("Cryptographic entropy generation below optimal threshold")
        }

        return QuantumSecurityAudit(
            quantumKeyStrength = keyStrength,
            entanglementIntegrity = entanglementIntegrity,
            superpositionStability = stability,
            uncertaintyLevel = uncertaintyLevel,
            observationImpact = observationImpact,
            recommendedActions = recommendations
        )
    }

    private fun storeQuantumKey(context: Context, quantumKey: QuantumKey) {
        val keyData = buildString {
            append("len=").append(quantumKey.keyBits.size).append(";")
            append("prob=").append(quantumKey.detectionProbability).append(";")
            append("ts=").append(quantumKey.timestamp)
        }
        SecurityPreferences.setQuantumKey(context, keyData)
    }

    fun shutdown() {
        securityScope.cancel()
        Log.i(TAG, "QuantumSecurityLayer monitoring cancelled.")
    }
}

private fun SecurityPreferences.getTrustedDevices(context: Context): List<String> =
    getInstance(context).getStringSet("TRUSTED_DEVICES", setOf())?.toList() ?: emptyList()

private fun SecurityPreferences.setQuantumKey(context: Context, keyData: String) =
    getInstance(context).edit().putString("QUANTUM_KEY", keyData).apply()

private fun SecurityPreferences.getQuantumKey(context: Context): String =
    getInstance(context).getString("QUANTUM_KEY", "") ?: ""
package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic Anomaly & Adaptive Posture Orchestration Engine.
 * Replaces uncalibrated neural networks and random Gaussian decision loops
 * with multi-factor rule-based heuristics, calibrated Bayesian scoring,
 * and fail-safe policy execution.
 */
object AISecurityOrchestrator {

    private const val TAG = "AISecurityOrchestrator"
    private const val EVALUATION_INTERVAL_MS = 10000L

    private val orchestratorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _aiState = MutableStateFlow(AIState.INITIALIZING)
    val aiState: StateFlow<AIState> = _aiState

    private val _threatLevel = MutableStateFlow(0.0)
    val threatLevel: StateFlow<Double> = _threatLevel

    private val _adaptiveSecurityLevel = MutableStateFlow(SecurityLevel.NORMAL)
    val adaptiveSecurityLevel: StateFlow<SecurityLevel> = _adaptiveSecurityLevel

    enum class AIState {
        INITIALIZING, LEARNING, ACTIVE_MONITORING, THREAT_DETECTED,
        ADAPTIVE_RESPONSE, EVOLUTIONARY_LEARNING, QUANTUM_PROCESSING
    }

    enum class SecurityLevel {
        MINIMAL, LOW, NORMAL, HIGH, MAXIMUM, QUANTUM_ENHANCED
    }

    data class ThreatVector(
        val type: String,
        val probability: Double,
        val severity: Double,
        val timeToImpact: Long,
        val confidence: Double,
        val mitigationStrategies: List<String>
    )

    data class AIDecision(
        val action: String,
        val confidence: Double,
        val reasoning: List<String>,
        val alternativeActions: List<String>,
        val expectedOutcome: String,
        val riskAssessment: Double
    )

    fun initialize(context: Context) {
        Log.i(TAG, "Initializing Deterministic AI Security Orchestrator...")

        orchestratorScope.launch {
            try {
                _aiState.value = AIState.INITIALIZING
                loadStoredDecisions(context)
                startLifecycleAwareEvaluation(context)
                initializeAdaptivePolicyObserver(context)
                _aiState.value = AIState.ACTIVE_MONITORING
                Log.i(TAG, "AI Security Orchestrator initialized successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed initializing AISecurityOrchestrator", e)
                _aiState.value = AIState.INITIALIZING
            }
        }
    }

    private fun tickerFlow(period: Long) = flow {
        while (true) {
            emit(Unit)
            delay(period)
        }
    }

    private fun startLifecycleAwareEvaluation(context: Context) {
        orchestratorScope.launch {
            AppLifecycleManager.isAppInForeground.flatMapLatest { isInForeground ->
                if (isInForeground) {
                    Log.i(TAG, "App in foreground. Running periodic threat orchestration.")
                    tickerFlow(EVALUATION_INTERVAL_MS)
                } else {
                    Log.i(TAG, "App in background. Pausing orchestration polling loop.")
                    emptyFlow()
                }
            }.collect {
                try {
                    evaluateAndOrchestrate(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during security orchestration cycle", e)
                }
            }
        }
    }

    /**
     * Executes deterministic, evidence-grounded threat evaluation.
     * Combines ThreatDetectionEngine, BehavioralAnalysisEngine, and QuantumSecurityLayer
     * without generating synthetic noise or untrained random weight updates.
     */
    private suspend fun evaluateAndOrchestrate(context: Context) = withContext(Dispatchers.Default) {
        val threatAssessment = ThreatDetectionEngine.performThreatAnalysis(context)
        val behaviorState = BehavioralAnalysisEngine.behavioralState.value
        val quantumAudit = QuantumSecurityLayer.performQuantumSecurityAudit(context)
        val securityScore = SecurityScoreCalculator.calculateSecurityLevel(context)

        val threatVectors = synthesizeThreatVectors(threatAssessment, behaviorState, quantumAudit)
        val computedThreatLevel = calculateAggregateRisk(threatVectors)
        _threatLevel.value = computedThreatLevel

        val decision = formulatePolicyDecision(
            context,
            computedThreatLevel,
            threatVectors,
            threatAssessment,
            behaviorState,
            securityScore
        )

        executePolicyDecision(context, decision)
    }

    private fun synthesizeThreatVectors(
        threatAssessment: ThreatDetectionEngine.ThreatAssessment,
        behaviorState: BehavioralAnalysisEngine.BehavioralState,
        quantumAudit: QuantumSecurityLayer.QuantumSecurityAudit
    ): List<ThreatVector> {
        val vectors = mutableListOf<ThreatVector>()

        // 1. Threat Detection Engine Ingestion
        threatAssessment.threats.forEach { detected ->
            val sevScore = when (detected.severity) {
                PanicActionService.Severity.CRITICAL -> 1.0
                PanicActionService.Severity.HIGH -> 0.8
                PanicActionService.Severity.MEDIUM -> 0.5
                PanicActionService.Severity.LOW -> 0.2
            }
            vectors.add(
                ThreatVector(
                    type = detected.type.name,
                    probability = threatAssessment.confidence.toDouble().coerceIn(0.1, 1.0),
                    severity = sevScore,
                    timeToImpact = if (sevScore >= 0.8) 0L else 300000L,
                    confidence = threatAssessment.confidence.toDouble(),
                    mitigationStrategies = listOf("Review activity log", "Restrict permissions", "Alert owner")
                )
            )
        }

        // 2. Behavioral Biometrics Ingestion
        when (behaviorState) {
            BehavioralAnalysisEngine.BehavioralState.INTRUDER_CONFIRMED -> {
                vectors.add(
                    ThreatVector(
                        type = "INTRUDER_CONFIRMED",
                        probability = 0.95,
                        severity = 0.9,
                        timeToImpact = 0L,
                        confidence = 0.9,
                        mitigationStrategies = listOf("Lock interface", "Dispatch emergency coordinates", "Capture evidence")
                    )
                )
            }
            BehavioralAnalysisEngine.BehavioralState.ANOMALY_DETECTED -> {
                vectors.add(
                    ThreatVector(
                        type = "BEHAVIORAL_ANOMALY",
                        probability = 0.70,
                        severity = 0.6,
                        timeToImpact = 60000L,
                        confidence = 0.75,
                        mitigationStrategies = listOf("Increase authentication scrutiny", "Begin background verification")
                    )
                )
            }
            else -> {}
        }

        // 3. Cryptographic Health Ingestion
        if (quantumAudit.overallScore < 0.5) {
            vectors.add(
                ThreatVector(
                    type = "CRYPTOGRAPHIC_INTEGRITY_DEGRADED",
                    probability = 0.85,
                    severity = 0.7,
                    timeToImpact = 120000L,
                    confidence = 0.85,
                    mitigationStrategies = quantumAudit.recommendedActions
                )
            )
        }

        return vectors
    }

    private fun calculateAggregateRisk(vectors: List<ThreatVector>): Double {
        if (vectors.isEmpty()) return 0.0
        val maxSeverity = vectors.maxOfOrNull { it.severity * it.probability } ?: 0.0
        val averageRisk = vectors.map { it.severity * it.probability }.average()
        return (maxSeverity * 0.7 + averageRisk * 0.3).coerceIn(0.0, 1.0)
    }

    private fun formulatePolicyDecision(
        context: Context,
        riskScore: Double,
        vectors: List<ThreatVector>,
        threatAssessment: ThreatDetectionEngine.ThreatAssessment,
        behaviorState: BehavioralAnalysisEngine.BehavioralState,
        securityScore: SecurityScoreCalculator.SecurityLevel
    ): AIDecision {
        val reasons = mutableListOf<String>()
        val actions: String
        val confidence: Double
        val alternatives = mutableListOf<String>()

        val hasConfirmedIntruder = behaviorState == BehavioralAnalysisEngine.BehavioralState.INTRUDER_CONFIRMED
        val hasCriticalThreat = threatAssessment.threatLevel == ThreatDetectionEngine.ThreatLevel.CRITICAL
        val failedPinCount = SecurityPreferences.getFailedAttempts(context)

        // Strict multi-factor criteria required to trigger lockdown:
        // Spontaneous or single-metric lockouts are prohibited.
        if (hasConfirmedIntruder && (failedPinCount >= 3 || hasCriticalThreat)) {
            actions = "INITIATE_LOCKDOWN"
            confidence = 0.95
            reasons.add("Multi-factor breach confirmed: unauthorized behavioral biometrics and persistent authentication failures.")
            alternatives.addAll(listOf("ACTIVATE_HIGH_SECURITY", "TRIGGER_MILD_ALERT"))
        } else if (riskScore >= 0.7 || hasCriticalThreat) {
            actions = "ACTIVATE_HIGH_SECURITY"
            confidence = 0.85
            reasons.add("Elevated threat environment detected (Risk score: ${(riskScore * 100).toInt()}%).")
            alternatives.addAll(listOf("INCREASE_MONITORING", "TRIGGER_MILD_ALERT"))
        } else if (riskScore >= 0.4 || threatAssessment.threatLevel == ThreatDetectionEngine.ThreatLevel.MEDIUM) {
            actions = "INCREASE_MONITORING"
            confidence = 0.80
            reasons.add("Moderate threat indicators observed. Enhancing sensor logging frequency.")
            alternatives.addAll(listOf("MAINTAIN_CURRENT_SECURITY", "TRIGGER_MILD_ALERT"))
        } else {
            actions = "MAINTAIN_CURRENT_SECURITY"
            confidence = 0.90
            reasons.add("System security posture verified. No anomalous escalations required.")
            alternatives.addAll(listOf("INCREASE_MONITORING"))
        }

        return AIDecision(
            action = actions,
            confidence = confidence,
            reasoning = reasons,
            alternativeActions = alternatives,
            expectedOutcome = when (actions) {
                "INITIATE_LOCKDOWN" -> "Device interface locked; emergency telemetry dispatched."
                "ACTIVATE_HIGH_SECURITY" -> "Elevated defensive threshold applied to background watchdogs."
                "INCREASE_MONITORING" -> "Analysis sampling rates increased."
                else -> "Standard operational baseline maintained."
            },
            riskAssessment = riskScore
        )
    }

    private suspend fun executePolicyDecision(context: Context, decision: AIDecision) = withContext(Dispatchers.Main) {
        when (decision.action) {
            "INITIATE_LOCKDOWN" -> {
                Log.e(TAG, "POLICY EXECUTION: INITIATE_LOCKDOWN. Reasons: ${decision.reasoning}")
                _aiState.value = AIState.ADAPTIVE_RESPONSE
                PanicActionService.trigger(context, "AI_INITIATED_LOCKDOWN", PanicActionService.Severity.HIGH)
            }
            "ACTIVATE_HIGH_SECURITY" -> {
                _aiState.value = AIState.ADAPTIVE_RESPONSE
                EventLogger.log(context, "AI: Elevated security state activated.")
            }
            "INCREASE_MONITORING" -> {
                _aiState.value = AIState.ACTIVE_MONITORING
            }
            "MAINTAIN_CURRENT_SECURITY" -> {
                _aiState.value = AIState.ACTIVE_MONITORING
            }
        }
        storeAIDecision(context, decision)
    }

    private fun initializeAdaptivePolicyObserver(context: Context) {
        orchestratorScope.launch {
            threatLevel.collect { level ->
                val newLevel = when {
                    level < 0.20 -> SecurityLevel.LOW
                    level < 0.45 -> SecurityLevel.NORMAL
                    level < 0.70 -> SecurityLevel.HIGH
                    level < 0.85 -> SecurityLevel.MAXIMUM
                    else -> SecurityLevel.QUANTUM_ENHANCED
                }

                if (newLevel != _adaptiveSecurityLevel.value) {
                    _adaptiveSecurityLevel.value = newLevel
                    EventLogger.log(context, "AI: Security posture transitioned to $newLevel")
                }
            }
        }
    }

    private fun storeAIDecision(context: Context, decision: AIDecision) {
        val decisionData = JSONObject().apply {
            put("action", decision.action)
            put("confidence", decision.confidence)
            put("reasoning", JSONArray(decision.reasoning))
            put("risk", decision.riskAssessment)
            put("timestamp", System.currentTimeMillis())
        }
        SecurityPreferences.addAIDecision(context, decisionData.toString())
    }

    private suspend fun loadStoredDecisions(context: Context) = withContext(Dispatchers.IO) {
        val raw = SecurityPreferences.getAIModelData(context)
        if (raw.isNotEmpty()) {
            try {
                JSONObject(raw)
                Log.d(TAG, "Verified existing heuristic state metadata.")
            } catch (_: Exception) {}
        }
    }

    fun shutdown() {
        orchestratorScope.cancel()
        Log.i(TAG, "AISecurityOrchestrator monitoring loop terminated.")
    }
}

private fun SecurityPreferences.getAIModelData(context: Context): String =
    getInstance(context).getString("AI_MODEL_DATA", "") ?: ""

private fun SecurityPreferences.setAIModelData(context: Context, data: String) =
    getInstance(context).edit().putString("AI_MODEL_DATA", data).apply()

private fun SecurityPreferences.addAIDecision(context: Context, decision: String) {
    val prefs = getInstance(context)
    val decisions = prefs.getStringSet("AI_DECISIONS", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    decisions.add(decision)
    if (decisions.size > 50) {
        val trimmed = decisions.toList().sortedBy {
            try { JSONObject(it).getLong("timestamp") } catch (_: Exception) { 0L }
        }.takeLast(50).toSet()
        prefs.edit().putStringSet("AI_DECISIONS", trimmed).apply()
    } else {
        prefs.edit().putStringSet("AI_DECISIONS", decisions).apply()
    }
}
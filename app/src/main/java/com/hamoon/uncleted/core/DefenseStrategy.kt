package com.hamoon.uncleted.core

interface DefenseStrategy {
    val profileName: String
    val isHardwareSecured: Boolean

    suspend fun executeWipe(reason: String)
    suspend fun setUsbDataPortEnabled(enabled: Boolean)
    suspend fun configureBruteForceThreshold(maxFailedAttempts: Int)
    suspend fun evictMemoryKeysAndLock()
    suspend fun disableBiometrics(disable: Boolean)
    suspend fun isolateRadiosAndNetwork()
}
package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.core.DefenseCoordinator
import kotlinx.coroutines.runBlocking

object DeviceAdminHelper {
    private const val TAG = "DeviceAdminHelper"

    /**
     * Executes the appropriate device wipe strategy through DefenseCoordinator.
     * Guarantees that Route A (Device Owner hardware revocation) or Route B (Root key demolition)
     * executes cleanly without raw block corruption.
     */
    fun wipeDeviceImmediately(context: Context, reason: String = "EMERGENCY_WIPE_INVOCATION") {
        Log.w(TAG, "Invoking wipe protocol through DefenseCoordinator: reason=$reason")

        try {
            runBlocking {
                val strategy = DefenseCoordinator.resolveStrategy(context)
                Log.i(TAG, "Executing wipe via active strategy: ${strategy.profileName}")
                strategy.executeWipe(reason)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Strategy execution failed, triggering emergency fallback engine", e)
            runBlocking {
                EmergencyDestructionEngine.executeDestructionSequence(context, reason)
            }
        }
    }
}
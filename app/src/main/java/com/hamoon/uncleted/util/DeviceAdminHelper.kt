package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.core.DefenseCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DeviceAdminHelper {
    private const val TAG = "DeviceAdminHelper"

    /**
     * Executes the appropriate device wipe strategy through DefenseCoordinator asynchronously.
     * Prevents ANR deadlocks by dispatching the operation off the main thread while maintaining
     * a stable non-blocking invocation interface for legacy receivers and UI actions.
     */
    fun wipeDeviceImmediately(context: Context, reason: String = "EMERGENCY_WIPE_INVOCATION") {
        Log.w(TAG, "Invoking wipe protocol through DefenseCoordinator: reason=$reason")
        EventLogger.log(context, "INVOCATION: wipeDeviceImmediately triggered (Reason: $reason)")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val strategy = DefenseCoordinator.resolveStrategy(context)
                Log.i(TAG, "Executing wipe via active strategy: ${strategy.profileName}")
                strategy.executeWipe(reason)
            } catch (e: Exception) {
                Log.e(TAG, "Strategy execution failed, triggering EmergencyDestructionEngine fallback", e)
                EmergencyDestructionEngine.executeDestructionSequence(context, reason)
            }
        }
    }
}
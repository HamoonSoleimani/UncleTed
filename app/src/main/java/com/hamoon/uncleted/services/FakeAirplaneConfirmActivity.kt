package com.hamoon.uncleted.services

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hamoon.uncleted.R
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.ActivityFakeAirplaneConfirmBinding
import com.hamoon.uncleted.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FakeAirplaneConfirmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFakeAirplaneConfirmBinding

    companion object {
        private const val TAG = "FakeAirplaneConfirm"

        /**
         * Executes the configured decoy action cleanly without disabling communications
         * prematurely during silent duress dispatch.
         */
        fun executeTrapProtocol(context: Context) {
            Log.e(TAG, "!!! FAKE AIRPLANE TILE TRIGGERED: EXECUTING DEFENSIVE COUNTERMEASURES !!!")
            EventLogger.log(context, "TRAP: Decoy Airplane Mode tile action engaged.")

            val action = SecurityPreferences.getFakeAirplaneAction(context)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val strategy = DefenseCoordinator.resolveStrategy(context)

                    when (action) {
                        "STANDARD_WIPE" -> {
                            Log.i(TAG, "Trap executing: Standard Platform Wipe (Factory Reset)")
                            strategy.executeStandardWipe("FAKE_AIRPLANE_STANDARD_WIPE")
                        }
                        "WIPE" -> {
                            Log.e(TAG, "Trap executing: Immediate Cryptographic Wipe (Lethal)")
                            strategy.isolateRadiosAndNetwork()
                            strategy.executeWipe("FAKE_AIRPLANE_TILE_TRAP")
                            PanicActionService.trigger(
                                context,
                                "FAKE_AIRPLANE_TILE_ACTIVATED",
                                PanicActionService.Severity.CRITICAL
                            )
                        }
                        "LOCK" -> {
                            Log.w(TAG, "Trap executing: Lock device to BFU")
                            strategy.isolateRadiosAndNetwork()
                            strategy.evictMemoryKeysAndLock()
                            PanicActionService.trigger(
                                context,
                                "FAKE_AIRPLANE_TILE_ACTIVATED",
                                PanicActionService.Severity.HIGH
                            )
                        }
                        "DURESS" -> {
                            Log.w(TAG, "Trap executing: Silent Duress Canary & Capture (Radios retained for dispatch)")
                            // Do NOT isolate radios prior to canary/email/SMS dispatch!
                            PanicActionService.trigger(
                                context,
                                "FAKE_AIRPLANE_TILE_ACTIVATED",
                                PanicActionService.Severity.HIGH
                            )
                        }
                        else -> {
                            strategy.isolateRadiosAndNetwork()
                            strategy.evictMemoryKeysAndLock()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing fake airplane tile trap", e)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureWindowFlags()

        try {
            binding = ActivityFakeAirplaneConfirmBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            Log.e(TAG, "Error inflating FakeAirplaneConfirmActivity layout", e)
            finish()
            return
        }

        val isPinChallengeRequired = SecurityPreferences.isFakeAirplanePinChallengeEnabled(this)
        if (isPinChallengeRequired) {
            binding.layoutPinChallenge.visibility = View.VISIBLE
        } else {
            binding.layoutPinChallenge.visibility = View.GONE
        }

        // Cancel Button: Safe dismiss for the real owner
        binding.btnCancel.setOnClickListener {
            Log.i(TAG, "Fake Airplane Mode activation cancelled by user.")
            finish()
        }

        // Turn On Button: Executes the trap or validates the PIN
        binding.btnConfirm.setOnClickListener {
            handleConfirmation()
        }
    }

    private fun configureWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    private fun handleConfirmation() {
        val isPinChallengeRequired = SecurityPreferences.isFakeAirplanePinChallengeEnabled(this)

        if (isPinChallengeRequired) {
            val enteredPin = binding.etPinEntry.text?.toString()?.trim().orEmpty()
            val normalPin = SecurityPreferences.getNormalPin(this)

            // If the real owner enters their normal PIN, safely exit without triggering the trap
            if (!normalPin.isNullOrEmpty() && enteredPin == normalPin) {
                Log.i(TAG, "Owner authenticated with normal PIN. Disarming trap.")
                Toast.makeText(this, "Airplane mode toggle aborted.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        executeTrapProtocol(applicationContext)
        finish()
    }
}

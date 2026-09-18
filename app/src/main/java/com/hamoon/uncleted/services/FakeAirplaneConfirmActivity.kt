package com.hamoon.uncleted.services

import android.content.Intent
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

        // If no PIN challenge is enabled or an unauthorized user confirmed: Trigger the trap
        executeTrapProtocol()
        finish()
    }

    private fun executeTrapProtocol() {
        Log.e(TAG, "!!! FAKE AIRPLANE TILE CONFIRMED: EXECUTING DEFENSIVE COUNTERMEASURES !!!")
        EventLogger.log(this, "TRAP: Decoy Airplane Mode confirmed. Executing defensive protocol.")

        val action = SecurityPreferences.getFakeAirplaneAction(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val strategy = DefenseCoordinator.resolveStrategy(applicationContext)

                // Step 1: Immediately isolate radios to simulate authentic airplane mode
                strategy.isolateRadiosAndNetwork()

                // Step 2: Execute selected action
                when (action) {
                    "WIPE" -> {
                        Log.e(TAG, "Trap executing: Immediate Cryptographic Wipe")
                        strategy.executeWipe("FAKE_AIRPLANE_TILE_TRAP")
                        PanicActionService.trigger(
                            applicationContext,
                            "FAKE_AIRPLANE_TILE_ACTIVATED",
                            PanicActionService.Severity.CRITICAL
                        )
                    }
                    "LOCK" -> {
                        Log.w(TAG, "Trap executing: Lock device to BFU")
                        strategy.evictMemoryKeysAndLock()
                        PanicActionService.trigger(
                            applicationContext,
                            "FAKE_AIRPLANE_TILE_ACTIVATED",
                            PanicActionService.Severity.HIGH
                        )
                    }
                    "DURESS" -> {
                        Log.w(TAG, "Trap executing: Silent Duress Canary & Capture")
                        PanicActionService.trigger(
                            applicationContext,
                            "FAKE_AIRPLANE_TILE_ACTIVATED",
                            PanicActionService.Severity.HIGH
                        )
                    }
                    else -> {
                        strategy.evictMemoryKeysAndLock()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing fake airplane tile trap", e)
            }
        }
    }
}
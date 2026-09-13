package com.hamoon.uncleted

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.ActivityLockScreenBinding
import com.hamoon.uncleted.honeypot.HoneypotLauncherActivity
import com.hamoon.uncleted.services.PanicActionService

class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private val TAG = "LockScreenActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityLockScreenBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            Log.e(TAG, "Critical UI Crash during LockScreen inflation", e)
            finish()
            return
        }

        // ### FIX: Block Back Button ###
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing to block back navigation
            }
        })

        // ... (Existing intent handling code matches previous logic) ...
        val reason = intent.getStringExtra("REASON")
        if (reason == "UNINSTALL_ATTEMPT") {
            binding.tvTitleLockScreen.text = getString(R.string.unauthorized_action_title)
            binding.tvSubtitleLockScreen.text = getString(R.string.unauthorized_action_subtitle)
        }

        val normalPin = SecurityPreferences.getNormalPin(this)
        val duressPin = SecurityPreferences.getDuressPin(this)
        val wipePin = SecurityPreferences.getWipePin(this)
        val honeypotPin = SecurityPreferences.getHoneypotPin(this)

        if (normalPin.isNullOrEmpty() || duressPin.isNullOrEmpty() || wipePin.isNullOrEmpty()) {
            binding.tvTitleLockScreen.text = "Configuration Error"
            binding.tvSubtitleLockScreen.text = "Security PINs have not been set."
            binding.etPinEntry.isEnabled = false
            binding.btnUnlock.isEnabled = false
        }

        binding.btnUnlock.setOnClickListener {
            val enteredPin = binding.etPinEntry.text.toString()

            if (normalPin.isNullOrEmpty()) {
                Toast.makeText(this, "PINs not configured!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            when (enteredPin) {
                normalPin -> {
                    SecurityPreferences.resetFailedAttempts(this)
                    // ### FIX: Stop pinning before finishing ###
                    try { stopLockTask() } catch (e: Exception) {}
                    finish()
                }
                duressPin -> {
                    PanicActionService.trigger(this, "DURESS_PIN", PanicActionService.Severity.HIGH)
                    try { stopLockTask() } catch (e: Exception) {}
                    finish()
                }
                wipePin -> {
                    PanicActionService.trigger(this, "WIPE_PIN", PanicActionService.Severity.CRITICAL)
                    try { stopLockTask() } catch (e: Exception) {}
                    finish()
                }
                honeypotPin -> {
                    PanicActionService.trigger(this, "HONEYPOT_ACTIVATED", PanicActionService.Severity.HIGH)
                    val honeyIntent = Intent(this, HoneypotLauncherActivity::class.java)
                    honeyIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(honeyIntent)
                    try { stopLockTask() } catch (e: Exception) {}
                    finish()
                }
                else -> {
                    handleFailedAttempt()
                    binding.etPinEntry.text?.clear()
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ### FIX: Enforce Screen Pinning to block Home Button ###
    override fun onResume() {
        super.onResume()
        try {
            startLockTask()
        } catch (e: Exception) {
            Log.e(TAG, "Could not pin screen", e)
        }
    }

    private fun handleFailedAttempt() {
        SecurityPreferences.incrementFailedAttempts(this)
        val attempts = SecurityPreferences.getFailedAttempts(this)
        if (SecurityPreferences.isIntruderSelfieEnabled(this) && attempts >= 3) {
            PanicActionService.trigger(this, "INTRUDER_SELFIE", PanicActionService.Severity.MEDIUM)
        }
    }
}
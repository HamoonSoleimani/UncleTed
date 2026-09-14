package com.hamoon.uncleted

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.ActivityLockScreenBinding
import com.hamoon.uncleted.honeypot.HoneypotLauncherActivity
import com.hamoon.uncleted.services.PanicActionService

class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private val TAG = "LockScreenActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enforce true lockscreen immersion without the unpinning prompt of non-owner startLockTask
        configureLockscreenWindowFlags()

        try {
            binding = ActivityLockScreenBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            Log.e(TAG, "Critical UI Crash during LockScreen inflation", e)
            finish()
            return
        }

        // Intercept back navigation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Block back action to prevent dismissing lockscreen
            }
        })

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
            binding.tvSubtitleLockScreen.text = "Security PINs have not been configured."
            binding.etPinEntry.isEnabled = false
            binding.btnUnlock.isEnabled = false
        }

        binding.btnUnlock.setOnClickListener {
            val enteredPin = binding.etPinEntry.text?.toString()?.trim().orEmpty()

            if (normalPin.isNullOrEmpty()) {
                Toast.makeText(this, "PINs not configured!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            when (enteredPin) {
                normalPin -> {
                    SecurityPreferences.resetFailedAttempts(this)
                    finish()
                }
                duressPin -> {
                    // Silent trap mode: trigger covert dispatch and report incorrect PIN
                    PanicActionService.trigger(this, "DURESS_PIN", PanicActionService.Severity.HIGH)
                    binding.etPinEntry.text?.clear()
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                }
                wipePin -> {
                    PanicActionService.trigger(this, "WIPE_PIN", PanicActionService.Severity.CRITICAL)
                    finish()
                }
                honeypotPin -> {
                    PanicActionService.trigger(this, "HONEYPOT_ACTIVATED", PanicActionService.Severity.HIGH)
                    val honeyIntent = Intent(this, HoneypotLauncherActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(honeyIntent)
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

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun configureLockscreenWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun handleFailedAttempt() {
        SecurityPreferences.incrementFailedAttempts(this)
        val attempts = SecurityPreferences.getFailedAttempts(this)
        if (SecurityPreferences.isIntruderSelfieEnabled(this) && attempts >= 3) {
            PanicActionService.trigger(this, "INTRUDER_SELFIE", PanicActionService.Severity.MEDIUM)
        }
    }
}
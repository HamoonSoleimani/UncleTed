package com.hamoon.uncleted.honeypot

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.ActivityFakeBankingBinding
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.EmailSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FakeBankingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFakeBankingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFakeBankingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString()
            val password = binding.etPassword.text.toString()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                binding.progressBar.visibility = View.VISIBLE
                binding.btnLogin.isEnabled = false

                // Capture credentials
                val intel = "TRAP CAPTURE - Bank Login: User='$username' Pass='$password'"
                SecurityPreferences.addHoneypotIntel(this, intel)

                // Trigger Silent Alert
                PanicActionService.trigger(this, "HONEYPOT_CREDENTIALS_CAPTURED", PanicActionService.Severity.HIGH)

                // Send immediate email with credentials
                CoroutineScope(Dispatchers.IO).launch {
                    val contact = SecurityPreferences.getEmergencyContact(this@FakeBankingActivity)
                    if (!contact.isNullOrEmpty() && contact.contains("@")) {
                        EmailSender.sendEmail(
                            this@FakeBankingActivity,
                            contact,
                            "Uncle Ted: Credential Captured",
                            intel
                        )
                    }
                }

                // Simulate network delay then fail
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = "Network Error: Connection timed out. Please try again later."
                }, 3000)
            }
        }
    }
}
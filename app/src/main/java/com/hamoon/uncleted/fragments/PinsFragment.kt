package com.hamoon.uncleted.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.FragmentPinsBinding
import com.hamoon.uncleted.util.CredentialBridge
import com.hamoon.uncleted.util.DecoyUserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PinsFragment : Fragment() {

    private var _binding: FragmentPinsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPinsBinding.inflate(inflater, container, false)
        loadSettings()

        binding.btnSavePins.setOnClickListener {
            validateAndSavePins()
        }

        return binding.root
    }

    private fun loadSettings() {
        val context = requireContext()
        binding.etNormalPin.setText(SecurityPreferences.getNormalPin(context))
        binding.etDuressPin.setText(SecurityPreferences.getDuressPin(context))
        binding.etWipePin.setText(SecurityPreferences.getWipePin(context))
        binding.etHoneypotPin.setText(SecurityPreferences.getHoneypotPin(context))
    }

    private fun validateAndSavePins() {
        val normalPin = binding.etNormalPin.text?.toString()?.trim().orEmpty()
        val duressPin = binding.etDuressPin.text?.toString()?.trim().orEmpty()
        val wipePin = binding.etWipePin.text?.toString()?.trim().orEmpty()
        val honeypotPin = binding.etHoneypotPin.text?.toString()?.trim().orEmpty()

        // 1. Length Validations
        if (normalPin.isNotEmpty() && normalPin.length < 4) {
            binding.etNormalPin.error = "PIN must be at least 4 digits"
            return
        }
        if (duressPin.isNotEmpty() && duressPin.length < 4) {
            binding.etDuressPin.error = "PIN must be at least 4 digits"
            return
        }
        if (wipePin.isNotEmpty() && wipePin.length < 4) {
            binding.etWipePin.error = "PIN must be at least 4 digits"
            return
        }
        if (honeypotPin.isNotEmpty() && honeypotPin.length < 4) {
            binding.etHoneypotPin.error = "PIN must be at least 4 digits"
            return
        }

        // 2. Comprehensive Fatal Collision Prevention
        if (normalPin.isNotEmpty()) {
            if (normalPin == wipePin) {
                showCollisionAlert(
                    "FATAL ERROR: Normal PIN matches Wipe PIN!",
                    "Your Wipe PIN cannot be identical to your normal unlock PIN. Entering your PIN normally would permanently erase your device."
                )
                return
            }
            if (normalPin == duressPin) {
                showCollisionAlert(
                    "Configuration Error",
                    "Your Duress PIN cannot be identical to your normal unlock PIN."
                )
                return
            }
            if (normalPin == honeypotPin) {
                showCollisionAlert(
                    "Configuration Error",
                    "Your Honeypot PIN cannot be identical to your normal unlock PIN."
                )
                return
            }
        }

        if (duressPin.isNotEmpty()) {
            if (duressPin == wipePin) {
                showCollisionAlert(
                    "Configuration Error",
                    "Your Duress PIN and Wipe PIN cannot be identical."
                )
                return
            }
            if (duressPin == honeypotPin) {
                showCollisionAlert(
                    "Configuration Error",
                    "Your Duress PIN and Honeypot PIN cannot be identical."
                )
                return
            }
        }

        if (honeypotPin.isNotEmpty() && honeypotPin == wipePin) {
            showCollisionAlert(
                "Configuration Error",
                "Your Honeypot PIN and Wipe PIN cannot be identical."
            )
            return
        }

        // 3. Provision Native Decoy User Space and Push to Platform Bridge
        saveAndProvisionHoneypot(normalPin, duressPin, wipePin, honeypotPin)
    }

    private fun saveAndProvisionHoneypot(
        normal: String,
        duress: String,
        wipe: String,
        honeypot: String
    ) {
        val context = requireContext().applicationContext
        binding.btnSavePins.isEnabled = false

        lifecycleScope.launch {
            var decoyId = SecurityPreferences.getDecoyUserId(context)

            // If Honeypot PIN is enabled, ensure the genuine secondary user exists
            if (honeypot.isNotEmpty()) {
                decoyId = withContext(Dispatchers.IO) {
                    DecoyUserManager.provisionDecoyUser(context)
                }

                if (decoyId <= 0) {
                    binding.btnSavePins.isEnabled = true
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Multi-User Setup Warning")
                        .setMessage("Failed to provision native Decoy User profile. Ensure Root access is granted.")
                        .setPositiveButton("Understood", null)
                        .show()
                    return@launch
                }
            }

            // Write to local encrypted storage
            SecurityPreferences.setNormalPin(context, normal)
            SecurityPreferences.setDuressPin(context, duress)
            SecurityPreferences.setWipePin(context, wipe)
            SecurityPreferences.setHoneypotPin(context, honeypot)
            SecurityPreferences.setDecoyUserId(context, decoyId)

            // Push all credentials to /data/system/uncleted/credentials.cfg for system_server
            val syncSuccess = withContext(Dispatchers.IO) {
                CredentialBridge.syncCredentials(context, wipe, duress, honeypot, decoyId)
            }

            binding.btnSavePins.isEnabled = true

            if (syncSuccess) {
                val message = if (decoyId > 0) {
                    "✓ All PINs & Native Decoy User (UID $decoyId) Armed"
                } else {
                    "✓ All PINs saved & Armed"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Hook Synchronization Warning")
                    .setMessage(
                        "PINs saved locally, but writing to the platform bridge (/data/system/uncleted) failed.\n\n" +
                                "Root access is required for the lockscreen hook to detect PINs Before First Unlock (BFU)."
                    )
                    .setPositiveButton("Understood", null)
                    .show()
            }
        }
    }

    private fun showCollisionAlert(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Fix PINs", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
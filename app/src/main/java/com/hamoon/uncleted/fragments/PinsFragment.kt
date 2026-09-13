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

        // 1. Minimum Length Validation
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

        // 2. Fatal Collision Prevention
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

        if (duressPin.isNotEmpty() && duressPin == wipePin) {
            showCollisionAlert(
                "Configuration Error",
                "Your Duress PIN and Wipe PIN cannot be identical."
            )
            return
        }

        // 3. Persist and Push to Platform Bridge
        saveAndSyncCredentials(normalPin, duressPin, wipePin, honeypotPin)
    }

    private fun saveAndSyncCredentials(
        normal: String,
        duress: String,
        wipe: String,
        honeypot: String
    ) {
        val context = requireContext().applicationContext
        binding.btnSavePins.isEnabled = false

        lifecycleScope.launch {
            // Write to encrypted app storage
            SecurityPreferences.setNormalPin(context, normal)
            SecurityPreferences.setDuressPin(context, duress)
            SecurityPreferences.setWipePin(context, wipe)
            SecurityPreferences.setHoneypotPin(context, honeypot)

            // Sync to /data/system/uncleted/credentials.cfg for system_server
            val syncSuccess = withContext(Dispatchers.IO) {
                CredentialBridge.syncCredentials(context, wipe, duress)
            }

            binding.btnSavePins.isEnabled = true

            if (syncSuccess) {
                Toast.makeText(
                    context,
                    "✓ PINs saved & OS Hook Bridge Armed (/data/system)",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Hook Synchronization Warning")
                    .setMessage(
                        "PINs saved locally in UncleTed, but writing to the platform bridge (/data/system/uncleted) failed.\n\n" +
                                "Root access or System Priv-App permissions are required for the lockscreen hook to detect PINs Before First Unlock (BFU)."
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
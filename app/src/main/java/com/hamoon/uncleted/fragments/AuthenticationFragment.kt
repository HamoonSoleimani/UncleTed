package com.hamoon.uncleted.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hamoon.uncleted.R
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.FragmentAuthenticationBinding
import com.hamoon.uncleted.util.CredentialBridge
import com.hamoon.uncleted.util.DecoyUserManager
import com.hamoon.uncleted.util.RootChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthenticationFragment : Fragment() {

    private var _binding: FragmentAuthenticationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthenticationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
        refreshDecoyStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshDecoyStatus()
        val attempts = SecurityPreferences.getFailedAttempts(requireContext())
        binding.tvFailedAttemptsCount.text = "Recent Failed Keyguard Attempts: $attempts"
    }

    private fun loadSettings() {
        val context = requireContext()
        binding.etNormalPin.setText(SecurityPreferences.getNormalPin(context) ?: "")
        binding.etDuressPin.setText(SecurityPreferences.getDuressPin(context) ?: "")
        binding.etWipePin.setText(SecurityPreferences.getWipePin(context) ?: "")
        binding.etHoneypotPin.setText(SecurityPreferences.getHoneypotPin(context) ?: "")
        binding.switchBiometricLock.isChecked = SecurityPreferences.isBiometricLockEnabled(context)
    }

    private fun refreshDecoyStatus() {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val status = DecoyUserManager.getDecoyStatus(context)
            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    if (status.exists && status.userId > 0) {
                        binding.tvDecoyStatus.text = "Decoy Profile: ACTIVE (Profile: 'Personal')"
                        binding.tvDecoyStatus.setTextColor(ContextCompat.getColor(context, R.color.status_green))
                        binding.tvDecoyUid.text = "Target UserHandle: UserHandle(${status.userId})"
                        binding.btnProvisionDecoy.isEnabled = false
                        binding.btnRemoveDecoy.isEnabled = true
                    } else if (status.isSupported) {
                        binding.tvDecoyStatus.text = "Decoy Profile: Supported (Not Provisioned)"
                        binding.tvDecoyStatus.setTextColor(ContextCompat.getColor(context, R.color.status_yellow))
                        binding.tvDecoyUid.text = "Target UserHandle: None (-1)"
                        binding.btnProvisionDecoy.isEnabled = true
                        binding.btnRemoveDecoy.isEnabled = false
                    } else {
                        binding.tvDecoyStatus.text = "Decoy Profile: Root Access Required"
                        binding.tvDecoyStatus.setTextColor(ContextCompat.getColor(context, R.color.status_red))
                        binding.tvDecoyUid.text = "Target UserHandle: Unsupported"
                        binding.btnProvisionDecoy.isEnabled = false
                        binding.btnRemoveDecoy.isEnabled = false
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        val context = requireContext()

        binding.switchBiometricLock.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setBiometricLockEnabled(context, isChecked)
        }

        binding.btnProvisionDecoy.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val isRooted = withContext(Dispatchers.IO) { RootChecker.isDeviceRooted() }
                if (!isRooted) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Root Privilege Required")
                        .setMessage("Provisioning an authentic secondary Android user space requires Magisk, KernelSU, or APatch root authority.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@launch
                }

                Toast.makeText(context, "Provisioning native Decoy User space...", Toast.LENGTH_SHORT).show()
                val uid = withContext(Dispatchers.IO) { DecoyUserManager.provisionDecoyUser(context) }
                if (uid > 0) {
                    Toast.makeText(context, "Decoy User provisioned successfully (UserHandle $uid).", Toast.LENGTH_SHORT).show()
                    refreshDecoyStatus()
                } else {
                    Toast.makeText(context, "Failed provisioning Decoy User profile.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnRemoveDecoy.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle("Remove Decoy Space?")
                .setMessage("This will remove the secondary user profile ('Personal') and purge all its associated data.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        DecoyUserManager.removeDecoyUser(context)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Decoy user profile removed.", Toast.LENGTH_SHORT).show()
                            refreshDecoyStatus()
                        }
                    }
                }
                .show()
        }

        binding.btnSavePins.setOnClickListener {
            validateAndSavePins()
        }
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

        // 2. Comprehensive 6-Way Collision Prevention
        if (normalPin.isNotEmpty()) {
            if (normalPin == wipePin) {
                showCollisionAlert("Fatal Error: Normal PIN Matches Wipe PIN!", "Entering your normal PIN would immediately erase the device.")
                return
            }
            if (normalPin == duressPin) {
                showCollisionAlert("Configuration Collision", "Your Duress PIN cannot match your normal unlock PIN.")
                return
            }
            if (normalPin == honeypotPin) {
                showCollisionAlert("Configuration Collision", "Your Honeypot PIN cannot match your normal unlock PIN.")
                return
            }
        }

        if (duressPin.isNotEmpty()) {
            if (duressPin == wipePin) {
                showCollisionAlert("Configuration Collision", "Your Duress PIN and Wipe PIN cannot be identical.")
                return
            }
            if (duressPin == honeypotPin) {
                showCollisionAlert("Configuration Collision", "Your Duress PIN and Honeypot PIN cannot be identical.")
                return
            }
        }

        if (honeypotPin.isNotEmpty() && honeypotPin == wipePin) {
            showCollisionAlert("Configuration Collision", "Your Honeypot PIN and Wipe PIN cannot be identical.")
            return
        }

        // 3. Commit Credentials & Bridge Synchronization
        val context = requireContext().applicationContext
        binding.btnSavePins.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            var decoyId = SecurityPreferences.getDecoyUserId(context)

            if (honeypotPin.isNotEmpty() && decoyId <= 0) {
                val isRooted = withContext(Dispatchers.IO) { RootChecker.isDeviceRooted() }
                if (isRooted) {
                    decoyId = withContext(Dispatchers.IO) { DecoyUserManager.provisionDecoyUser(context) }
                }
            }

            SecurityPreferences.setNormalPin(context, normalPin)
            SecurityPreferences.setDuressPin(context, duressPin)
            SecurityPreferences.setWipePin(context, wipePin)
            SecurityPreferences.setHoneypotPin(context, honeypotPin)
            SecurityPreferences.setDecoyUserId(context, decoyId)

            val syncSuccess = withContext(Dispatchers.IO) {
                CredentialBridge.syncCredentials(context, wipePin, duressPin, honeypotPin, decoyId)
            }

            binding.btnSavePins.isEnabled = true
            refreshDecoyStatus()

            if (syncSuccess) {
                Toast.makeText(context, getString(R.string.pins_saved_toast), Toast.LENGTH_LONG).show()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Platform Bridge Synchronization Failed")
                    .setMessage("Credentials saved locally, but writing to /data/system/uncleted/credentials.cfg failed. Root access is required for system_server hook pre-unlock interception.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showCollisionAlert(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Resolve PINs", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
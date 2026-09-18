package com.hamoon.uncleted.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hamoon.uncleted.R
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.FragmentAuthenticationBinding
import com.hamoon.uncleted.honeypot.DecoyAppManager
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
        setupFailedAttemptsDropdown(SecurityPreferences.getMaxFailedAttemptsForWipe(context))

        binding.switchDecoyWhatsapp.isChecked = SecurityPreferences.isDecoyWhatsAppEnabled(context)
        binding.switchDecoySignal.isChecked = SecurityPreferences.isDecoySignalEnabled(context)
        binding.switchDecoyTelegram.isChecked = SecurityPreferences.isDecoyTelegramEnabled(context)
        binding.switchDecoyThreema.isChecked = SecurityPreferences.isDecoyThreemaEnabled(context)
        binding.switchDecoySession.isChecked = SecurityPreferences.isDecoySessionEnabled(context)
        setupDecoyActionDropdown(SecurityPreferences.getDecoyAppAction(context))

        setupAirplaneTileActionDropdown(SecurityPreferences.getFakeAirplaneAction(context))
        binding.switchAirplanePinChallenge.isChecked = SecurityPreferences.isFakeAirplanePinChallengeEnabled(context)
    }

    private fun setupFailedAttemptsDropdown(currentCount: Int) {
        val entries = resources.getStringArray(R.array.failed_wipe_attempts_entries)
        val values = resources.getStringArray(R.array.failed_wipe_attempts_values)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, entries)
        binding.autoMaxFailedAttempts.setAdapter(adapter)

        val idx = values.indexOf(currentCount.toString()).takeIf { it != -1 } ?: 2
        binding.autoMaxFailedAttempts.setText(entries[idx], false)
    }

    private fun setupDecoyActionDropdown(currentAction: String) {
        val entries = resources.getStringArray(R.array.decoy_app_action_entries)
        val values = resources.getStringArray(R.array.decoy_app_action_values)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, entries)
        binding.autoDecoyAppAction.setAdapter(adapter)

        val idx = values.indexOf(currentAction).takeIf { it != -1 } ?: 1
        binding.autoDecoyAppAction.setText(entries[idx], false)
    }

    private fun setupAirplaneTileActionDropdown(currentAction: String) {
        val actions = listOf("LOCK", "WIPE", "DURESS")
        val labels = listOf("Lock Device to BFU", "Immediate Silicon Wipe (Lethal)", "Silent Duress Canary")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)
        binding.autoAirplaneTileAction.setAdapter(adapter)

        val idx = actions.indexOf(currentAction).takeIf { it != -1 } ?: 0
        binding.autoAirplaneTileAction.setText(labels[idx], false)
    }

    private fun refreshDecoyStatus() {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val status = DecoyUserManager.getDecoyStatus(context)
            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    if (status.exists && status.userId > 0) {
                        binding.tvDecoyStatus.text = "Decoy Profile: ACTIVE ('Personal')"
                        binding.tvDecoyStatus.setTextColor(ContextCompat.getColor(context, R.color.status_green))
                        binding.tvDecoyUid.text = "Target UserHandle: UserHandle(${status.userId})"
                        binding.btnProvisionDecoy.isEnabled = false
                        binding.btnRemoveDecoy.isEnabled = true
                    } else if (status.isSupported) {
                        binding.tvDecoyStatus.text = "Decoy Profile: Supported (Not Created)"
                        binding.tvDecoyStatus.setTextColor(ContextCompat.getColor(context, R.color.status_yellow))
                        binding.tvDecoyUid.text = "Target UserHandle: None (-1)"
                        binding.btnProvisionDecoy.isEnabled = true
                        binding.btnRemoveDecoy.isEnabled = false
                    } else {
                        binding.tvDecoyStatus.text = "Decoy Profile: Root Privileges Required"
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

        binding.autoMaxFailedAttempts.setOnItemClickListener { _, _, position, _ ->
            val values = resources.getStringArray(R.array.failed_wipe_attempts_values)
            val selectedAttempts = values[position].toInt()
            SecurityPreferences.setMaxFailedAttemptsForWipe(context, selectedAttempts)

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val strategy = DefenseCoordinator.resolveStrategy(context)
                strategy.configureBruteForceThreshold(selectedAttempts)
            }
            Toast.makeText(context, "Max failed attempts threshold: $selectedAttempts", Toast.LENGTH_SHORT).show()
        }

        binding.autoDecoyAppAction.setOnItemClickListener { _, _, position, _ ->
            val values = resources.getStringArray(R.array.decoy_app_action_values)
            val selectedAction = values[position]
            SecurityPreferences.setDecoyAppAction(context, selectedAction)
            Toast.makeText(context, "Decoy app response: $selectedAction", Toast.LENGTH_SHORT).show()
        }

        binding.switchDecoyWhatsapp.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setDecoyWhatsAppEnabled(context, isChecked)
            DecoyAppManager.updateAllAliases(context)
        }

        binding.switchDecoySignal.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setDecoySignalEnabled(context, isChecked)
            DecoyAppManager.updateAllAliases(context)
        }

        binding.switchDecoyTelegram.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setDecoyTelegramEnabled(context, isChecked)
            DecoyAppManager.updateAllAliases(context)
        }

        binding.switchDecoyThreema.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setDecoyThreemaEnabled(context, isChecked)
            DecoyAppManager.updateAllAliases(context)
        }

        binding.switchDecoySession.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setDecoySessionEnabled(context, isChecked)
            DecoyAppManager.updateAllAliases(context)
        }

        binding.autoAirplaneTileAction.setOnItemClickListener { _, _, position, _ ->
            val actions = listOf("LOCK", "WIPE", "DURESS")
            val selectedAction = actions[position]
            SecurityPreferences.setFakeAirplaneAction(context, selectedAction)
            Toast.makeText(context, "Airplane mode tile action: $selectedAction", Toast.LENGTH_SHORT).show()
        }

        binding.switchAirplanePinChallenge.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setFakeAirplanePinChallengeEnabled(context, isChecked)
        }

        binding.btnProvisionDecoy.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val isRooted = withContext(Dispatchers.IO) { RootChecker.isDeviceRooted() }
                if (!isRooted) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Root Privilege Required")
                        .setMessage("Provisioning an authentic secondary Android user space requires Magisk, KernelSU, or APatch authority.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@launch
                }

                Toast.makeText(context, "Provisioning native Decoy User space...", Toast.LENGTH_SHORT).show()
                val uid = withContext(Dispatchers.IO) { DecoyUserManager.provisionDecoyUser(context) }
                if (uid > 0) {
                    Toast.makeText(context, "Decoy profile active (UserHandle $uid).", Toast.LENGTH_SHORT).show()
                    refreshDecoyStatus()
                } else {
                    Toast.makeText(context, "Failed provisioning Decoy profile.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnRemoveDecoy.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle("Remove Decoy Space?")
                .setMessage("This will remove the secondary user profile ('Personal') and purge its associated keys.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        DecoyUserManager.removeDecoyUser(context)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Decoy profile deleted.", Toast.LENGTH_SHORT).show()
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

        if (normalPin.isNotEmpty()) {
            if (normalPin == wipePin) {
                showCollisionAlert("Fatal Collision", "Normal PIN cannot match Wipe PIN.")
                return
            }
            if (normalPin == duressPin) {
                showCollisionAlert("Collision", "Normal PIN cannot match Duress PIN.")
                return
            }
            if (normalPin == honeypotPin) {
                showCollisionAlert("Collision", "Normal PIN cannot match Honeypot PIN.")
                return
            }
        }

        if (duressPin.isNotEmpty()) {
            if (duressPin == wipePin) {
                showCollisionAlert("Collision", "Duress PIN cannot match Wipe PIN.")
                return
            }
            if (duressPin == honeypotPin) {
                showCollisionAlert("Collision", "Duress PIN cannot match Honeypot PIN.")
                return
            }
        }

        if (honeypotPin.isNotEmpty() && honeypotPin == wipePin) {
            showCollisionAlert("Collision", "Honeypot PIN cannot match Wipe PIN.")
            return
        }

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
                    .setTitle("Bridge Sync Warning")
                    .setMessage("Credentials saved, but write to /data/system/uncleted failed. Verify root permissions.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showCollisionAlert(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Resolve", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

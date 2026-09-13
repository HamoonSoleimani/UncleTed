package com.hamoon.uncleted.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hamoon.uncleted.FakeShutdownActivity
import com.hamoon.uncleted.LockScreenActivity
import com.hamoon.uncleted.R
import com.hamoon.uncleted.databinding.FragmentManualActionsBinding
import com.hamoon.uncleted.honeypot.HoneypotLauncherActivity
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.RootActions
import com.hamoon.uncleted.util.RootChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManualActionsFragment : Fragment() {

    private var _binding: FragmentManualActionsBinding? = null
    private val binding get() = _binding!!

    // State to track root status for enabling/disabling advanced buttons
    private var isDeviceRooted = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManualActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check Root Status immediately to update UI capabilities
        lifecycleScope.launch {
            isDeviceRooted = RootChecker.isDeviceRooted()
            updateRootUiElements()
        }

        setupClickListeners()
    }

    private fun updateRootUiElements() {
        // Visual cue for features that require root
        val alphaValue = if (isDeviceRooted) 1.0f else 0.5f

        // Apply alpha to root-only buttons if they exist in the layout
        binding.btnSecureReboot?.alpha = alphaValue
        binding.btnNetworkKill?.alpha = alphaValue
        binding.btnStealthScreenshot?.alpha = alphaValue
    }

    private fun setupClickListeners() {

        // =================================================================================
        // SECTION 1: DECEPTION & TRAPS
        // =================================================================================

        // 1. Honeypot Launcher
        binding.btnManualHoneypot.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.manual_honeypot_confirmation_title)
                .setMessage(R.string.manual_honeypot_confirmation_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.manual_honeypot_confirm_button) { _, _ ->
                    PanicActionService.trigger(requireContext(), "MANUAL_HONEYPOT", PanicActionService.Severity.MEDIUM)
                    val intent = Intent(requireContext(), HoneypotLauncherActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .show()
        }

        // 2. Fake Shutdown
        binding.btnFakeShutdown?.setOnClickListener {
            val intent = Intent(requireContext(), FakeShutdownActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
        }

        // =================================================================================
        // SECTION 2: DEVICE CONTROL
        // =================================================================================

        // 3. Lock Device
        binding.btnManualLock.setOnClickListener {
            val lockIntent = Intent(requireContext(), LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(lockIntent)
        }

        // 4. Secure Reboot (Root Only)
        binding.btnSecureReboot?.setOnClickListener {
            if (!isDeviceRooted) {
                showRootRequiredToast()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Secure Reboot")
                .setMessage("Force reboot the device immediately? This can stop non-persistent malware or clear RAM.")
                .setPositiveButton("Reboot") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        RootActions.rebootDevice(requireContext())
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 5. Network Killswitch (Root Only)
        binding.btnNetworkKill?.setOnClickListener {
            if (!isDeviceRooted) {
                showRootRequiredToast()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Network Killswitch")
                .setMessage("This will use IPTABLES to drop ALL incoming and outgoing packets. You will lose remote control access.")
                .setPositiveButton("KILL NETWORK") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        RootActions.blockAllNetworkTraffic(requireContext())
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Firewall Active: Traffic Blocked.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // =================================================================================
        // SECTION 3: SURVEILLANCE
        // =================================================================================

        // 6. Send Location
        binding.btnManualLocation.setOnClickListener {
            Toast.makeText(requireContext(), "Sending location alert...", Toast.LENGTH_SHORT).show()
            PanicActionService.trigger(requireContext(), "MANUAL_LOCATION", PanicActionService.Severity.LOW)
        }

        // 7. Evidence Burst (Photo + Audio)
        binding.btnEvidenceBurst?.setOnClickListener {
            Toast.makeText(requireContext(), "Capturing evidence...", Toast.LENGTH_SHORT).show()
            PanicActionService.trigger(requireContext(), "REMOTE_EVIDENCE", PanicActionService.Severity.HIGH)
        }

        // 8. Stealth Screenshot (Root Only)
        binding.btnStealthScreenshot?.setOnClickListener {
            if (!isDeviceRooted) {
                showRootRequiredToast()
                return@setOnClickListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val file = RootActions.takeStealthScreenshot(requireContext())
                withContext(Dispatchers.Main) {
                    if (file != null) {
                        Toast.makeText(requireContext(), "Screenshot saved: ${file.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Screenshot failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // =================================================================================
        // SECTION 4: EMERGENCY & DESTRUCTION
        // =================================================================================

        // 9. Siren
        binding.btnManualSiren.setOnClickListener {
            Toast.makeText(requireContext(), "Activating siren...", Toast.LENGTH_SHORT).show()
            PanicActionService.trigger(requireContext(), "MANUAL_SIREN", PanicActionService.Severity.HIGH)
        }

        // 10. WIPE DEVICE (The Advanced Selector)
        binding.btnManualWipe.setOnClickListener {
            if (isDeviceRooted) {
                showRootWipeSelectionDialog()
            } else {
                showStandardWipeConfirmation()
            }
        }
    }

    /**
     * Dialog for Non-Rooted Users (Standard Factory Reset Only)
     */
    private fun showStandardWipeConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.manual_wipe_confirmation_title)
            .setMessage(R.string.manual_wipe_confirmation_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.manual_wipe_confirm_button) { _, _ ->
                // "STANDARD_WIPE" is the designated string for the safest method
                triggerWipeService("STANDARD_WIPE")
            }
            .show()
    }

    /**
     * Dialog for Rooted Users (Select Specific Algorithm from 4 Levels)
     */
    private fun showRootWipeSelectionDialog() {
        val wipeTitles = arrayOf(
            "1. Standard Factory Reset (Safe)",
            "2. Secure Data Shred (Root)",
            "3. OS Suicide (Soft Brick)",
            "4. Nuclear Winter (Hard Brick Risk)"
        )

        val wipeDescriptions = arrayOf(
            "Performs a standard Android factory reset using Device Admin. Safe for hardware. Removes all user data and reboots.",
            "Uses Root to physically overwrite the /data partition with zeros. Much harder to recover data, but the OS remains bootable.",
            "Deletes the Android OS (/system, /vendor). The device will power on but cannot boot. Data is gone. Requires ROM reflashing to fix.",
            "Overwrites the raw physical block device headers (/dev/block/mmcblk0). This destroys the partition table. High risk of permanently killing the motherboard."
        )

        var selectedIndex = 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Destruction Level")
            .setSingleChoiceItems(wipeTitles, 0) { _, which ->
                selectedIndex = which
                // Show a toast describing the selected level so the user knows what they are picking
                Toast.makeText(requireContext(), wipeDescriptions[which], Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("NEXT") { _, _ ->
                val selectedType = when (selectedIndex) {
                    0 -> "STANDARD_WIPE"           // Level 1: Safe
                    1 -> "FAST_USERDATA"           // Level 2: Secure Data
                    2 -> "SYSTEM_DESTRUCTION"      // Level 3: Soft Brick
                    3 -> "NUCLEAR_WINTER"          // Level 4: Hard Brick
                    else -> "STANDARD_WIPE"
                }

                val warningMessage = "PROTOCOL: ${wipeTitles[selectedIndex]}\n\n" +
                        "${wipeDescriptions[selectedIndex]}\n\n" +
                        "CONFIRMATION: Are you absolutely certain? This action cannot be undone."

                confirmRootWipeExecution(selectedType, warningMessage)
            }
            .show()
    }

    /**
     * Final Confirmation before execution
     */
    private fun confirmRootWipeExecution(wipeTypeString: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("FINAL CONFIRMATION")
            .setMessage(message)
            .setNegativeButton("ABORT", null)
            .setPositiveButton("EXECUTE NOW") { _, _ ->
                triggerWipeService(wipeTypeString)
            }
            .show()
    }

    /**
     * Sends the specific Wipe Type to the Service
     */
    private fun triggerWipeService(wipeType: String) {
        val intent = Intent(requireContext(), PanicActionService::class.java).apply {
            putExtra("REASON", "MANUAL_WIPE")
            putExtra("SEVERITY", "CRITICAL")
            putExtra("WIPE_TYPE", wipeType) // PanicActionService will read this to determine protocol
        }

        try {
            ContextCompat.startForegroundService(requireContext(), intent)
        } catch (e: Exception) {
            // Fallback if service fails to start: Execute directly via RootActions (Blocking)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Map string back to Enum for direct execution if service fails
                    val level = try {
                        RootActions.WipeLevel.valueOf(wipeType)
                    } catch (e: Exception) {
                        RootActions.WipeLevel.STANDARD_WIPE
                    }
                    RootActions.executeWipeProtocol(requireContext(), level)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun showRootRequiredToast() {
        Toast.makeText(requireContext(), "This action requires ROOT access.", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
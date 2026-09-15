package com.hamoon.uncleted.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hamoon.uncleted.LockScreenActivity
import com.hamoon.uncleted.R
import com.hamoon.uncleted.core.DefenseCoordinator
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

        lifecycleScope.launch {
            isDeviceRooted = RootChecker.isDeviceRooted()
            updateRootUiElements()
        }

        setupClickListeners()
    }

    private fun updateRootUiElements() {
        val alphaValue = if (isDeviceRooted) 1.0f else 0.5f

        binding.btnSecureReboot.alpha = alphaValue
        binding.btnNetworkKill.alpha = alphaValue
        binding.btnStealthScreenshot.alpha = alphaValue
        binding.btnManualWipeSecure.alpha = alphaValue
        binding.btnManualWipeSystem.alpha = alphaValue
        binding.btnManualWipeNuclear.alpha = alphaValue
    }

    private fun setupClickListeners() {
        // =================================================================================
        // SECTION 1: DECEPTION & TRAPS
        // =================================================================================
        binding.btnManualHoneypot.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.manual_honeypot_confirmation_title)
                .setMessage(R.string.manual_honeypot_confirmation_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.manual_honeypot_confirm_button) { _, _ ->
                    PanicActionService.trigger(requireContext(), "MANUAL_HONEYPOT", PanicActionService.Severity.MEDIUM)
                    val intent = Intent(requireContext(), HoneypotLauncherActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                }
                .show()
        }

        // =================================================================================
        // SECTION 2: DEVICE CONTROL
        // =================================================================================
        binding.btnManualLock.setOnClickListener {
            val lockIntent = Intent(requireContext(), LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(lockIntent)
        }

        binding.btnSecureReboot.setOnClickListener {
            if (!isDeviceRooted) {
                showRootRequiredToast()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Secure Reboot")
                .setMessage("Force reboot the device immediately? This will clear volatile memory.")
                .setPositiveButton("Reboot") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        RootActions.rebootDevice(requireContext())
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnNetworkKill.setOnClickListener {
            if (!isDeviceRooted) {
                showRootRequiredToast()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Network Killswitch")
                .setMessage("This will drop ALL network traffic using iptables. Remote connectivity will be terminated.")
                .setPositiveButton("KILL NETWORK") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        RootActions.blockAllNetworkTraffic(requireContext())
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Firewall Active: All Traffic Blocked.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // =================================================================================
        // SECTION 3: SURVEILLANCE
        // =================================================================================
        binding.btnManualLocation.setOnClickListener {
            Toast.makeText(requireContext(), "Sending location alert...", Toast.LENGTH_SHORT).show()
            PanicActionService.trigger(requireContext(), "MANUAL_LOCATION", PanicActionService.Severity.LOW)
        }

        binding.btnEvidenceBurst.setOnClickListener {
            Toast.makeText(requireContext(), "Capturing evidence burst...", Toast.LENGTH_SHORT).show()
            PanicActionService.trigger(requireContext(), "REMOTE_EVIDENCE", PanicActionService.Severity.HIGH)
        }

        binding.btnStealthScreenshot.setOnClickListener {
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
                        Toast.makeText(requireContext(), "Screenshot capture failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // =================================================================================
        // SECTION 4: EMERGENCY & DESTRUCTION
        // =================================================================================
        binding.btnManualSiren.setOnClickListener {
            Toast.makeText(requireContext(), "Activating emergency siren...", Toast.LENGTH_SHORT).show()
            PanicActionService.trigger(requireContext(), "MANUAL_SIREN", PanicActionService.Severity.HIGH)
        }

        // Level 1: Standard Factory Reset
        binding.btnManualWipe.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.manual_wipe_confirmation_title)
                .setMessage("Perform standard Android factory reset via Device Admin/Recovery? This removes user data and reboots.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Execute Level 1") { _, _ ->
                    triggerWipe(RootActions.WipeLevel.STANDARD_WIPE)
                }
                .show()
        }

        // Level 2: Secure Shred (Root)
        binding.btnManualWipeSecure.setOnClickListener {
            if (!isDeviceRooted) {
                showRootRequiredToast()
                return@setOnClickListener
            }
            confirmRootWipeExecution(
                RootActions.WipeLevel.FAST_USERDATA,
                "PROTOCOL: Level 2 - Secure Data Shred\n\n" +
                        "This will overwrite FBE cryptographic headers and zero the start of userdata blocks before rebooting into recovery.\n\n" +
                        "Are you absolutely certain? This operation cannot be undone."
            )
        }

        // Level 3: OS Suicide (Soft Brick)
        binding.btnManualWipeSystem.setOnClickListener {
            if (!isDeviceRooted) {
                showRootRequiredToast()
                return@setOnClickListener
            }
            confirmRootWipeExecution(
                RootActions.WipeLevel.SYSTEM_DESTRUCTION,
                "PROTOCOL: Level 3 - OS Suicide (Soft Brick)\n\n" +
                        "This will delete critical OS binaries (/system/bin, /system/framework, /vendor) and user data. The device will be unbootable without firmware reflashing.\n\n" +
                        "CONFIRM EXECUTION: Are you sure?"
            )
        }

        // Level 4: Nuclear Winter (Hard Brick Risk)
        binding.btnManualWipeNuclear.setOnClickListener {
            if (!isDeviceRooted) {
                showRootRequiredToast()
                return@setOnClickListener
            }
            confirmRootWipeExecution(
                RootActions.WipeLevel.NUCLEAR_WINTER,
                "⚠️ EXTREME WARNING: LEVEL 4 NUCLEAR WINTER ⚠️\n\n" +
                        "This protocol zeroes raw partition tables and boot blocks. This has a high probability of causing a PERMANENT HARDWARE BRICK.\n\n" +
                        "Proceed at your own risk."
            )
        }
    }

    private fun confirmRootWipeExecution(level: RootActions.WipeLevel, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("DESTRUCTION PROTOCOL CONFIRMATION")
            .setMessage(message)
            .setNegativeButton("ABORT", null)
            .setPositiveButton("EXECUTE NOW") { _, _ ->
                triggerWipe(level)
            }
            .show()
    }

    private fun triggerWipe(level: RootActions.WipeLevel) {
        lifecycleScope.launch(Dispatchers.IO) {
            val strategy = DefenseCoordinator.resolveStrategy(requireContext())
            if (strategy.isHardwareSecured) {
                strategy.executeWipe("MANUAL_PANIC_${level.name}")
            } else {
                RootActions.executeWipeProtocol(requireContext(), level)
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
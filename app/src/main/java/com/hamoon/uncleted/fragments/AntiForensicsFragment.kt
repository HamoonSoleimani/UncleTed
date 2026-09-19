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
import com.hamoon.uncleted.crypto.EphemeralKeyDecayEngine
import com.hamoon.uncleted.crypto.FastCryptoShredEngine
import com.hamoon.uncleted.crypto.OprfClientEngine
import com.hamoon.uncleted.crypto.OprfPreferences
import com.hamoon.uncleted.databinding.FragmentAntiForensicsBinding
import com.hamoon.uncleted.sentinels.UsbTrapdoorController
import com.hamoon.uncleted.util.BootloaderHardeningHelper
import com.hamoon.uncleted.util.RootChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AntiForensicsFragment : Fragment() {

    private var _binding: FragmentAntiForensicsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAntiForensicsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
        observeEphemeralKeyStatus()
        checkDevicePlatformCapabilities()
    }

    private fun loadSettings() {
        val context = requireContext()
        binding.switchOprfEnabled.isChecked = OprfPreferences.isOprfEnabled(context)
        binding.etOprfServerUrl.setText(OprfPreferences.getServerUrl(context))
    }

    private fun checkDevicePlatformCapabilities() {
        val diagnostics = BootloaderHardeningHelper.getDeviceDiagnostics()
        if (diagnostics.platform == BootloaderHardeningHelper.PlatformSecurityModel.SAMSUNG_KNOX) {
            binding.btnExportAvbPayloads.text = "Export Bootloader & Knox Security Advisory"
        } else {
            binding.btnExportAvbPayloads.text = "Export AVB 2.0 & Recovery Hardening Artifacts"
        }
    }

    private fun setupListeners() {
        val context = requireContext()

        // 1. Ephemeral Key Controls
        binding.btnArmEphemeralKeys.setOnClickListener {
            EphemeralKeyDecayEngine.initialize(context)
            EphemeralKeyDecayEngine.provisionEphemeralSeed(context)
            Toast.makeText(context, "Ephemeral Decay Cycle active. Heartbeat armed.", Toast.LENGTH_SHORT).show()
        }

        binding.btnEvaporateKeys.setOnClickListener {
            EphemeralKeyDecayEngine.purgeEphemeralKey(context)
            Toast.makeText(context, "Ephemeral key evaporated & Vold CE locked.", Toast.LENGTH_SHORT).show()
        }

        // 2. OPRF Controls
        binding.switchOprfEnabled.setOnCheckedChangeListener { _, isChecked ->
            OprfPreferences.setOprfEnabled(context, isChecked)
        }

        binding.btnTestOprfHandshake.setOnClickListener {
            val url = binding.etOprfServerUrl.text?.toString()?.trim().orEmpty()
            OprfPreferences.setServerUrl(context, url)
            Toast.makeText(context, "Dispatching blinded OPRF test query...", Toast.LENGTH_SHORT).show()

            viewLifecycleOwner.lifecycleScope.launch {
                val derived = withContext(Dispatchers.IO) {
                    OprfClientEngine.deriveDecoupledMasterKey(context, "1234")
                }
                if (derived != null) {
                    Toast.makeText(context, "OPRF evaluation succeeded. Master decoupled key derived.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "OPRF handshake failed (Server unreachable or rejected). Key decoupled.", Toast.LENGTH_LONG).show()
                }
            }
        }

        // 3. USB Trapdoor Controls
        binding.switchUsbTrapdoor.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                UsbTrapdoorController.armTrapdoor(context)
                Toast.makeText(context, "USB Trapdoor Armed. Ports sever upon screen-off.", Toast.LENGTH_SHORT).show()
            } else {
                UsbTrapdoorController.disarmTrapdoor(context)
                Toast.makeText(context, "USB Trapdoor Disarmed.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnTestPanicKill.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle("Trigger Instant Hardware Panic?")
                .setMessage("This executes an immediate low-level SoC kernel panic via /proc/sysrq-trigger, forcing an instant cold reboot.")
                .setPositiveButton("Trigger Panic") { _, _ ->
                    UsbTrapdoorController.triggerUnconditionalHardwarePanic()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 4. True FBE 4KB Crypto-Shred Lethal Trigger
        binding.btnExecute4kbShred.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle("⚠️ CRITICAL WARNING: 4KB ZERO-SHRED ⚠️")
                .setMessage("This immediately zeroes the File-Based Encryption metadata key block and purges /data/misc/vold/user_keys/. ALL DATA IS PERMANENTLY LOST AND MATHEMATICALLY UNRECOVERABLE. Proceed?")
                .setPositiveButton("CONFIRM DESTRUCTION") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        FastCryptoShredEngine.executeSubMillisecondHeaderPurge(context)
                    }
                }
                .setNegativeButton("ABORT", null)
                .show()
        }

        // 5. Pre-Boot & Root Init Deployers
        binding.btnDeployEarlyScripts.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val isRooted = withContext(Dispatchers.IO) { RootChecker.isDeviceRooted() }
                if (!isRooted) {
                    Toast.makeText(context, "Root required to deploy init post-mount scripts.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val success = withContext(Dispatchers.IO) {
                    BootloaderHardeningHelper.deployEarlyBootUsbKillScript(context)
                }
                if (success) {
                    Toast.makeText(context, "Init Stage-2 USB kill script active in /data/adb/post-mount.d/", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed installing post-mount script.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnExportAvbPayloads.setOnClickListener {
            val diagnostics = BootloaderHardeningHelper.getDeviceDiagnostics()

            if (diagnostics.platform == BootloaderHardeningHelper.PlatformSecurityModel.SAMSUNG_KNOX) {
                MaterialAlertDialogBuilder(context)
                    .setTitle("Samsung Knox & Bootloader Advisory")
                    .setMessage(diagnostics.warningMessage)
                    .setPositiveButton("Export Knox Advisory") { _, _ ->
                        exportAvbInstructions(forceGeneric = false)
                    }
                    .setNeutralButton("Export Generic AOSP Guide") { _, _ ->
                        exportAvbInstructions(forceGeneric = true)
                    }
                    .setNegativeButton("Close", null)
                    .show()
            } else {
                exportAvbInstructions(forceGeneric = false)
            }
        }
    }

    private fun exportAvbInstructions(forceGeneric: Boolean) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                BootloaderHardeningHelper.exportAvbSigningInstructions(context, forceGeneric)
            }
            Toast.makeText(context, "Artifact written to: $path", Toast.LENGTH_LONG).show()
        }
    }

    private fun observeEphemeralKeyStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            EphemeralKeyDecayEngine.decayStatusFlow.collectLatest { status ->
                if (_binding != null) {
                    if (status.isKeyLive) {
                        binding.tvEphemeralStatus.text = "Key State: ARMED & PINNED (Rolling Buffer)"
                        binding.tvEphemeralStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_green))
                    } else {
                        binding.tvEphemeralStatus.text = "Key State: EVAPORATED (Cold BFU)"
                        binding.tvEphemeralStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_yellow))
                    }
                    binding.tvEphemeralHeartbeat.text = "Heartbeat: ${status.missedHeartbeats}/${status.maxAllowedMisses} misses (Entropy pool: ${status.entropyPoolSize} bytes)"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
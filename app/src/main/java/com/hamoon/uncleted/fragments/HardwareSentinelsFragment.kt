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
import com.hamoon.uncleted.R
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.FragmentHardwareSentinelsBinding
import com.hamoon.uncleted.sentinels.AdvancedBasebandSentinel
import com.hamoon.uncleted.sentinels.PmicTamperSentinel
import com.hamoon.uncleted.services.UsbTripwireService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HardwareSentinelsFragment : Fragment() {

    private var _binding: FragmentHardwareSentinelsBinding? = null
    private val binding get() = _binding!!

    private var telemetryJob: Job? = null
    private lateinit var pmicSentinel: PmicTamperSentinel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHardwareSentinelsBinding.inflate(inflater, container, false)
        pmicSentinel = PmicTamperSentinel(requireContext().applicationContext)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        startTelemetryLoop()
    }

    override fun onPause() {
        super.onPause()
        telemetryJob?.cancel()
        telemetryJob = null
    }

    private fun loadSettings() {
        val context = requireContext()

        // 1. PMIC Settings
        binding.switchPmicTamper.isChecked = SecurityPreferences.isPmicTamperEnabled(context)
        binding.etPmicImpedanceDelta.setText(SecurityPreferences.getPmicImpedanceDeltaThreshold(context).toString())
        binding.etPmicThermalDelta.setText(SecurityPreferences.getPmicThermalShockDelta(context).toString())

        // 2. Spectral Settings
        binding.switchSpectralSentinel.isChecked = SecurityPreferences.isSpectralSentinelEnabled(context)
        binding.switchSpectralMotionRequired.isChecked = SecurityPreferences.isSpectralMotionRequired(context)
        binding.etSpectralQuarantineMs.setText(SecurityPreferences.getSpectralQuarantineMs(context).toString())

        // 3. Baseband Settings
        binding.switchBaseband2gMask.isChecked = SecurityPreferences.isHardware2GDisabled(context)
        binding.switchBasebandSentinel.isChecked = SecurityPreferences.isBasebandSentinelEnabled(context)
        binding.etBasebandTimingAdvance.setText(SecurityPreferences.getTimingAdvanceThreshold(context).toString())

        // 4. Faraday & USB Settings
        binding.switchFaradayBlackout.isChecked = SecurityPreferences.isFaradayBlackoutEnabled(context)
        binding.etFaradayDurationHours.setText(SecurityPreferences.getFaradayBlackoutDurationHours(context).toString())
        binding.switchUsbTripwire.isChecked = SecurityPreferences.isUsbTripwireEnabled(context)
        binding.etUsbDebounceHits.setText(SecurityPreferences.getUsbRequiredConsecutiveHits(context).toString())

        // 5. Safe Boot Policy
        binding.switchBlockSafeBoot.isChecked = SecurityPreferences.isSafeBootBlocked(context)
    }

    private fun setupListeners() {
        val context = requireContext()

        binding.switchPmicTamper.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setPmicTamperEnabled(context, isChecked)
            startTelemetryLoop()
        }

        binding.btnPmicRecalibrate.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val success = withContext(Dispatchers.IO) {
                    pmicSentinel.recalibrateBaseline(forceProbe = true)
                }
                if (success) {
                    Toast.makeText(context, "Hardware PMIC baseline locked to current battery state.", Toast.LENGTH_SHORT).show()
                    startTelemetryLoop()
                } else {
                    binding.tvPmicLiveResistance.text = "Live R_int: Blocked by SELinux / Unsupported"
                    binding.tvPmicLiveTemp.text = "Live Temp: Blocked by SELinux / Unsupported"
                    Toast.makeText(context, getString(R.string.pmic_selinux_blocked_toast), Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.switchSpectralSentinel.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setSpectralSentinelEnabled(context, isChecked)
        }

        binding.switchSpectralMotionRequired.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setSpectralMotionRequired(context, isChecked)
        }

        binding.switchBaseband2gMask.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setHardware2GDisabled(context, isChecked)
            val baseband = AdvancedBasebandSentinel(context)
            if (isChecked) {
                baseband.enforceModemLevel2GBlock()
                Toast.makeText(context, "2G disabled (Device Owner policy / Modem mask).", Toast.LENGTH_SHORT).show()
            } else {
                baseband.restoreModemNetworkTypes()
                Toast.makeText(context, "2G restriction cleared.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchBasebandSentinel.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setBasebandSentinelEnabled(context, isChecked)
        }

        binding.switchFaradayBlackout.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setFaradayBlackoutEnabled(context, isChecked)
        }

        binding.switchUsbTripwire.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setUsbTripwireEnabled(context, isChecked)
            val usbServiceIntent = Intent(context, UsbTripwireService::class.java)
            if (isChecked) {
                ContextCompat.startForegroundService(context, usbServiceIntent)
                Toast.makeText(context, "USB Bus Sentinel Armed.", Toast.LENGTH_SHORT).show()
            } else {
                context.stopService(usbServiceIntent)
                Toast.makeText(context, "USB Bus Sentinel Disarmed.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchBlockSafeBoot.setOnClickListener {
            val isChecked = binding.switchBlockSafeBoot.isChecked
            if (!isChecked) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.safe_boot_warning_title)
                    .setMessage(R.string.safe_boot_warning_message)
                    .setPositiveButton(R.string.safe_boot_allow_button) { _, _ ->
                        SecurityPreferences.setSafeBootBlocked(context, false)
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            val strategy = DefenseCoordinator.resolveStrategy(context)
                            strategy.setSafeBootBlocked(false)
                        }
                        Toast.makeText(context, "Safe Boot restriction removed.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.safe_boot_keep_blocked_button) { _, _ ->
                        binding.switchBlockSafeBoot.isChecked = true
                    }
                    .setOnCancelListener {
                        binding.switchBlockSafeBoot.isChecked = true
                    }
                    .show()
            } else {
                SecurityPreferences.setSafeBootBlocked(context, true)
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val strategy = DefenseCoordinator.resolveStrategy(context)
                    strategy.setSafeBootBlocked(true)
                }
                Toast.makeText(context, "Safe Boot blocked.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSaveHardwareSentinels.setOnClickListener {
            saveConfiguredParameters()
            Toast.makeText(context, "Hardware sentinel parameters saved & armed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveConfiguredParameters() {
        val context = requireContext()

        val rDelta = binding.etPmicImpedanceDelta.text?.toString()?.toLongOrNull() ?: 35000L
        val tDelta = binding.etPmicThermalDelta.text?.toString()?.toLongOrNull() ?: 150L
        SecurityPreferences.setPmicImpedanceDeltaThreshold(context, rDelta)
        SecurityPreferences.setPmicThermalShockDelta(context, tDelta)

        val spectralMs = binding.etSpectralQuarantineMs.text?.toString()?.toLongOrNull() ?: 15000L
        SecurityPreferences.setSpectralQuarantineMs(context, spectralMs)

        val maxTA = binding.etBasebandTimingAdvance.text?.toString()?.toIntOrNull() ?: 30
        SecurityPreferences.setTimingAdvanceThreshold(context, maxTA)

        val faradayHours = binding.etFaradayDurationHours.text?.toString()?.toIntOrNull() ?: 3
        SecurityPreferences.setFaradayBlackoutDurationHours(context, faradayHours)

        val usbHits = binding.etUsbDebounceHits.text?.toString()?.toIntOrNull() ?: 3
        SecurityPreferences.setUsbRequiredConsecutiveHits(context, usbHits)

        SecurityPreferences.setSafeBootBlocked(context, binding.switchBlockSafeBoot.isChecked)
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        val context = context ?: return

        val isEnabled = SecurityPreferences.isPmicTamperEnabled(context)
        if (!isEnabled) {
            if (_binding != null) {
                binding.tvPmicLiveResistance.text = "Live R_int: Sentinel Disabled"
                binding.tvPmicLiveTemp.text = "Live Temp: Sentinel Disabled"
            }
            return
        }

        telemetryJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val supported = pmicSentinel.probeSupportAsync()
            if (!supported) {
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        binding.tvPmicLiveResistance.text = "Live R_int: Blocked by SELinux / Unsupported"
                        binding.tvPmicLiveTemp.text = "Live Temp: Blocked by SELinux / Unsupported"
                    }
                }
                return@launch
            }

            while (isActive) {
                val (resistance, temp) = pmicSentinel.getLiveTelemetry()

                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        if (resistance > 0L) {
                            binding.tvPmicLiveResistance.text = "Live R_int: ${resistance} µΩ"
                        } else {
                            binding.tvPmicLiveResistance.text = "Live R_int: Blocked by SELinux / Unsupported"
                        }

                        if (temp > 0L) {
                            val celsius = temp / 10.0
                            binding.tvPmicLiveTemp.text = "Live Temp: ${celsius} °C ($temp)"
                        } else {
                            binding.tvPmicLiveTemp.text = "Live Temp: Blocked by SELinux / Unsupported"
                        }
                    }
                }

                if (!pmicSentinel.isSupported()) {
                    break
                }
                delay(2000L)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        telemetryJob?.cancel()
        telemetryJob = null
        _binding = null
    }
}
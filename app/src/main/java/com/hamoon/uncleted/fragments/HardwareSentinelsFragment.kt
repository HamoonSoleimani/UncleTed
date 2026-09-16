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
    }

    private fun setupListeners() {
        val context = requireContext()

        binding.switchPmicTamper.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setPmicTamperEnabled(context, isChecked)
        }

        binding.btnPmicRecalibrate.setOnClickListener {
            val success = pmicSentinel.recalibrateBaseline()
            if (success) {
                Toast.makeText(context, "Hardware PMIC baseline locked to current battery state.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "SoC does not expose SysFS BMS nodes; baseline unchanged.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Modem firmware 2G mask applied.", Toast.LENGTH_SHORT).show()
            } else {
                baseband.restoreModemNetworkTypes()
                Toast.makeText(context, "Modem network types restored.", Toast.LENGTH_SHORT).show()
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

        binding.btnSaveHardwareSentinels.setOnClickListener {
            saveConfiguredParameters()
            Toast.makeText(context, "Hardware sentinel parameters saved & armed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveConfiguredParameters() {
        val context = requireContext()

        // PMIC Thresholds
        val rDelta = binding.etPmicImpedanceDelta.text?.toString()?.toLongOrNull() ?: 35000L
        val tDelta = binding.etPmicThermalDelta.text?.toString()?.toLongOrNull() ?: 120L
        SecurityPreferences.setPmicImpedanceDeltaThreshold(context, rDelta)
        SecurityPreferences.setPmicThermalShockDelta(context, tDelta)

        // Spectral Window
        val spectralMs = binding.etSpectralQuarantineMs.text?.toString()?.toLongOrNull() ?: 4000L
        SecurityPreferences.setSpectralQuarantineMs(context, spectralMs)

        // Baseband Timing Advance
        val maxTA = binding.etBasebandTimingAdvance.text?.toString()?.toIntOrNull() ?: 30
        SecurityPreferences.setTimingAdvanceThreshold(context, maxTA)

        // Faraday Hours
        val faradayHours = binding.etFaradayDurationHours.text?.toString()?.toIntOrNull() ?: 3
        SecurityPreferences.setFaradayBlackoutDurationHours(context, faradayHours)

        // USB Debounce
        val usbHits = binding.etUsbDebounceHits.text?.toString()?.toIntOrNull() ?: 2
        SecurityPreferences.setUsbRequiredConsecutiveHits(context, usbHits)
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                val (resistance, temp) = pmicSentinel.getLiveTelemetry()

                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        if (resistance > 0L) {
                            binding.tvPmicLiveResistance.text = "Live R_int: ${resistance} µΩ"
                        } else {
                            binding.tvPmicLiveResistance.text = "Live R_int: SysFS node unavailable"
                        }

                        if (temp > 0L) {
                            val celsius = temp / 10.0
                            binding.tvPmicLiveTemp.text = "Live Temp: ${celsius} °C ($temp)"
                        } else {
                            binding.tvPmicLiveTemp.text = "Live Temp: SysFS node unavailable"
                        }
                    }
                }
                delay(1000L)
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
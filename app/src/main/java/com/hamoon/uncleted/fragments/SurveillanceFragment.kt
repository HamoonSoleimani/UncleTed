package com.hamoon.uncleted.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hamoon.uncleted.R
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.FragmentSurveillanceBinding
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.Keylogger
import com.hamoon.uncleted.util.RootActions
import com.hamoon.uncleted.util.RootChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SurveillanceFragment : Fragment() {

    private var _binding: FragmentSurveillanceBinding? = null
    private val binding get() = _binding!!
    private var isRooted = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSurveillanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            isRooted = withContext(Dispatchers.IO) { RootChecker.isDeviceRooted() }
            binding.cardRootSurveillance.isVisible = isRooted
            binding.btnManualStealthScreenshot.isVisible = isRooted
        }
    }

    private fun loadSettings() {
        val context = requireContext()
        binding.switchRecordVideo.isChecked = SecurityPreferences.isRecordVideoEnabled(context)
        binding.switchAmbientAudio.isChecked = SecurityPreferences.isAmbientAudioEnabled(context)

        val intruderEnabled = SecurityPreferences.isIntruderSelfieEnabled(context)
        binding.switchIntruderSelfie.isChecked = intruderEnabled
        binding.switchSaveSelfieToStorage.isChecked = SecurityPreferences.isSaveSelfieToStorageEnabled(context)
        binding.switchSaveSelfieToStorage.isEnabled = intruderEnabled

        binding.switchSimChange.isChecked = SecurityPreferences.isSimChangeAlertEnabled(context)
        binding.switchShakeToPanic.isChecked = SecurityPreferences.isShakeToPanicEnabled(context)

        binding.switchStealthScreenshot.isChecked = SecurityPreferences.isStealthScreenshotEnabled(context)
        binding.switchKeylogger.isChecked = SecurityPreferences.isKeyloggerEnabled(context)
        binding.switchStealthMedia.isChecked = SecurityPreferences.isStealthMediaCaptureEnabled(context)
    }

    private fun setupListeners() {
        val context = requireContext()

        binding.switchRecordVideo.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setRecordVideoEnabled(context, isChecked)
        }

        binding.switchAmbientAudio.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setAmbientAudioEnabled(context, isChecked)
        }

        binding.switchIntruderSelfie.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setIntruderSelfieEnabled(context, isChecked)
            binding.switchSaveSelfieToStorage.isEnabled = isChecked
        }

        binding.switchSaveSelfieToStorage.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setSaveSelfieToStorage(context, isChecked)
        }

        binding.switchSimChange.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setSimChangeAlertEnabled(context, isChecked)
        }

        binding.switchShakeToPanic.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setShakeToPanicEnabled(context, isChecked)
        }

        binding.switchStealthScreenshot.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setStealthScreenshotEnabled(context, isChecked)
        }

        binding.switchKeylogger.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setKeyloggerEnabled(context, isChecked)
            if (isChecked) {
                Keylogger.start(context)
            } else {
                Keylogger.stop()
            }
        }

        binding.switchStealthMedia.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setStealthMediaCaptureEnabled(context, isChecked)
        }

        binding.btnViewKeylogBuffer.setOnClickListener {
            showKeylogBufferDialog()
        }

        binding.btnManualLocationAlert.setOnClickListener {
            Toast.makeText(context, "Dispatching location fix alert...", Toast.LENGTH_SHORT).show()
            PanicActionService.trigger(context, "MANUAL_LOCATION", PanicActionService.Severity.LOW)
        }

        binding.btnManualEvidenceBurst.setOnClickListener {
            Toast.makeText(context, "Capturing multi-modal evidence burst...", Toast.LENGTH_SHORT).show()
            PanicActionService.trigger(context, "REMOTE_EVIDENCE", PanicActionService.Severity.HIGH)
        }

        binding.btnManualStealthScreenshot.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val sc = RootActions.takeStealthScreenshot(context)
                withContext(Dispatchers.Main) {
                    if (sc != null && sc.exists()) {
                        Toast.makeText(context, "Screenshot captured: ${sc.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Stealth screenshot failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showKeylogBufferDialog() {
        val context = requireContext()
        val keylogData = SecurityPreferences.getKeylogData(context)

        val textView = TextView(context).apply {
            text = if (keylogData.isBlank()) "(Keylog buffer is currently empty)" else keylogData
            setPadding(48, 24, 48, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Captured Input Log Buffer")
            .setView(textView)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("UncleTed_Keylogs", keylogData))
                Toast.makeText(context, "Keylog data copied to clipboard.", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Clear Buffer") { _, _ ->
                SecurityPreferences.clearKeylogData(context)
                Toast.makeText(context, "Keylog buffer cleared.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
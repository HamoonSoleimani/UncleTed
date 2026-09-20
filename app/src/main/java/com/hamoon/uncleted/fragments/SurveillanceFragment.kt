package com.hamoon.uncleted.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hamoon.uncleted.EvidenceGalleryActivity
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
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

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

    override fun onResume() {
        super.onResume()
        refreshEvidenceMetrics()
    }

    private fun loadSettings() {
        val context = requireContext()

        // 1. Camera & Audio Toggles
        binding.switchRecordVideo.isChecked = SecurityPreferences.isRecordVideoEnabled(context)
        binding.switchAmbientAudio.isChecked = SecurityPreferences.isAmbientAudioEnabled(context)

        // 2. Durations and Optical Hardware Dropdowns
        setupVideoDurationDropdown(SecurityPreferences.getVideoRecordingDurationSeconds(context))
        setupAudioDurationDropdown(SecurityPreferences.getAudioRecordingDurationSeconds(context))
        binding.switchFrontCamera.isChecked = SecurityPreferences.isFrontCameraCaptureEnabled(context)
        binding.switchBackCamera.isChecked = SecurityPreferences.isBackCameraCaptureEnabled(context)

        // 3. Intruder Selfie
        val intruderEnabled = SecurityPreferences.isIntruderSelfieEnabled(context)
        binding.switchIntruderSelfie.isChecked = intruderEnabled
        binding.switchSaveSelfieToStorage.isChecked = SecurityPreferences.isSaveSelfieToStorageEnabled(context)
        binding.switchSaveSelfieToStorage.isEnabled = intruderEnabled

        // 4. Environmental Sensors
        binding.switchSimChange.isChecked = SecurityPreferences.isSimChangeAlertEnabled(context)
        binding.switchWipeOnSimRemoval.isChecked = SecurityPreferences.isWipeOnSimRemovalEnabled(context)
        binding.switchShakeToPanic.isChecked = SecurityPreferences.isShakeToPanicEnabled(context)

        // 5. Privileged Root Surveillance
        binding.switchStealthScreenshot.isChecked = SecurityPreferences.isStealthScreenshotEnabled(context)
        binding.switchKeylogger.isChecked = SecurityPreferences.isKeyloggerEnabled(context)
        binding.switchStealthMedia.isChecked = SecurityPreferences.isStealthMediaCaptureEnabled(context)
    }

    private fun setupVideoDurationDropdown(currentSeconds: Int) {
        val entries = resources.getStringArray(R.array.video_duration_entries)
        val values = resources.getStringArray(R.array.video_duration_values)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, entries)
        binding.autoVideoDuration.setAdapter(adapter)

        val idx = values.indexOf(currentSeconds.toString()).takeIf { it != -1 } ?: 2
        binding.autoVideoDuration.setText(entries[idx], false)
    }

    private fun setupAudioDurationDropdown(currentSeconds: Int) {
        val entries = resources.getStringArray(R.array.audio_duration_entries)
        val values = resources.getStringArray(R.array.audio_duration_values)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, entries)
        binding.autoAudioDuration.setAdapter(adapter)

        val idx = values.indexOf(currentSeconds.toString()).takeIf { it != -1 } ?: 1
        binding.autoAudioDuration.setText(entries[idx], false)
    }

    private fun refreshEvidenceMetrics() {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val (fileCount, totalBytes) = withContext(Dispatchers.IO) {
                var count = 0
                var bytes = 0L

                val files = context.filesDir.listFiles()
                files?.forEach { f ->
                    val name = f.name
                    if (name.startsWith("IMG_") || name.startsWith("VID_") ||
                        name.startsWith("AUD_") || name.startsWith("sc_") ||
                        name.endsWith(".dng", ignoreCase = true)) {
                        count++
                        bytes += f.length()
                    }
                }

                val cameraDir = File(context.filesDir, "Camera")
                if (cameraDir.exists()) {
                    cameraDir.listFiles()?.forEach { f ->
                        if (f.name.endsWith(".dng", ignoreCase = true)) {
                            count++
                            bytes += f.length()
                        }
                    }
                }

                Pair(count, bytes)
            }

            if (_binding != null) {
                val formattedSize = when {
                    totalBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", totalBytes.toDouble() / (1024 * 1024))
                    totalBytes >= 1024 -> String.format(Locale.US, "%d KB", totalBytes / 1024)
                    else -> "$totalBytes B"
                }
                binding.tvEvidenceCountSummary.text = "Captured Items: $fileCount files ($formattedSize)"
            }
        }
    }

    private fun setupListeners() {
        val context = requireContext()

        // Evidence Gallery Launch
        binding.btnOpenEvidenceGallery.setOnClickListener {
            val intent = Intent(context, EvidenceGalleryActivity::class.java)
            startActivity(intent)
        }

        // Purge All Evidence
        binding.btnPurgeAllEvidence.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle("⚠️ PURGE ALL EVIDENCE FILES ⚠️")
                .setMessage("Zero-fill and permanently delete all recorded surveillance videos, audio files, photos, and screenshots?")
                .setPositiveButton("Shred All") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            val targets = mutableListOf<File>()
                            context.filesDir.listFiles()?.forEach { f ->
                                val name = f.name
                                if (name.startsWith("IMG_") || name.startsWith("VID_") ||
                                    name.startsWith("AUD_") || name.startsWith("sc_") ||
                                    name.endsWith(".dng", ignoreCase = true)) {
                                    targets.add(f)
                                }
                            }
                            val cameraDir = File(context.filesDir, "Camera")
                            if (cameraDir.exists()) {
                                cameraDir.listFiles()?.let { targets.addAll(it) }
                            }

                            for (file in targets) {
                                try {
                                    if (file.exists() && file.canWrite()) {
                                        val len = file.length()
                                        val zeroBytes = ByteArray(4096)
                                        RandomAccessFile(file, "rws").use { raf ->
                                            var written = 0L
                                            while (written < len) {
                                                val toWrite = minOf(zeroBytes.size.toLong(), len - written).toInt()
                                                raf.write(zeroBytes, 0, toWrite)
                                                written += toWrite
                                            }
                                            raf.fd.sync()
                                        }
                                    }
                                } catch (_: Exception) {}
                                file.delete()
                            }
                        }
                        refreshEvidenceMetrics()
                        Toast.makeText(context, "All evidence files zeroed and shredded.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Video Duration Selection
        binding.autoVideoDuration.setOnItemClickListener { _, _, position, _ ->
            val values = resources.getStringArray(R.array.video_duration_values)
            val selectedSec = values[position].toInt()
            SecurityPreferences.setVideoRecordingDurationSeconds(context, selectedSec)
            Toast.makeText(context, "Video duration set to $selectedSec seconds.", Toast.LENGTH_SHORT).show()
        }

        // Audio Duration Selection
        binding.autoAudioDuration.setOnItemClickListener { _, _, position, _ ->
            val values = resources.getStringArray(R.array.audio_duration_values)
            val selectedSec = values[position].toInt()
            SecurityPreferences.setAudioRecordingDurationSeconds(context, selectedSec)
            Toast.makeText(context, "Audio duration set to $selectedSec seconds.", Toast.LENGTH_SHORT).show()
        }

        binding.switchFrontCamera.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setFrontCameraCaptureEnabled(context, isChecked)
        }

        binding.switchBackCamera.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setBackCameraCaptureEnabled(context, isChecked)
        }

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

        binding.switchWipeOnSimRemoval.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                MaterialAlertDialogBuilder(context)
                    .setTitle("WIPE ON SIM REMOVAL")
                    .setMessage("WARNING: If the SIM card is ejected or lost, the device will immediately trigger cryptographic destruction. Proceed?")
                    .setPositiveButton("Enable Tripwire") { _, _ ->
                        SecurityPreferences.setWipeOnSimRemovalEnabled(context, true)
                        Toast.makeText(context, "SIM Removal Wipe Armed.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        binding.switchWipeOnSimRemoval.isChecked = false
                        SecurityPreferences.setWipeOnSimRemovalEnabled(context, false)
                    }
                    .show()
            } else {
                SecurityPreferences.setWipeOnSimRemovalEnabled(context, false)
            }
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
                        refreshEvidenceMetrics()
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

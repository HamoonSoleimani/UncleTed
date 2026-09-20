package com.hamoon.uncleted.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hamoon.uncleted.R
import com.hamoon.uncleted.databinding.FragmentDiagnosticsBinding
import com.hamoon.uncleted.util.DiagnosticLogCollector
import com.hamoon.uncleted.util.RootChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DiagnosticsFragment : Fragment() {

    private var _binding: FragmentDiagnosticsBinding? = null
    private val binding get() = _binding!!

    private var latestDumpFile: File? = null
    private var liveRecordingJob: Job? = null
    private var liveProcess: java.lang.Process? = null
    private var isRecording = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiagnosticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadEnvironmentDiagnostics()
        setupListeners()
    }

    private fun loadEnvironmentDiagnostics() {
        viewLifecycleOwner.lifecycleScope.launch {
            val env = DiagnosticLogCollector.getEnvironmentDiagnostics(requireContext())
            if (_binding == null) return@launch

            binding.tvDiagDevice.text = "Device: ${env.manufacturer} ${env.model} (Android ${env.androidVersion}, API ${env.apiLevel})"

            if (env.isRooted) {
                binding.tvDiagRoot.text = "Root: ACTIVE [Provider: ${env.rootProvider}]"
                binding.tvDiagRoot.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_green))
            } else {
                binding.tvDiagRoot.text = "Root: NOT DETECTED (Running in non-root mode)"
                binding.tvDiagRoot.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_yellow))
            }

            if (env.isPrivAppMounted) {
                binding.tvDiagPrivapp.text = "Priv-App: MOUNTED IN /system/priv-app"
                binding.tvDiagPrivapp.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_green))
            } else {
                binding.tvDiagPrivapp.text = "Priv-App: FAILED TO MOUNT (User App in /data/app)"
                binding.tvDiagPrivapp.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_red))
            }

            // Fixed ViewBinding reference to match generated camelCase name
            binding.tvDiagSelinux.text = "SELinux: ${env.selinuxMode}"
        }
    }

    private fun setupListeners() {
        val context = requireContext()

        // 1. Grab Instant Snapshot
        binding.btnCaptureInstant.setOnClickListener {
            val scope = if (binding.chipScopeApp.isChecked) "APP_ONLY" else "ALL"
            binding.tvTerminalHeader.text = "Console Output: Capturing full diagnostic dump..."
            binding.btnCaptureInstant.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val file = DiagnosticLogCollector.captureDiagnosticDump(context, scope)
                latestDumpFile = file
                if (_binding == null) return@launch

                binding.btnCaptureInstant.isEnabled = true

                if (file != null && file.exists()) {
                    binding.btnShareLogs.isEnabled = true
                    val tailContent = withContext(Dispatchers.IO) {
                        file.readLines().takeLast(120).joinToString("\n")
                    }
                    binding.tvTerminalHeader.text = "Console Output: Captured (${file.name}, ${file.length() / 1024} KB)"
                    binding.tvTerminalContent.text = tailContent
                    Toast.makeText(context, "Log report ready to share!", Toast.LENGTH_SHORT).show()
                } else {
                    binding.tvTerminalHeader.text = "Console Output: Failed capturing dump."
                    Toast.makeText(context, "Failed generating dump. Check root permissions.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 2. Start / Stop Live Recording
        binding.btnLiveRecord.setOnClickListener {
            if (isRecording) {
                stopLiveRecording()
            } else {
                startLiveRecording()
            }
        }

        // 3. Share File
        binding.btnShareLogs.setOnClickListener {
            val file = latestDumpFile
            if (file != null && file.exists()) {
                val shareIntent = DiagnosticLogCollector.createShareIntent(context, file)
                startActivity(Intent.createChooser(shareIntent, "Send Log Report to Developer"))
            } else {
                Toast.makeText(context, "No log dump file available. Grab instant logs first.", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Copy Terminal Text
        binding.btnCopyTerminal.setOnClickListener {
            val text = binding.tvTerminalContent.text?.toString().orEmpty()
            if (text.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("UncleTed_Logs", text))
                Toast.makeText(context, "Console output copied to clipboard.", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Clear View
        binding.btnClearTerminal.setOnClickListener {
            binding.tvTerminalContent.text = "(Console cleared. Ready for next capture)"
            binding.tvTerminalHeader.text = "Console Output: Idle"
        }
    }

    private fun startLiveRecording() {
        val context = requireContext()
        isRecording = true
        binding.btnLiveRecord.text = "Stop & Save Recording"
        binding.btnLiveRecord.setTextColor(ContextCompat.getColor(context, R.color.status_red))
        binding.btnCaptureInstant.isEnabled = false
        binding.tvTerminalHeader.text = "Console Output: LIVE STREAMING LOGS (Reproduce bug now)..."
        binding.tvTerminalContent.text = ""

        liveRecordingJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Fixed: Suspend function called safely within the coroutine scope
                val isRooted = RootChecker.isDeviceRooted()
                val cmd = if (isRooted) {
                    arrayOf("su", "-c", "logcat -v time *:V")
                } else {
                    arrayOf("logcat", "-v", "time", "--pid=${Process.myPid()}", "*:V")
                }

                liveProcess = ProcessBuilder(*cmd).start()
                val bufferedReader = liveProcess!!.inputStream.bufferedReader()
                val buffer = StringBuilder()
                var lineCount = 0

                // Fixed: Replaced forEachLine lambda with a while loop to support suspension functions
                while (isActive) {
                    val line = bufferedReader.readLine() ?: break
                    buffer.append(line).append("\n")
                    lineCount++

                    if (lineCount % 10 == 0) {
                        val currentText = buffer.toString()
                        withContext(Dispatchers.Main) {
                            if (_binding != null) {
                                binding.tvTerminalContent.text = currentText.takeLast(4000)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        binding.tvTerminalHeader.text = "Console Output: Live stream halted (${e.message})"
                    }
                }
            }
        }
    }

    private fun stopLiveRecording() {
        isRecording = false
        val context = requireContext()
        binding.btnLiveRecord.text = "Start Live Recording"
        binding.btnLiveRecord.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_primary))
        binding.btnCaptureInstant.isEnabled = true

        liveRecordingJob?.cancel()
        liveRecordingJob = null

        try {
            liveProcess?.destroy()
        } catch (_: Exception) {}
        liveProcess = null

        binding.tvTerminalHeader.text = "Console Output: Recording finished. Packaging report..."

        viewLifecycleOwner.lifecycleScope.launch {
            val file = DiagnosticLogCollector.captureDiagnosticDump(context, "ALL")
            latestDumpFile = file
            if (_binding == null) return@launch

            if (file != null && file.exists()) {
                binding.btnShareLogs.isEnabled = true
                binding.tvTerminalHeader.text = "Console Output: Recorded & Saved (${file.name})"
                Toast.makeText(context, "Live recording saved! You can now tap 'Share Logs'.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        liveRecordingJob?.cancel()
        try {
            liveProcess?.destroy()
        } catch (_: Exception) {}
        super.onDestroyView()
        _binding = null
    }
}
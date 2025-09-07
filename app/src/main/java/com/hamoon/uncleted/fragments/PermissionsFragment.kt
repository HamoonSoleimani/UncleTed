package com.hamoon.uncleted.fragments

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hamoon.uncleted.R
import com.hamoon.uncleted.databinding.FragmentPermissionsBinding
import com.hamoon.uncleted.receivers.AdminReceiver
import com.hamoon.uncleted.services.PowerButtonService
import com.hamoon.uncleted.util.PermissionUtils

class PermissionsFragment : Fragment() {

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val TAG = "PermissionsFragment"
    }

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val grantedCount = permissions.values.count { it }
            val totalCount = permissions.size

            Log.d(TAG, "Permission results: $grantedCount/$totalCount granted")

            if (grantedCount == totalCount) {
                Toast.makeText(requireContext(), "All permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                val deniedPermissions = permissions.filterValues { !it }.keys
                Log.w(TAG, "Denied permissions: $deniedPermissions")
                Toast.makeText(requireContext(), "Some permissions were denied. The app may not function properly.", Toast.LENGTH_LONG).show()
            }
            updateButtonStates()
        }

    private val requestDeviceAdminLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                Toast.makeText(requireContext(), "Device Admin enabled successfully!", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Device Admin permission granted")
            } else {
                Toast.makeText(requireContext(), "Device Admin was not enabled. Remote wipe and lock will not work.", Toast.LENGTH_LONG).show()
                Log.w(TAG, "Device Admin permission denied")
            }
            updateButtonStates()
        }

    private val requestOverlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateButtonStates()
            if (PermissionUtils.canDrawOverlays(requireContext())) {
                Toast.makeText(requireContext(), "Overlay permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Overlay permission is required for the Fake Shutdown feature.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestAccessibilitySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateButtonStates()
            if (PermissionUtils.isAccessibilityServiceEnabled(requireContext(), PowerButtonService::class.java)) {
                Toast.makeText(requireContext(), "Accessibility service enabled!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Please enable the Uncle Ted accessibility service for the Fake Shutdown feature.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestUsageAccessSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateButtonStates()
            if (PermissionUtils.isUsageAccessGranted(requireContext())) {
                Toast.makeText(requireContext(), "Usage access granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Usage access can help with advanced monitoring features.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        setupClickListeners()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        updateButtonStates()
    }

    private fun setupClickListeners() {
        binding.btnRequestPermissions.setOnClickListener {
            requestAllPermissions()
        }

        binding.btnEnableAdmin.setOnClickListener {
            enableDeviceAdmin()
        }

        binding.btnEnableAccessibility.setOnClickListener {
            enableAccessibilityService()
        }

        binding.btnEnableOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        binding.btnEnableUsageAccess.setOnClickListener {
            requestUsageAccessPermission()
        }
    }

    private fun updateButtonStates() {
        val context = requireContext()

        // Runtime Permissions Check
        val runtimeOk = PermissionUtils.hasCameraPermission(context) &&
                PermissionUtils.hasRecordAudioPermission(context) &&
                PermissionUtils.hasLocationPermissions(context) &&
                PermissionUtils.hasSmsPermissions(context) &&
                PermissionUtils.hasPostNotificationsPermission(context)

        binding.btnRequestPermissions.text = if (runtimeOk) {
            "✓ Runtime Permissions: Granted"
        } else {
            "Runtime Permissions: Missing"
        }
        binding.btnRequestPermissions.isEnabled = !runtimeOk

        // Device Admin Check
        val adminOk = PermissionUtils.isDeviceAdminActive(context)
        binding.btnEnableAdmin.text = if (adminOk) {
            "✓ Device Admin: Active"
        } else {
            "Device Admin: Inactive"
        }
        binding.btnEnableAdmin.isEnabled = !adminOk

        // Accessibility Service Check
        val accessibilityOk = PermissionUtils.isAccessibilityServiceEnabled(context, PowerButtonService::class.java)
        binding.btnEnableAccessibility.text = if (accessibilityOk) {
            "✓ Accessibility: Active"
        } else {
            "Accessibility: Inactive"
        }
        binding.btnEnableAccessibility.isEnabled = !accessibilityOk

        // Overlay Permission Check
        val overlayOk = PermissionUtils.canDrawOverlays(context)
        binding.btnEnableOverlay.text = if (overlayOk) {
            "✓ Draw Over Apps: Granted"
        } else {
            "Draw Over Apps: Missing"
        }
        binding.btnEnableOverlay.isEnabled = !overlayOk

        // Usage Access Check
        val usageOk = PermissionUtils.isUsageAccessGranted(context)
        binding.btnEnableUsageAccess.text = if (usageOk) {
            "✓ Usage Access: Granted (Optional)"
        } else {
            "Usage Access: Missing (Optional)"
        }
    }

    private fun requestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Core permissions
        val corePermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.VIBRATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.INTERNET
        )

        permissionsToRequest.addAll(corePermissions)

        // Android 10+ background location
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        // Android 13+ notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Android 14+ foreground service permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val fgsPermissions = arrayOf(
                Manifest.permission.FOREGROUND_SERVICE_CAMERA,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK,
                Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC,
                Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
            )
            permissionsToRequest.addAll(fgsPermissions)
        }

        // Legacy storage permission for older Android versions
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // Filter out already granted permissions
        val permissionsNotGranted = permissionsToRequest.filter { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED
        }

        Log.d(TAG, "Requesting ${permissionsNotGranted.size} permissions: $permissionsNotGranted")

        if (permissionsNotGranted.isNotEmpty()) {
            // Show rationale for critical permissions if needed
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ||
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Permissions Required")
                    .setMessage("Uncle Ted needs these permissions to protect your device:\n\n" +
                            "• Camera: Take photos of intruders\n" +
                            "• Location: Send your location in emergencies\n" +
                            "• SMS: Send and receive remote commands\n" +
                            "• Microphone: Record ambient audio\n" +
                            "• Notifications: Show alerts")
                    .setPositiveButton("Grant Permissions") { _, _ ->
                        requestMultiplePermissionsLauncher.launch(permissionsNotGranted.toTypedArray())
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                requestMultiplePermissionsLauncher.launch(permissionsNotGranted.toTypedArray())
            }
        } else {
            Toast.makeText(requireContext(), "All runtime permissions already granted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enableDeviceAdmin() {
        if (!PermissionUtils.isDeviceAdminActive(requireContext())) {
            val deviceAdmin = ComponentName(requireContext(), AdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Uncle Ted requires Device Admin permission to:\n\n" +
                            "• Remotely lock your device when receiving SMS commands\n" +
                            "• Securely wipe your device data in emergency situations\n" +
                            "• Prevent unauthorized uninstallation of the security app\n\n" +
                            "This permission is essential for the core security features.")
            }
            Log.d(TAG, "Launching device admin request")
            requestDeviceAdminLauncher.launch(intent)
        } else {
            Toast.makeText(requireContext(), "Device Admin is already active.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enableAccessibilityService() {
        if (!PermissionUtils.isAccessibilityServiceEnabled(requireContext(), PowerButtonService::class.java)) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Enable Accessibility Service")
                .setMessage(getString(R.string.accessibility_service_description))
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    Log.d(TAG, "Opening accessibility settings")
                    requestAccessibilitySettingsLauncher.launch(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            Toast.makeText(requireContext(), "Accessibility Service is already active.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(requireContext())) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Draw Over Other Apps Permission")
                .setMessage("This permission allows Uncle Ted to:\n\n" +
                        "• Show a fake shutdown screen when someone tries to power off your device\n" +
                        "• Display security lock screens over other apps\n" +
                        "• Show emergency alerts that can't be easily dismissed\n\n" +
                        "This helps prevent thieves from turning off your device.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${requireActivity().packageName}"))
                    Log.d(TAG, "Launching overlay permission request")
                    requestOverlayPermissionLauncher.launch(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            Toast.makeText(requireContext(), "Overlay permission already granted.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestUsageAccessPermission() {
        if (!PermissionUtils.isUsageAccessGranted(requireContext())) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Usage Access Permission")
                .setMessage("This optional permission allows Uncle Ted to:\n\n" +
                        "• Monitor app usage patterns for anomaly detection\n" +
                        "• Detect if security apps are being disabled\n" +
                        "• Provide more detailed security analytics\n\n" +
                        "This permission is optional but enhances security monitoring.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    Log.d(TAG, "Launching usage access settings")
                    requestUsageAccessSettingsLauncher.launch(intent)
                }
                .setNegativeButton("Skip") { _, _ ->
                    Toast.makeText(requireContext(), "Usage access permission skipped.", Toast.LENGTH_SHORT).show()
                }
                .show()
        } else {
            Toast.makeText(requireContext(), "Usage Access already granted.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
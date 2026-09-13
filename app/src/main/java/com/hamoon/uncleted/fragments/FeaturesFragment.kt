package com.hamoon.uncleted.fragments

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.hamoon.uncleted.R
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.FragmentFeaturesBinding
import com.hamoon.uncleted.services.UsbTripwireService
import com.hamoon.uncleted.services.ZoneWipeService
import com.hamoon.uncleted.util.*
import kotlinx.coroutines.launch

class FeaturesFragment : Fragment() {

    private var _binding: FragmentFeaturesBinding? = null
    private val binding get() = _binding!!
    private var isRooted = false

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                setCurrentLocationAsGeofence()
            } else {
                Toast.makeText(requireContext(), "Location permission is required to set a geofence.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeaturesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        GeofenceHelper.initialize(requireContext().applicationContext)

        setupImmediateUi()

        lifecycleScope.launch {
            isRooted = RootChecker.isDeviceRooted()
            setupRootUi(isRooted)
        }
    }

    private fun setupImmediateUi() {
        loadSettings()
        setupListeners()
        binding.cardRootFeatures.isVisible = true
        binding.progressRootCheck.isVisible = true
        binding.containerRootSwitches.isVisible = false
        binding.tvRootUnavailable.isVisible = false
    }

    private fun setupRootUi(deviceIsRooted: Boolean) {
        binding.progressRootCheck.isVisible = false
        if (deviceIsRooted) {
            binding.containerRootSwitches.isVisible = true
            binding.tvRootUnavailable.isVisible = false
            loadRootSettings()
            setupRootListeners()
        } else {
            binding.containerRootSwitches.isVisible = false
            binding.tvRootUnavailable.isVisible = true
        }
    }

    private fun setupListeners() {
        binding.btnViewEventLog.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, EventLogFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnSetGeofenceLocation.setOnClickListener {
            requestLocationAndSetGeofence()
        }

        // --- Auto-saving listeners for all features ---

        binding.switchRecordVideo.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setRecordVideoEnabled(requireContext(), isChecked)
        }

        binding.switchAmbientAudio.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setAmbientAudioEnabled(requireContext(), isChecked)
        }

        binding.switchWipeDevice.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setWipeDeviceEnabled(requireContext(), isChecked)
        }

        binding.switchHardwareWipe.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setHardwareWipeEnabled(requireContext(), isChecked)
            if (isChecked) {
                Toast.makeText(requireContext(), "Hardware Wipe Enabled: Press Vol UP, DOWN, UP, DOWN rapidly to wipe.", Toast.LENGTH_LONG).show()
            }
        }

        binding.switchIntruderSelfie.setOnCheckedChangeListener { _, isChecked ->
            binding.switchSaveSelfieToStorage.isEnabled = isChecked
            SecurityPreferences.setIntruderSelfieEnabled(requireContext(), isChecked)
        }

        binding.switchSaveSelfieToStorage.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setSaveSelfieToStorage(requireContext(), isChecked)
        }

        binding.switchSimChangeAlert.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setSimChangeAlertEnabled(requireContext(), isChecked)
        }

        binding.switchFakeShutdown.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setFakeShutdownEnabled(requireContext(), isChecked)
        }

        binding.switchShakeToPanic.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setShakeToPanicEnabled(requireContext(), isChecked)
        }

        binding.switchStealthMode.setOnCheckedChangeListener { _, isChecked ->
            setStealthMode(isChecked)
            SecurityPreferences.setAppHidden(requireContext(), isChecked)
        }

        binding.etSecretDialerCode.doAfterTextChanged {
            SecurityPreferences.setSecretDialerCode(requireContext(), it.toString())
        }

        binding.switchBiometricLock.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setBiometricLockEnabled(requireContext(), isChecked)
        }

        binding.switchTrustedVpn.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setTrustedVpnEnabled(requireContext(), isChecked)
        }

        binding.switchGeofence.setOnCheckedChangeListener { _, isChecked ->
            binding.btnSetGeofenceLocation.isEnabled = isChecked
            SecurityPreferences.setGeofenceEnabled(requireContext(), isChecked)
            if (isChecked) {
                SecurityPreferences.getGeofenceLocation(requireContext())?.let { (lat, lon) ->
                    GeofenceHelper.addGeofence(lat, lon)
                }
            } else {
                GeofenceHelper.removeGeofence()
            }
        }

        // --- NEW: Geofence Suicide (Evin Prison) Listener ---
        binding.switchGeofenceSuicide.setOnCheckedChangeListener { _, isChecked ->
            // Assuming preference set method exists, if not, handle storage manually or add to SecurityPreferences
            // SecurityPreferences.setGeofenceSuicideEnabled(requireContext(), isChecked)

            if (isChecked) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("ACTIVATE WAR MODE?")
                    .setMessage("If your phone enters the GPS coordinates of Evin Prison, it will instantly self-destruct. \n\nGPS drift is possible. Do not use this if you live or drive immediately adjacent to the prison walls.")
                    .setPositiveButton("ARM SYSTEM") { _, _ ->
                        val intent = Intent(requireContext(), ZoneWipeService::class.java)
                        ContextCompat.startForegroundService(requireContext(), intent)
                        Toast.makeText(requireContext(), "Geographic Suicide Armed.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        binding.switchGeofenceSuicide.isChecked = false
                    }
                    .setCancelable(false)
                    .show()
            } else {
                requireContext().stopService(Intent(requireContext(), ZoneWipeService::class.java))
                Toast.makeText(requireContext(), "Geographic Suicide Disarmed.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchWatchdogMode.setOnCheckedChangeListener { _, isChecked ->
            binding.menuWatchdogInterval.isEnabled = isChecked
            SecurityPreferences.setWatchdogModeEnabled(requireContext(), isChecked)
            WatchdogManager.scheduleOrCancelWatchdog(requireContext())
        }

        binding.autoCompleteWatchdogInterval.setOnItemClickListener { _, _, position, _ ->
            val values = resources.getStringArray(R.array.watchdog_interval_values)
            SecurityPreferences.setWatchdogInterval(requireContext(), values[position].toInt())
            WatchdogManager.scheduleOrCancelWatchdog(requireContext())
        }

        binding.switchTripwireMode.setOnCheckedChangeListener { _, isChecked ->
            binding.menuTripwireDuration.isEnabled = isChecked
            SecurityPreferences.setTripwireEnabled(requireContext(), isChecked)
            TripwireManager.scheduleOrCancelTripwire(requireContext())
        }

        binding.autoCompleteTripwireDuration.setOnItemClickListener { _, _, position, _ ->
            val values = resources.getStringArray(R.array.tripwire_duration_values)
            SecurityPreferences.setTripwireDuration(requireContext(), values[position].toInt())
            TripwireManager.scheduleOrCancelTripwire(requireContext())
        }

        binding.switchMaintenanceMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                promptForPinToToggleMaintenanceMode()
            } else {
                SecurityPreferences.setMaintenanceMode(requireContext(), false)
                Toast.makeText(requireContext(), "Maintenance Mode Disabled.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRootListeners() {
        if (!isRooted) return

        binding.switchRootGpsSpoof.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutDecoyGps.isEnabled = isChecked
            SecurityPreferences.setGpsSpoofingEnabled(requireContext(), isChecked)
        }

        binding.etDecoyGpsLocation.doAfterTextChanged {
            SecurityPreferences.setDecoyGpsLocation(requireContext(), it.toString())
        }

        binding.switchRootSilentInstall.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutRemoteApkUrl.isEnabled = isChecked
            SecurityPreferences.setSilentInstallEnabled(requireContext(), isChecked)
        }

        binding.etRemoteApkUrl.doAfterTextChanged {
            SecurityPreferences.setRemoteApkUrl(requireContext(), it.toString())
        }

        // --- NEW: USB Tripwire Listener ---
        binding.switchUsbTripwire.setOnCheckedChangeListener { _, isChecked ->
            // Assuming preference set method exists
            // SecurityPreferences.setUsbTripwireEnabled(requireContext(), isChecked)

            if (isChecked) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("EXTREME DANGER")
                    .setMessage("This feature runs at the KERNEL level. If you plug your locked phone into a PC, car stereo, or even a 'smart' charger that attempts a data handshake, YOUR DATA WILL BE DESTROYED INSTANTLY.\n\nOnly enable this when entering high-risk zones.")
                    .setPositiveButton("I Understand") { _, _ ->
                        val intent = Intent(requireContext(), UsbTripwireService::class.java)
                        ContextCompat.startForegroundService(requireContext(), intent)
                        Toast.makeText(requireContext(), "USB Kill Switch Armed.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        binding.switchUsbTripwire.isChecked = false
                    }
                    .setCancelable(false)
                    .show()
            } else {
                requireContext().stopService(Intent(requireContext(), UsbTripwireService::class.java))
                Toast.makeText(requireContext(), "USB Kill Switch Disarmed.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchRootFirewallTripwire.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setFirewallTripwireEnabled(requireContext(), isChecked)
        }

        binding.switchRootSecureWipe.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setSecureWipeEnabled(requireContext(), isChecked)
        }

        binding.switchRootSystemApp.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showSystemAppConfirmationDialog()
            }
        }

        binding.switchRootUnkillableService.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                val success = RootActions.toggleUnkillableService(requireContext(), isChecked)
                if (success) {
                    SecurityPreferences.setUnkillableServiceEnabled(requireContext(), isChecked)
                    val message = if(isChecked) "Unkillable service enabled." else "Unkillable service disabled."
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Operation failed. Check logs.", Toast.LENGTH_LONG).show()
                    binding.switchRootUnkillableService.isChecked = !isChecked // Revert UI
                }
            }
        }

        binding.switchRootHideProcess.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                val success = RootActions.toggleProcessHiding(requireContext(), isChecked)
                if (success) {
                    SecurityPreferences.setProcessHiddenEnabled(requireContext(), isChecked)
                    val message = if(isChecked) "Process hiding enabled." else "Process hiding disabled."
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Operation failed. MagiskHide might not be available.", Toast.LENGTH_LONG).show()
                    binding.switchRootHideProcess.isChecked = !isChecked // Revert UI
                }
            }
        }

        binding.switchRootSurviveReset.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutLoaderScriptUrl.isEnabled = isChecked
            if (isChecked) {
                showFactoryResetWarningDialog()
            }
            // The preference is saved only after a successful dialog confirmation
        }

        binding.etLoaderScriptUrl.doAfterTextChanged {
            SecurityPreferences.setLoaderScriptUrl(requireContext(), it.toString())
        }

        binding.switchRootStealthScreenshot.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setStealthScreenshotEnabled(requireContext(), isChecked)
        }

        binding.switchRootKeylogger.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setKeyloggerEnabled(requireContext(), isChecked)
            // Start or stop the keylogger immediately
            if (isChecked) {
                Keylogger.start(requireContext())
            } else {
                Keylogger.stop()
            }
        }

        binding.switchRootStealthMedia.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setStealthMediaCaptureEnabled(requireContext(), isChecked)
        }
    }

    private fun loadSettings() {
        val context = requireContext()

        // Features
        binding.switchRecordVideo.isChecked = SecurityPreferences.isRecordVideoEnabled(context)
        binding.switchAmbientAudio.isChecked = SecurityPreferences.isAmbientAudioEnabled(context)
        binding.switchWipeDevice.isChecked = SecurityPreferences.isWipeDeviceEnabled(context)
        binding.switchHardwareWipe.isChecked = SecurityPreferences.isHardwareWipeEnabled(context)

        val isIntruderSelfieEnabled = SecurityPreferences.isIntruderSelfieEnabled(context)
        binding.switchIntruderSelfie.isChecked = isIntruderSelfieEnabled
        binding.switchSaveSelfieToStorage.isChecked = SecurityPreferences.isSaveSelfieToStorageEnabled(context)
        binding.switchSimChangeAlert.isChecked = SecurityPreferences.isSimChangeAlertEnabled(context)
        binding.switchFakeShutdown.isChecked = SecurityPreferences.isFakeShutdownEnabled(context)
        binding.switchShakeToPanic.isChecked = SecurityPreferences.isShakeToPanicEnabled(context)

        // Check if services are running to update switch state
        // Note: This assumes service running state is truth. For persistence, prefs should be checked.
        // We initialize switches to OFF by default if service check is complex, but here we can check if enabled
        // via preferences if those methods are added to SecurityPreferences.
        // For now, we leave them off on load or rely on stored prefs if you implement get methods.

        // Stealth Mode & App Lock
        binding.switchStealthMode.isChecked = isStealthModeEnabled()
        binding.etSecretDialerCode.setText(SecurityPreferences.getSecretDialerCode(context))
        binding.switchBiometricLock.isChecked = SecurityPreferences.isBiometricLockEnabled(context)
        binding.switchTrustedVpn.isChecked = SecurityPreferences.isTrustedVpnEnabled(context)

        // Maintenance Mode
        binding.switchMaintenanceMode.isChecked = SecurityPreferences.isMaintenanceMode(context)

        // Geofence
        val isGeofenceEnabled = SecurityPreferences.isGeofenceEnabled(context)
        binding.switchGeofence.isChecked = isGeofenceEnabled

        // Watchdog
        val isWatchdogEnabled = SecurityPreferences.isWatchdogModeEnabled(context)
        binding.switchWatchdogMode.isChecked = isWatchdogEnabled
        setupDropdown(
            R.array.watchdog_interval_entries,
            R.array.watchdog_interval_values,
            SecurityPreferences.getWatchdogInterval(context),
            binding.autoCompleteWatchdogInterval
        )

        // Tripwire
        val isTripwireEnabled = SecurityPreferences.isTripwireEnabled(context)
        binding.switchTripwireMode.isChecked = isTripwireEnabled
        setupDropdown(
            R.array.tripwire_duration_entries,
            R.array.tripwire_duration_values,
            SecurityPreferences.getTripwireDuration(context),
            binding.autoCompleteTripwireDuration
        )

        // Set initial enabled state for dependent controls
        binding.switchSaveSelfieToStorage.isEnabled = isIntruderSelfieEnabled
        binding.btnSetGeofenceLocation.isEnabled = isGeofenceEnabled
        binding.menuWatchdogInterval.isEnabled = isWatchdogEnabled
        binding.menuTripwireDuration.isEnabled = isTripwireEnabled
    }

    private fun loadRootSettings() {
        val context = requireContext()
        if (isRooted) {
            val isGpsSpoofEnabled = SecurityPreferences.isGpsSpoofingEnabled(context)
            binding.switchRootGpsSpoof.isChecked = isGpsSpoofEnabled
            binding.etDecoyGpsLocation.setText(SecurityPreferences.getDecoyGpsLocation(context))
            binding.layoutDecoyGps.isEnabled = isGpsSpoofEnabled

            val isSilentInstallEnabled = SecurityPreferences.isSilentInstallEnabled(context)
            binding.switchRootSilentInstall.isChecked = isSilentInstallEnabled
            binding.etRemoteApkUrl.setText(SecurityPreferences.getRemoteApkUrl(context))
            binding.layoutRemoteApkUrl.isEnabled = isSilentInstallEnabled

            binding.switchRootFirewallTripwire.isChecked = SecurityPreferences.isFirewallTripwireEnabled(context)
            binding.switchRootSecureWipe.isChecked = SecurityPreferences.isSecureWipeEnabled(context)
            binding.switchRootSystemApp.isChecked = SecurityPreferences.isSystemAppEnabled(context)
            binding.switchRootSystemApp.isEnabled = !SecurityPreferences.isSystemAppEnabled(context)

            binding.switchRootUnkillableService.isChecked = SecurityPreferences.isUnkillableServiceEnabled(context)
            binding.switchRootHideProcess.isChecked = SecurityPreferences.isProcessHiddenEnabled(context)

            val isSurviveResetEnabled = SecurityPreferences.isSurviveFactoryResetEnabled(context)
            binding.switchRootSurviveReset.isChecked = isSurviveResetEnabled
            binding.layoutLoaderScriptUrl.isEnabled = isSurviveResetEnabled
            binding.etLoaderScriptUrl.setText(SecurityPreferences.getLoaderScriptUrl(context))

            binding.switchRootStealthScreenshot.isChecked = SecurityPreferences.isStealthScreenshotEnabled(context)
            binding.switchRootKeylogger.isChecked = SecurityPreferences.isKeyloggerEnabled(context)
            binding.switchRootStealthMedia.isChecked = SecurityPreferences.isStealthMediaCaptureEnabled(context)
        }
    }

    private fun setStealthMode(enable: Boolean) {
        val context = requireContext()
        val packageManager = context.packageManager
        val launcherComponent = ComponentName(context, "com.hamoon.uncleted.Launcher")
        val state = if (enable) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        packageManager.setComponentEnabledSetting(launcherComponent, state, PackageManager.DONT_KILL_APP)
    }

    private fun isStealthModeEnabled(): Boolean {
        return try {
            val context = requireContext()
            val packageManager = context.packageManager
            val launcherComponent = ComponentName(context, "com.hamoon.uncleted.Launcher")
            packageManager.getComponentEnabledSetting(launcherComponent) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (e: Exception) {
            false
        }
    }

    private fun promptForPinToToggleMaintenanceMode() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_pin_prompt, null)
        val pinInput = dialogView.findViewById<TextInputEditText>(R.id.et_pin_entry_dialog)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enter PIN to Enable Maintenance")
            .setMessage("For your security, please enter your normal unlock PIN to enable Maintenance Mode. This will allow you to safely disable Device Admin or uninstall the app.")
            .setView(dialogView)
            .setNegativeButton("Cancel") { _, _ ->
                binding.switchMaintenanceMode.isChecked = false
            }
            .setPositiveButton("Confirm") { _, _ ->
                val enteredPin = pinInput.text.toString()
                val correctPin = SecurityPreferences.getNormalPin(requireContext())
                if (enteredPin == correctPin) {
                    SecurityPreferences.setMaintenanceMode(requireContext(), true)
                    Toast.makeText(requireContext(), "Maintenance Mode Enabled.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Incorrect PIN.", Toast.LENGTH_SHORT).show()
                    binding.switchMaintenanceMode.isChecked = false
                }
            }
            .setOnCancelListener {
                binding.switchMaintenanceMode.isChecked = false
            }
            .show()
    }

    private fun showSystemAppConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.system_app_warning_title)
            .setMessage(R.string.system_app_warning_message)
            .setNegativeButton("Cancel") { _, _ ->
                binding.switchRootSystemApp.isChecked = false
            }
            .setPositiveButton("Proceed & Reboot") { _, _ ->
                Toast.makeText(requireContext(), "Converting to system app and rebooting...", Toast.LENGTH_LONG).show()
                lifecycleScope.launch {
                    val success = RootActions.convertToSystemApp(requireContext())
                    if (success) {
                        SecurityPreferences.setSystemAppEnabled(requireContext(), true)
                    } else {
                        Toast.makeText(requireContext(), "Failed to convert to system app. Check logs.", Toast.LENGTH_LONG).show()
                        binding.switchRootSystemApp.isChecked = false
                    }
                }
            }
            .setOnCancelListener {
                binding.switchRootSystemApp.isChecked = false
            }
            .show()
    }

    private fun showFactoryResetWarningDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.factory_reset_warning_title))
            .setMessage(getString(R.string.factory_reset_warning_message))
            .setNegativeButton("Cancel") { _, _ ->
                binding.switchRootSurviveReset.isChecked = false
                SecurityPreferences.setSurviveFactoryResetEnabled(requireContext(), false)
            }
            .setPositiveButton("I Understand, Proceed") { _, _ ->
                Toast.makeText(requireContext(), "Attempting to flash loader...", Toast.LENGTH_LONG).show()
                lifecycleScope.launch {
                    val success = RootActions.flashResetSurvivalLoader(requireContext())
                    if (success) {
                        SecurityPreferences.setSurviveFactoryResetEnabled(requireContext(), true)
                        Toast.makeText(requireContext(), "Loader flashed successfully.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "FLASH FAILED. CHECK LOGS.", Toast.LENGTH_LONG).show()
                        binding.switchRootSurviveReset.isChecked = false
                        SecurityPreferences.setSurviveFactoryResetEnabled(requireContext(), false)
                    }
                }
            }
            .setOnCancelListener {
                binding.switchRootSurviveReset.isChecked = false
                SecurityPreferences.setSurviveFactoryResetEnabled(requireContext(), false)
            }
            .show()
    }

    private fun requestLocationAndSetGeofence() {
        when {
            PermissionUtils.hasLocationPermissions(requireContext()) -> {
                setCurrentLocationAsGeofence()
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun setCurrentLocationAsGeofence() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    SecurityPreferences.setGeofenceLocation(requireContext(), location.latitude, location.longitude)
                    GeofenceHelper.addGeofence(location.latitude, location.longitude)
                    Toast.makeText(requireContext(), "Safe zone set to current location.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Could not retrieve current location. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun setupDropdown(entriesRes: Int, valuesRes: Int, currentValue: Int, autoCompleteTextView: android.widget.AutoCompleteTextView) {
        val entries = resources.getStringArray(entriesRes)
        val values = resources.getStringArray(valuesRes)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, entries)
        autoCompleteTextView.setAdapter(adapter)

        val currentIndex = values.indexOf(currentValue.toString()).takeIf { it != -1 } ?: 0
        autoCompleteTextView.setText(entries[currentIndex], false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStop() {
        super.onStop()
        if (SecurityPreferences.isKeyloggerEnabled(requireContext())) {
            Keylogger.stop()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isRooted && SecurityPreferences.isKeyloggerEnabled(requireContext())) {
            Keylogger.start(requireContext())
        }
    }
}
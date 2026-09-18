package com.hamoon.uncleted.fragments

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
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
                Toast.makeText(requireContext(), "Location permission is required.", Toast.LENGTH_LONG).show()
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
                if (isRooted) {
                    Keylogger.startHardwareKeyMonitor(requireContext())
                }
                Toast.makeText(requireContext(), "Hardware Wipe Enabled: Press Vol UP, DOWN, UP, DOWN rapidly to wipe.", Toast.LENGTH_LONG).show()
            } else {
                if (isRooted && !SecurityPreferences.isKeyloggerEnabled(requireContext())) {
                    Keylogger.stop()
                }
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

        binding.switchGeofenceSuicide.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("ACTIVATE NO-GO SUICIDE?")
                    .setMessage("If your phone enters active destruction zones, it will instantly wipe cryptographic keys.")
                    .setPositiveButton("ARM SYSTEM") { _, _ ->
                        SecurityPreferences.setGeofenceSuicideEnabled(requireContext(), true)
                        val intent = Intent(requireContext(), ZoneWipeService::class.java)
                        ContextCompat.startForegroundService(requireContext(), intent)
                        Toast.makeText(requireContext(), "Geographic Destruction Armed.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        binding.switchGeofenceSuicide.isChecked = false
                        SecurityPreferences.setGeofenceSuicideEnabled(requireContext(), false)
                    }
                    .setCancelable(false)
                    .show()
            } else {
                SecurityPreferences.setGeofenceSuicideEnabled(requireContext(), false)
                requireContext().stopService(Intent(requireContext(), ZoneWipeService::class.java))
                Toast.makeText(requireContext(), "Geographic Destruction Disarmed.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnManageWipeZones.setOnClickListener {
            showManageWipeZonesDialog()
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

    private fun showManageWipeZonesDialog() {
        val context = requireContext()
        val zones = SecurityPreferences.getCustomWipeZones(context)
        val zoneNames = zones.map { "${it.name} (${if (it.radiusMeters > 0) "${it.radiusMeters.toInt()}m radius" else "${it.polygon.size} pts"})" }.toMutableList()
        zoneNames.add(0, "[+] Add Current Location as Wipe Zone")
        zoneNames.add(1, "[+] Add Custom Coordinates (Lat, Lon, Radius)")

        MaterialAlertDialogBuilder(context)
            .setTitle("Destruction No-Go Zones")
            .setItems(zoneNames.toTypedArray()) { _, which ->
                when (which) {
                    0 -> promptAddCurrentLocationAsWipeZone()
                    1 -> promptAddManualWipeZone()
                    else -> {
                        val selectedZone = zones[which - 2]
                        promptZoneActions(selectedZone)
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun promptAddCurrentLocationAsWipeZone() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(requireActivity())
        client.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                promptZoneRadiusAndName(loc.latitude, loc.longitude)
            } else {
                Toast.makeText(requireContext(), "Could not retrieve GPS fix. Try again outdoors.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun promptAddManualWipeZone() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val etName = EditText(requireContext()).apply { hint = "Zone Name (e.g. Danger Area)" }
        val etLat = EditText(requireContext()).apply { hint = "Latitude (e.g. 35.7921)" }
        val etLon = EditText(requireContext()).apply { hint = "Longitude (e.g. 51.3814)" }
        val etRadius = EditText(requireContext()).apply { hint = "Radius in meters (e.g. 150)" }

        layout.addView(etName)
        layout.addView(etLat)
        layout.addView(etLon)
        layout.addView(etRadius)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create Custom Wipe Zone")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = etName.text.toString().ifEmpty { "Custom Zone" }
                val lat = etLat.text.toString().toDoubleOrNull()
                val lon = etLon.text.toString().toDoubleOrNull()
                val radius = etRadius.text.toString().toFloatOrNull() ?: 100f

                if (lat != null && lon != null) {
                    val zone = PolygonUtils.WipeZone(
                        name = name,
                        centerLat = lat,
                        centerLon = lon,
                        radiusMeters = radius,
                        isEnabled = true
                    )
                    SecurityPreferences.addCustomWipeZone(requireContext(), zone)
                    Toast.makeText(requireContext(), "Destruction Zone '$name' created.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Invalid coordinates.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptZoneRadiusAndName(lat: Double, lon: Double) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val etName = EditText(requireContext()).apply { hint = "Zone Name" }
        val etRadius = EditText(requireContext()).apply { hint = "Radius (meters, default 100)" }

        layout.addView(etName)
        layout.addView(etRadius)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Arm Current Location")
            .setMessage("Lat: $lat, Lon: $lon")
            .setView(layout)
            .setPositiveButton("Arm Zone") { _, _ ->
                val name = etName.text.toString().ifEmpty { "Location Zone" }
                val radius = etRadius.text.toString().toFloatOrNull() ?: 100f

                val zone = PolygonUtils.WipeZone(
                    name = name,
                    centerLat = lat,
                    centerLon = lon,
                    radiusMeters = radius,
                    isEnabled = true
                )
                SecurityPreferences.addCustomWipeZone(requireContext(), zone)
                Toast.makeText(requireContext(), "Destruction Zone '$name' armed ($radius m).", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptZoneActions(zone: PolygonUtils.WipeZone) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(zone.name)
            .setMessage("Coordinates: ${zone.centerLat}, ${zone.centerLon}\nRadius: ${zone.radiusMeters}m")
            .setNeutralButton("Delete Zone") { _, _ ->
                SecurityPreferences.removeCustomWipeZone(requireContext(), zone.id)
                Toast.makeText(requireContext(), "Zone '${zone.name}' deleted.", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Close", null)
            .show()
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

        binding.switchUsbTripwire.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("EXTREME DANGER")
                    .setMessage("This feature monitors the Kernel USB subsystem. Connecting your locked phone to a computer will trigger emergency key eviction.")
                    .setPositiveButton("I Understand") { _, _ ->
                        SecurityPreferences.setUsbTripwireEnabled(requireContext(), true)
                        val intent = Intent(requireContext(), UsbTripwireService::class.java)
                        ContextCompat.startForegroundService(requireContext(), intent)
                        Toast.makeText(requireContext(), "USB Kill Switch Armed.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        binding.switchUsbTripwire.isChecked = false
                        SecurityPreferences.setUsbTripwireEnabled(requireContext(), false)
                    }
                    .setCancelable(false)
                    .show()
            } else {
                SecurityPreferences.setUsbTripwireEnabled(requireContext(), false)
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
                    val message = if (isChecked) "Unkillable service enabled." else "Unkillable service disabled."
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Operation failed. Check logs.", Toast.LENGTH_LONG).show()
                    binding.switchRootUnkillableService.isChecked = !isChecked
                }
            }
        }

        binding.switchRootHideProcess.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                val success = RootActions.toggleProcessHiding(requireContext(), isChecked)
                if (success) {
                    SecurityPreferences.setProcessHiddenEnabled(requireContext(), isChecked)
                    val message = if (isChecked) "Process hiding enabled." else "Process hiding disabled."
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Operation failed.", Toast.LENGTH_LONG).show()
                    binding.switchRootHideProcess.isChecked = !isChecked
                }
            }
        }

        binding.switchRootStealthScreenshot.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setStealthScreenshotEnabled(requireContext(), isChecked)
        }

        binding.switchRootKeylogger.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setKeyloggerEnabled(requireContext(), isChecked)
            if (isChecked) {
                Keylogger.startHardwareKeyMonitor(requireContext())
            } else if (!SecurityPreferences.isHardwareWipeEnabled(requireContext())) {
                Keylogger.stop()
            }
        }

        binding.switchRootStealthMedia.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setStealthMediaCaptureEnabled(requireContext(), isChecked)
        }
    }

    private fun loadSettings() {
        val context = requireContext()

        binding.switchRecordVideo.isChecked = SecurityPreferences.isRecordVideoEnabled(context)
        binding.switchAmbientAudio.isChecked = SecurityPreferences.isAmbientAudioEnabled(context)
        binding.switchWipeDevice.isChecked = SecurityPreferences.isWipeDeviceEnabled(context)
        binding.switchHardwareWipe.isChecked = SecurityPreferences.isHardwareWipeEnabled(context)

        val isIntruderSelfieEnabled = SecurityPreferences.isIntruderSelfieEnabled(context)
        binding.switchIntruderSelfie.isChecked = isIntruderSelfieEnabled
        binding.switchSaveSelfieToStorage.isChecked = SecurityPreferences.isSaveSelfieToStorageEnabled(context)
        binding.switchSimChangeAlert.isChecked = SecurityPreferences.isSimChangeAlertEnabled(context)
        binding.switchShakeToPanic.isChecked = SecurityPreferences.isShakeToPanicEnabled(context)

        binding.switchGeofenceSuicide.isChecked = SecurityPreferences.isGeofenceSuicideEnabled(context)

        binding.switchStealthMode.isChecked = isStealthModeEnabled()
        binding.etSecretDialerCode.setText(SecurityPreferences.getSecretDialerCode(context))
        binding.switchBiometricLock.isChecked = SecurityPreferences.isBiometricLockEnabled(context)
        binding.switchTrustedVpn.isChecked = SecurityPreferences.isTrustedVpnEnabled(context)

        binding.switchMaintenanceMode.isChecked = SecurityPreferences.isMaintenanceMode(context)

        val isGeofenceEnabled = SecurityPreferences.isGeofenceEnabled(context)
        binding.switchGeofence.isChecked = isGeofenceEnabled

        val isWatchdogEnabled = SecurityPreferences.isWatchdogModeEnabled(context)
        binding.switchWatchdogMode.isChecked = isWatchdogEnabled
        setupDropdown(
            R.array.watchdog_interval_entries,
            R.array.watchdog_interval_values,
            SecurityPreferences.getWatchdogInterval(context),
            binding.autoCompleteWatchdogInterval
        )

        val isTripwireEnabled = SecurityPreferences.isTripwireEnabled(context)
        binding.switchTripwireMode.isChecked = isTripwireEnabled
        setupDropdown(
            R.array.tripwire_duration_entries,
            R.array.tripwire_duration_values,
            SecurityPreferences.getTripwireDuration(context),
            binding.autoCompleteTripwireDuration
        )

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

            binding.switchUsbTripwire.isChecked = SecurityPreferences.isUsbTripwireEnabled(context)

            binding.switchRootFirewallTripwire.isChecked = SecurityPreferences.isFirewallTripwireEnabled(context)
            binding.switchRootSecureWipe.isChecked = SecurityPreferences.isSecureWipeEnabled(context)

            // Validate against the Android PackageManager FLAG_SYSTEM status directly
            val isActualSystemApp = (context.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            binding.switchRootSystemApp.isChecked = isActualSystemApp
            binding.switchRootSystemApp.isEnabled = !isActualSystemApp
            SecurityPreferences.setSystemAppEnabled(context, isActualSystemApp)

            binding.switchRootUnkillableService.isChecked = SecurityPreferences.isUnkillableServiceEnabled(context)
            binding.switchRootHideProcess.isChecked = SecurityPreferences.isProcessHiddenEnabled(context)

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
            .setMessage("Please enter your normal unlock PIN to enable Maintenance Mode.")
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
        val context = requireContext()
        lifecycleScope.launch {
            val provider = RootChecker.getRootProvider()
            val isKernelSu = provider == RootChecker.RootProvider.KERNEL_SU
            val hasMetamodule = RootExecutor.run("test -d /data/adb/metamodule || ls -d /data/adb/modules/meta-* 2>/dev/null", logErrors = false).isSuccess

            val message = if (isKernelSu && !hasMetamodule) {
                "${getString(R.string.system_app_warning_message)}\n\n" +
                        "⚠️ KernelSU Metamodule Notice: KernelSU requires an active metamodule (such as meta-overlayfs or hybrid-mount) to mount /system/priv-app. " +
                        "The module will be configured, but ensure meta-overlayfs is active in KernelSU."
            } else {
                getString(R.string.system_app_warning_message)
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.system_app_warning_title)
                .setMessage(message)
                .setNegativeButton("Cancel") { _, _ ->
                    binding.switchRootSystemApp.isChecked = false
                }
                .setPositiveButton("Proceed & Reboot") { _, _ ->
                    Toast.makeText(context, "Converting to system app and rebooting...", Toast.LENGTH_LONG).show()
                    lifecycleScope.launch {
                        val success = RootActions.convertToSystemApp(context)
                        if (success) {
                            SecurityPreferences.setSystemAppEnabled(context, true)
                        } else {
                            Toast.makeText(context, "Failed to convert to system app. Check logs.", Toast.LENGTH_LONG).show()
                            binding.switchRootSystemApp.isChecked = false
                        }
                    }
                }
                .setOnCancelListener {
                    binding.switchRootSystemApp.isChecked = false
                }
                .show()
        }
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

    override fun onResume() {
        super.onResume()
        loadSettings()
        if (isRooted) {
            loadRootSettings()
        }
    }
}

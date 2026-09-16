package com.hamoon.uncleted.fragments

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hamoon.uncleted.R
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.FragmentProximityTripwireBinding
import com.hamoon.uncleted.proximity.BleProximitySentinel
import com.hamoon.uncleted.proximity.ProximityShardingEngine
import com.hamoon.uncleted.services.ZoneWipeService
import com.hamoon.uncleted.util.PermissionUtils
import com.hamoon.uncleted.util.PolygonUtils
import com.hamoon.uncleted.util.TripwireManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProximityTripwireFragment : Fragment() {

    private var _binding: FragmentProximityTripwireBinding? = null
    private val binding get() = _binding!!

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                promptAddCurrentLocationAsWipeZone()
            } else {
                Toast.makeText(requireContext(), "Location permission is required for geofence arming.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProximityTripwireBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
        observeSentinelStatus()
    }

    private fun loadSettings() {
        val context = requireContext()

        // 1. BLE Proximity Settings
        binding.switchProximitySharding.isChecked = SecurityPreferences.isProximityShardingEnabled(context)
        binding.etProximityTargetMac.setText(SecurityPreferences.getProximityBleTargetAddress(context) ?: "")
        binding.etProximityRssiThreshold.setText(SecurityPreferences.getProximityRssiThreshold(context).toString())
        binding.etProximityBreachLimit.setText(SecurityPreferences.getProximityMissedHeartbeatThreshold(context).toString())

        // 2. Dead-Man Tripwire Settings
        binding.switchDeadmanTripwire.isChecked = SecurityPreferences.isTripwireEnabled(context)
        setupDurationDropdown(SecurityPreferences.getTripwireDuration(context))

        // 3. Geographic Suicide Settings
        binding.switchGeofenceSuicide.isChecked = SecurityPreferences.isGeofenceSuicideEnabled(context)
    }

    private fun setupDurationDropdown(currentHours: Int) {
        val entries = resources.getStringArray(R.array.tripwire_duration_entries)
        val values = resources.getStringArray(R.array.tripwire_duration_values)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, entries)
        binding.autoTripwireDuration.setAdapter(adapter)

        val currentIndex = values.indexOf(currentHours.toString()).takeIf { it != -1 } ?: 2
        binding.autoTripwireDuration.setText(entries[currentIndex], false)
    }

    private fun setupListeners() {
        val context = requireContext()

        binding.switchProximitySharding.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setProximityShardingEnabled(context, isChecked)
        }

        binding.btnProvisionShards.setOnClickListener {
            val shardBBase64 = ProximityShardingEngine.provisionFreshMasterShards(context)
            if (shardBBase64 != null) {
                showShardBExportDialog(shardBBase64)
            } else {
                Toast.makeText(context, "Failed to seal Shard A into discrete StrongBox HSM.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchDeadmanTripwire.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setTripwireEnabled(context, isChecked)
            TripwireManager.scheduleOrCancelTripwire(context)
        }

        binding.autoTripwireDuration.setOnItemClickListener { _, _, position, _ ->
            val values = resources.getStringArray(R.array.tripwire_duration_values)
            val selectedHours = values[position].toInt()
            SecurityPreferences.setTripwireDuration(context, selectedHours)
            TripwireManager.scheduleOrCancelTripwire(context)
        }

        binding.btnDeadmanCheckin.setOnClickListener {
            TripwireManager.checkIn(context)
            Toast.makeText(context, "Check-in recorded. Tripwire hardware alarm reset.", Toast.LENGTH_SHORT).show()
        }

        binding.switchGeofenceSuicide.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                MaterialAlertDialogBuilder(context)
                    .setTitle("ACTIVATE GEOGRAPHIC SUICIDE?")
                    .setMessage("Entering pre-configured destruction perimeters (such as Evin Prison) or custom zones will trigger instant silicon-level key revocation and zero partitions.")
                    .setPositiveButton("ARM SYSTEM") { _, _ ->
                        SecurityPreferences.setGeofenceSuicideEnabled(context, true)
                        val zoneIntent = Intent(context, ZoneWipeService::class.java)
                        ContextCompat.startForegroundService(context, zoneIntent)
                        Toast.makeText(context, "Geographic Destruction Armed.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        binding.switchGeofenceSuicide.isChecked = false
                        SecurityPreferences.setGeofenceSuicideEnabled(context, false)
                    }
                    .setCancelable(false)
                    .show()
            } else {
                SecurityPreferences.setGeofenceSuicideEnabled(context, false)
                context.stopService(Intent(context, ZoneWipeService::class.java))
                Toast.makeText(context, "Geographic Destruction Disarmed.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnManageWipeZones.setOnClickListener {
            showManageWipeZonesDialog()
        }

        binding.btnSaveProximitySettings.setOnClickListener {
            saveProximityParameters()
            Toast.makeText(context, "Proximity & dead-man sentinel parameters saved.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveProximityParameters() {
        val context = requireContext()
        val mac = binding.etProximityTargetMac.text?.toString()?.trim()
        val rssi = binding.etProximityRssiThreshold.text?.toString()?.toIntOrNull() ?: -85
        val breachLimit = binding.etProximityBreachLimit.text?.toString()?.toIntOrNull() ?: 3

        SecurityPreferences.setProximityBleTargetAddress(context, if (mac.isNullOrEmpty()) null else mac)
        SecurityPreferences.setProximityRssiThreshold(context, rssi)
        SecurityPreferences.setProximityMissedHeartbeatThreshold(context, breachLimit)
    }

    private fun observeSentinelStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            BleProximitySentinel.statusFlow.collectLatest { status ->
                if (_binding != null) {
                    binding.tvProximityConnectionStatus.text = "GATT State: ${status.connectionState}"
                    binding.tvProximityLiveRssi.text = "Token RSSI: ${status.lastRssi} dBm (Breaches: ${status.consecutiveBreaches})"

                    if (status.isShardBLoaded) {
                        binding.tvProximityShardStatus.text = "RAM Shard B: Armed & Pinned in LPDDR5"
                        binding.tvProximityShardStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_green))
                    } else {
                        binding.tvProximityShardStatus.text = "RAM Shard B: Evaporated (BFU State)"
                        binding.tvProximityShardStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_yellow))
                    }
                }
            }
        }
    }

    private fun showShardBExportDialog(shardBBase64: String) {
        val context = requireContext()
        val textView = TextView(context).apply {
            text = shardBBase64
            setPadding(48, 24, 48, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f // Fixed 12f literal
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Shard B Provisioned (Wearable Share)")
            .setMessage("Transmit this 256-bit information-theoretic share to your paired hardware token. The phone retains only Shard A in discrete StrongBox HSM.")
            .setView(textView)
            .setPositiveButton("Copy Shard B") { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("UncleTed_Shard_B", shardBBase64))
                Toast.makeText(context, "Shard B copied to clipboard.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
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
        if (!PermissionUtils.hasLocationPermissions(requireContext())) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(requireActivity())
        try {
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    promptZoneRadiusAndName(loc.latitude, loc.longitude)
                } else {
                    Toast.makeText(requireContext(), "Could not retrieve GPS fix. Try again outdoors.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: SecurityException) {}
    }

    private fun promptAddManualWipeZone() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val etName = EditText(requireContext()).apply { hint = "Zone Name (e.g. Hostile Facility)" }
        val etLat = EditText(requireContext()).apply { hint = "Latitude (e.g. 35.7921)" }
        val etLon = EditText(requireContext()).apply { hint = "Longitude (e.g. 51.3814)" }
        val etRadius = EditText(requireContext()).apply { hint = "Radius in meters (e.g. 150)" }

        layout.addView(etName)
        layout.addView(etLat)
        layout.addView(etLon)
        layout.addView(etRadius)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create Custom Destruction Zone")
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
            .setTitle("Arm Current Location as Wipe Zone")
            .setMessage("Coordinates: $lat, $lon")
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
            .setMessage("Center: ${zone.centerLat}, ${zone.centerLon}\nRadius: ${zone.radiusMeters}m")
            .setNeutralButton("Delete Zone") { _, _ ->
                SecurityPreferences.removeCustomWipeZone(requireContext(), zone.id)
                Toast.makeText(requireContext(), "Zone '${zone.name}' deleted.", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
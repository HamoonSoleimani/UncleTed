package com.hamoon.uncleted.proximity

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class BleProximitySentinel(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private var activeGatt: BluetoothGatt? = null
    private var heartbeatJob: Job? = null
    private var isRunning = false

    private var consecutiveRssiBreaches = 0
    private var lastHeartbeatTime = 0L

    companion object {
        private const val TAG = "BleProximitySentinel"
        private const val HEARTBEAT_INTERVAL_MS = 1500L
        private const val HEARTBEAT_TIMEOUT_MS = 6000L

        // Primary Sharding Service UUID
        val SERVICE_UUID: UUID = UUID.fromString("0000UT01-0000-1000-8000-00805F9B34FB")
        // Shard B Transmission Characteristic
        val SHARD_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000UT02-0000-1000-8000-00805F9B34FB")
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning || bluetoothAdapter == null) return

        if (!SecurityPreferences.isProximityShardingEnabled(context)) {
            Log.d(TAG, "Proximity Sharding Sentinel disabled in preferences.")
            return
        }

        val targetAddress = SecurityPreferences.getProximityBleTargetAddress(context)
        if (targetAddress.isNullOrBlank() || !BluetoothAdapter.checkBluetoothAddress(targetAddress)) {
            Log.w(TAG, "No valid target BLE hardware address configured for proximity sharding.")
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Log.w(TAG, "Bluetooth adapter disabled; cannot arm BLE Proximity Sentinel.")
            return
        }

        isRunning = true
        consecutiveRssiBreaches = 0
        lastHeartbeatTime = SystemClock.elapsedRealtime()

        connectGatt(targetAddress)
        startHeartbeatLoop()
        Log.i(TAG, "BLE Proximity Sentinel armed against target: $targetAddress")
        EventLogger.log(context, "PROXIMITY: BLE Hardware Sentinel connected to target $targetAddress.")
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!isRunning) return
        isRunning = false

        heartbeatJob?.cancel()
        heartbeatJob = null

        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GATT connection", e)
        }
        activeGatt = null

        ProximityShardingEngine.purgeVolatileShard(context)
        Log.i(TAG, "BLE Proximity Sentinel stopped.")
    }

    @SuppressLint("MissingPermission")
    private fun connectGatt(address: String) {
        try {
            val device: BluetoothDevice = bluetoothAdapter?.getRemoteDevice(address) ?: return
            activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed initiating GATT connection to $address", e)
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isRunning) {
                delay(HEARTBEAT_INTERVAL_MS)
                checkHeartbeatAndQueryRssi()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkHeartbeatAndQueryRssi() {
        val now = SystemClock.elapsedRealtime()

        // 1. Verify Remote Heartbeat Timeout
        if (now - lastHeartbeatTime > HEARTBEAT_TIMEOUT_MS) {
            Log.e(TAG, "BREACH: Proximity token heartbeat timeout! Peripheral disconnected or confiscated.")
            triggerProximityBreachEviction("PERIPHERAL_HEARTBEAT_TIMEOUT")
            return
        }

        // 2. Poll Remote RSSI
        activeGatt?.let { gatt ->
            try {
                gatt.readRemoteRssi()
            } catch (e: Exception) {
                Log.w(TAG, "Failed querying remote RSSI", e)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT connected to proximity peripheral. Discovering services...")
                lastHeartbeatTime = SystemClock.elapsedRealtime()
                consecutiveRssiBreaches = 0
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "GATT connection severed with proximity token.")
                triggerProximityBreachEviction("PERIPHERAL_DISCONNECTED")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(SHARD_CHARACTERISTIC_UUID)
                if (characteristic != null) {
                    gatt.readCharacteristic(characteristic)
                }
            }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == SHARD_CHARACTERISTIC_UUID) {
                val value = characteristic.value
                if (value != null && value.isNotEmpty()) {
                    ProximityShardingEngine.loadVolatileShardB(context, value)
                    lastHeartbeatTime = SystemClock.elapsedRealtime()
                }
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                lastHeartbeatTime = SystemClock.elapsedRealtime()
                evaluateRssiDistance(rssi)
            }
        }
    }

    private fun evaluateRssiDistance(rssi: Int) {
        val threshold = SecurityPreferences.getProximityRssiThreshold(context)
        val breachLimit = SecurityPreferences.getProximityMissedHeartbeatThreshold(context)

        Log.d(TAG, "Proximity token RSSI: $rssi dBm (Threshold: $threshold dBm)")

        if (rssi < threshold) {
            consecutiveRssiBreaches++
            Log.w(TAG, "Token distance breach ($consecutiveRssiBreaches/$breachLimit): RSSI $rssi < $threshold")

            if (consecutiveRssiBreaches >= breachLimit) {
                Log.e(TAG, "!!! PHYSICAL PROXIMITY SEPARATION CONFIRMED ($consecutiveRssiBreaches PINGS < $threshold dBm) !!!")
                triggerProximityBreachEviction("PERIPHERAL_OUT_OF_RANGE_RSSI_${rssi}dBm")
            }
        } else {
            consecutiveRssiBreaches = 0
        }
    }

    private fun triggerProximityBreachEviction(reason: String) {
        stop()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Purge volatile Shard B from memory immediately
                ProximityShardingEngine.purgeVolatileShard(context)

                // 2. Drop kernel Vold CE keys and lock device
                val strategy = DefenseCoordinator.resolveStrategy(context)
                strategy.evictMemoryKeysAndLock()

                // 3. Dispatch alert telemetry
                PanicActionService.trigger(
                    context,
                    "PROXIMITY_KEY_SHARD_SEVERED",
                    PanicActionService.Severity.HIGH
                )
                EventLogger.log(context, "CRITICAL: Proximity token separated ($reason). Keys evicted to BFU.")
            } catch (e: Exception) {
                Log.e(TAG, "Error executing proximity breach sequence", e)
            }
        }
    }
}
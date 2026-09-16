package com.hamoon.uncleted.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hamoon.uncleted.util.CredentialBridge
import com.hamoon.uncleted.util.PolygonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object SecurityPreferences {

    private const val TAG = "SecurityPreferences"

    @Volatile
    private var encryptedInstance: SharedPreferences? = null
    @Volatile
    private var deInstance: SharedPreferences? = null

    private val LOCK = Any()
    private const val PREFS_FILE_NAME = "secure_app_prefs"
    private const val DE_PREFS_FILE_NAME = "device_encrypted_prefs"
    private const val EVENT_LOG_KEY = "event_log"
    private const val MAX_LOG_ENTRIES = 150
    private const val CUSTOM_WIPE_ZONES_KEY = "CUSTOM_WIPE_ZONES"

    fun isUserUnlocked(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = context.getSystemService(UserManager::class.java)
            userManager?.isUserUnlocked ?: true
        } else {
            true
        }
    }

    fun getDeviceProtectedPrefs(context: Context): SharedPreferences {
        return deInstance ?: synchronized(LOCK) {
            deInstance ?: run {
                val deContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.createDeviceProtectedStorageContext()
                } else {
                    context
                }
                deContext.getSharedPreferences(DE_PREFS_FILE_NAME, Context.MODE_PRIVATE).also {
                    deInstance = it
                }
            }
        }
    }

    internal fun getInstance(context: Context): SharedPreferences {
        if (!isUserUnlocked(context)) {
            Log.w(TAG, "Device is in BFU state. Accessing Device-Protected storage.")
            return getDeviceProtectedPrefs(context)
        }

        return encryptedInstance ?: synchronized(LOCK) {
            encryptedInstance ?: try {
                createEncryptedPrefs(context.applicationContext).also {
                    encryptedInstance = it
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed initializing EncryptedSharedPreferences. Falling back to DE storage.", e)
                getDeviceProtectedPrefs(context)
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // =========================================================================
    // 1. Covert Canary Signaling via Oblivious HTTP (RFC 9458 / RFC 9180)
    // =========================================================================
    fun setOhttpCanaryEnabled(context: Context, isEnabled: Boolean) {
        getDeviceProtectedPrefs(context).edit().putBoolean("BFU_OHTTP_CANARY_ENABLED", isEnabled).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putBoolean("OHTTP_CANARY_ENABLED", isEnabled).apply()
        }
    }

    fun isOhttpCanaryEnabled(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("BFU_OHTTP_CANARY_ENABLED", true)
        } else {
            getDeviceProtectedPrefs(context).getBoolean("BFU_OHTTP_CANARY_ENABLED", true) &&
                    getInstance(context).getBoolean("OHTTP_CANARY_ENABLED", true)
        }
    }

    fun setOhttpRelayUrl(context: Context, url: String) {
        getDeviceProtectedPrefs(context).edit().putString("BFU_OHTTP_RELAY_URL", url.trim()).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putString("OHTTP_RELAY_URL", url.trim()).apply()
        }
    }

    fun getOhttpRelayUrl(context: Context): String {
        val defaultRelay = "https://cloudflare-dns.com/dns-query"
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getString("BFU_OHTTP_RELAY_URL", defaultRelay) ?: defaultRelay
        } else {
            getDeviceProtectedPrefs(context).getString(
                "BFU_OHTTP_RELAY_URL",
                getInstance(context).getString("OHTTP_RELAY_URL", defaultRelay)
            ) ?: defaultRelay
        }
    }

    fun setOhttpGatewayPublicKey(context: Context, keyBase64: String?) {
        getDeviceProtectedPrefs(context).edit().putString("BFU_OHTTP_GATEWAY_PUBKEY", keyBase64?.trim()).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putString("OHTTP_GATEWAY_PUBKEY", keyBase64?.trim()).apply()
        }
    }

    fun getOhttpGatewayPublicKey(context: Context): String? {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getString("BFU_OHTTP_GATEWAY_PUBKEY", null)
        } else {
            getDeviceProtectedPrefs(context).getString(
                "BFU_OHTTP_GATEWAY_PUBKEY",
                getInstance(context).getString("OHTTP_GATEWAY_PUBKEY", null)
            )
        }
    }

    fun setOhttpMasqueradeProfile(context: Context, profile: String) {
        getDeviceProtectedPrefs(context).edit().putString("BFU_OHTTP_MASQUERADE_PROFILE", profile).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putString("OHTTP_MASQUERADE_PROFILE", profile).apply()
        }
    }

    fun getOhttpMasqueradeProfile(context: Context): String {
        val defaultProfile = "google_play_telemetry"
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getString("BFU_OHTTP_MASQUERADE_PROFILE", defaultProfile) ?: defaultProfile
        } else {
            getDeviceProtectedPrefs(context).getString(
                "BFU_OHTTP_MASQUERADE_PROFILE",
                getInstance(context).getString("OHTTP_MASQUERADE_PROFILE", defaultProfile)
            ) ?: defaultProfile
        }
    }

    // =========================================================================
    // 2. BLE/UWB Proximity Key Sharding Engine
    // =========================================================================
    fun setProximityShardingEnabled(context: Context, isEnabled: Boolean) {
        getDeviceProtectedPrefs(context).edit().putBoolean("BFU_PROXIMITY_SHARDING_ENABLED", isEnabled).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putBoolean("PROXIMITY_SHARDING_ENABLED", isEnabled).apply()
        }
    }

    fun isProximityShardingEnabled(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("BFU_PROXIMITY_SHARDING_ENABLED", false)
        } else {
            getDeviceProtectedPrefs(context).getBoolean("BFU_PROXIMITY_SHARDING_ENABLED", false) &&
                    getInstance(context).getBoolean("PROXIMITY_SHARDING_ENABLED", false)
        }
    }

    fun setProximityBleTargetAddress(context: Context, address: String?) {
        getDeviceProtectedPrefs(context).edit().putString("BFU_PROXIMITY_BLE_MAC", address?.trim()?.uppercase()).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putString("PROXIMITY_BLE_MAC", address?.trim()?.uppercase()).apply()
        }
    }

    fun getProximityBleTargetAddress(context: Context): String? {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getString("BFU_PROXIMITY_BLE_MAC", null)
        } else {
            getDeviceProtectedPrefs(context).getString(
                "BFU_PROXIMITY_BLE_MAC",
                getInstance(context).getString("PROXIMITY_BLE_MAC", null)
            )
        }
    }

    fun setProximityRssiThreshold(context: Context, rssiThresholdDbm: Int) {
        getDeviceProtectedPrefs(context).edit().putInt("BFU_PROXIMITY_RSSI_THRESHOLD", rssiThresholdDbm).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putInt("PROXIMITY_RSSI_THRESHOLD", rssiThresholdDbm).apply()
        }
    }

    fun getProximityRssiThreshold(context: Context): Int {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getInt("BFU_PROXIMITY_RSSI_THRESHOLD", -85)
        } else {
            getDeviceProtectedPrefs(context).getInt(
                "BFU_PROXIMITY_RSSI_THRESHOLD",
                getInstance(context).getInt("PROXIMITY_RSSI_THRESHOLD", -85)
            )
        }
    }

    fun setProximityMissedHeartbeatThreshold(context: Context, count: Int) {
        getDeviceProtectedPrefs(context).edit().putInt("BFU_PROXIMITY_BREACH_LIMIT", count).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putInt("PROXIMITY_BREACH_LIMIT", count).apply()
        }
    }

    fun getProximityMissedHeartbeatThreshold(context: Context): Int {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getInt("BFU_PROXIMITY_BREACH_LIMIT", 3)
        } else {
            getDeviceProtectedPrefs(context).getInt(
                "BFU_PROXIMITY_BREACH_LIMIT",
                getInstance(context).getInt("PROXIMITY_BREACH_LIMIT", 3)
            )
        }
    }

    fun setStoredShardA(context: Context, serializedShardA: String) {
        getDeviceProtectedPrefs(context).edit().putString("BFU_SEALED_SHARD_A", serializedShardA).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putString("SEALED_SHARD_A", serializedShardA).apply()
        }
    }

    fun getStoredShardA(context: Context): String? {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getString("BFU_SEALED_SHARD_A", null)
        } else {
            getDeviceProtectedPrefs(context).getString(
                "BFU_SEALED_SHARD_A",
                getInstance(context).getString("SEALED_SHARD_A", null)
            )
        }
    }

    fun clearStoredShardA(context: Context) {
        getDeviceProtectedPrefs(context).edit().remove("BFU_SEALED_SHARD_A").apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().remove("SEALED_SHARD_A").apply()
        }
    }

    // =========================================================================
    // 3. Volatile Memory Hardening & ZRAM Scrubbing
    // =========================================================================
    fun setZramScrubbingEnabled(context: Context, isEnabled: Boolean) {
        getDeviceProtectedPrefs(context).edit().putBoolean("BFU_ZRAM_SCRUBBING_ENABLED", isEnabled).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putBoolean("ZRAM_SCRUBBING_ENABLED", isEnabled).apply()
        }
    }

    fun isZramScrubbingEnabled(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("BFU_ZRAM_SCRUBBING_ENABLED", true)
        } else {
            getDeviceProtectedPrefs(context).getBoolean("BFU_ZRAM_SCRUBBING_ENABLED", true) &&
                    getInstance(context).getBoolean("ZRAM_SCRUBBING_ENABLED", true)
        }
    }

    fun setZramReKeyingEnabled(context: Context, isEnabled: Boolean) {
        getDeviceProtectedPrefs(context).edit().putBoolean("BFU_ZRAM_REKEYING_ENABLED", isEnabled).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putBoolean("ZRAM_REKEYING_ENABLED", isEnabled).apply()
        }
    }

    fun isZramReKeyingEnabled(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("BFU_ZRAM_REKEYING_ENABLED", false)
        } else {
            getDeviceProtectedPrefs(context).getBoolean("BFU_ZRAM_REKEYING_ENABLED", false) &&
                    getInstance(context).getBoolean("ZRAM_REKEYING_ENABLED", false)
        }
    }

    // =========================================================================
    // 4. Plausible Deniability Vault (DNG Container)
    // =========================================================================
    fun setVaultCarrierFileName(context: Context, fileName: String) {
        getDeviceProtectedPrefs(context).edit().putString("BFU_VAULT_CARRIER_FILENAME", fileName.trim()).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putString("VAULT_CARRIER_FILENAME", fileName.trim()).apply()
        }
    }

    fun getVaultCarrierFileName(context: Context): String {
        val defaultName = "RAW_20240812_0042.dng"
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getString("BFU_VAULT_CARRIER_FILENAME", defaultName) ?: defaultName
        } else {
            getInstance(context).getString("VAULT_CARRIER_FILENAME", defaultName) ?: defaultName
        }
    }

    fun setVaultSecretLabel(context: Context, label: String) {
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putString("VAULT_SECRET_LABEL", label.trim()).apply()
        }
    }

    fun getVaultSecretLabel(context: Context): String {
        return if (isUserUnlocked(context)) {
            getInstance(context).getString("VAULT_SECRET_LABEL", "PRIMARY_SECURE_PAYLOAD") ?: "PRIMARY_SECURE_PAYLOAD"
        } else {
            "PRIMARY_SECURE_PAYLOAD"
        }
    }

    // =========================================================================
    // 5. Baseband 2G Hardware Mask & IMSI-Catcher Sentinel
    // =========================================================================
    fun setBasebandSentinelEnabled(context: Context, isEnabled: Boolean) {
        getDeviceProtectedPrefs(context).edit().putBoolean("BFU_BASEBAND_SENTINEL_ENABLED", isEnabled).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putBoolean("BASEBAND_SENTINEL_ENABLED", isEnabled).apply()
        }
    }

    fun isBasebandSentinelEnabled(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("BFU_BASEBAND_SENTINEL_ENABLED", true)
        } else {
            getDeviceProtectedPrefs(context).getBoolean("BFU_BASEBAND_SENTINEL_ENABLED", true) &&
                    getInstance(context).getBoolean("BASEBAND_SENTINEL_ENABLED", true)
        }
    }

    fun setHardware2GDisabled(context: Context, disabled: Boolean) {
        getDeviceProtectedPrefs(context).edit().putBoolean("BFU_HARDWARE_2G_DISABLED", disabled).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putBoolean("HARDWARE_2G_DISABLED", disabled).apply()
        }
    }

    fun isHardware2GDisabled(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("BFU_HARDWARE_2G_DISABLED", true)
        } else {
            getDeviceProtectedPrefs(context).getBoolean("BFU_HARDWARE_2G_DISABLED", true) &&
                    getInstance(context).getBoolean("HARDWARE_2G_DISABLED", true)
        }
    }

    fun setTimingAdvanceThreshold(context: Context, maxTA: Int) {
        getDeviceProtectedPrefs(context).edit().putInt("BFU_TIMING_ADVANCE_MAX", maxTA).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putInt("TIMING_ADVANCE_MAX", maxTA).apply()
        }
    }

    fun getTimingAdvanceThreshold(context: Context): Int {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getInt("BFU_TIMING_ADVANCE_MAX", 30)
        } else {
            getDeviceProtectedPrefs(context).getInt(
                "BFU_TIMING_ADVANCE_MAX",
                getInstance(context).getInt("TIMING_ADVANCE_MAX", 30)
            )
        }
    }

    // =========================================================================
    // 6. Spectral Collapse & Faraday Bag Sentinels
    // =========================================================================
    fun setSpectralSentinelEnabled(context: Context, isEnabled: Boolean) {
        getDeviceProtectedPrefs(context).edit().putBoolean("BFU_SPECTRAL_SENTINEL_ENABLED", isEnabled).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putBoolean("SPECTRAL_SENTINEL_ENABLED", isEnabled).apply()
        }
    }

    fun isSpectralSentinelEnabled(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("BFU_SPECTRAL_SENTINEL_ENABLED", true)
        } else {
            getDeviceProtectedPrefs(context).getBoolean("BFU_SPECTRAL_SENTINEL_ENABLED", true) &&
                    getInstance(context).getBoolean("SPECTRAL_SENTINEL_ENABLED", true)
        }
    }

    fun setSpectralQuarantineMs(context: Context, ms: Long) {
        getDeviceProtectedPrefs(context).edit().putLong("BFU_SPECTRAL_QUARANTINE_MS", ms).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putLong("SPECTRAL_QUARANTINE_MS", ms).apply()
        }
    }

    fun getSpectralQuarantineMs(context: Context): Long {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getLong("BFU_SPECTRAL_QUARANTINE_MS", 4000L)
        } else {
            getDeviceProtectedPrefs(context).getLong(
                "BFU_SPECTRAL_QUARANTINE_MS",
                getInstance(context).getLong("SPECTRAL_QUARANTINE_MS", 4000L)
            )
        }
    }

    fun setSpectralMotionRequired(context: Context, required: Boolean) {
        getDeviceProtectedPrefs(context).edit().putBoolean("BFU_SPECTRAL_MOTION_REQUIRED", required).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putBoolean("SPECTRAL_MOTION_REQUIRED", required).apply()
        }
    }

    fun isSpectralMotionRequired(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("BFU_SPECTRAL_MOTION_REQUIRED", true)
        } else {
            getDeviceProtectedPrefs(context).getBoolean("BFU_SPECTRAL_MOTION_REQUIRED", true) &&
                    getInstance(context).getBoolean("SPECTRAL_MOTION_REQUIRED", true)
        }
    }

    fun setFaradayBlackoutEnabled(context: Context, isEnabled: Boolean) {
        getDeviceProtectedPrefs(context).edit().putBoolean("BFU_FARADAY_BLACKOUT_ENABLED", isEnabled).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putBoolean("FARADAY_BLACKOUT_ENABLED", isEnabled).apply()
        }
    }

    fun isFaradayBlackoutEnabled(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("BFU_FARADAY_BLACKOUT_ENABLED", true)
        } else {
            getDeviceProtectedPrefs(context).getBoolean("BFU_FARADAY_BLACKOUT_ENABLED", true) &&
                    getInstance(context).getBoolean("FARADAY_BLACKOUT_ENABLED", true)
        }
    }

    fun setFaradayBlackoutDurationHours(context: Context, hours: Int) {
        getDeviceProtectedPrefs(context).edit().putInt("BFU_FARADAY_DURATION_HOURS", hours).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putInt("FARADAY_DURATION_HOURS", hours).apply()
        }
    }

    fun getFaradayBlackoutDurationHours(context: Context): Int {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getInt("BFU_FARADAY_DURATION_HOURS", 3)
        } else {
            getDeviceProtectedPrefs(context).getInt(
                "BFU_FARADAY_DURATION_HOURS",
                getInstance(context).getInt("FARADAY_DURATION_HOURS", 3)
            )
        }
    }

    // =========================================================================
    // 7. PMIC Battery Micro-Telemetry & Anti-Disassembly Tripwire
    // =========================================================================
    fun setPmicTamperEnabled(context: Context, isEnabled: Boolean) {
        getDeviceProtectedPrefs(context).edit().putBoolean("BFU_PMIC_TAMPER_ENABLED", isEnabled).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putBoolean("PMIC_TAMPER_ENABLED", isEnabled).apply()
        }
    }

    fun isPmicTamperEnabled(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("BFU_PMIC_TAMPER_ENABLED", true)
        } else {
            getDeviceProtectedPrefs(context).getBoolean("BFU_PMIC_TAMPER_ENABLED", true) &&
                    getInstance(context).getBoolean("PMIC_TAMPER_ENABLED", true)
        }
    }

    fun setPmicImpedanceDeltaThreshold(context: Context, deltaUohm: Long) {
        getDeviceProtectedPrefs(context).edit().putLong("BFU_PMIC_IMPEDANCE_DELTA", deltaUohm).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putLong("PMIC_IMPEDANCE_DELTA", deltaUohm).apply()
        }
    }

    fun getPmicImpedanceDeltaThreshold(context: Context): Long {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getLong("BFU_PMIC_IMPEDANCE_DELTA", 35000L)
        } else {
            getDeviceProtectedPrefs(context).getLong(
                "BFU_PMIC_IMPEDANCE_DELTA",
                getInstance(context).getLong("PMIC_IMPEDANCE_DELTA", 35000L)
            )
        }
    }

    fun setPmicThermalShockDelta(context: Context, deltaTenthsCelsius: Long) {
        getDeviceProtectedPrefs(context).edit().putLong("BFU_PMIC_THERMAL_DELTA", deltaTenthsCelsius).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putLong("PMIC_THERMAL_DELTA", deltaTenthsCelsius).apply()
        }
    }

    fun getPmicThermalShockDelta(context: Context): Long {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getLong("BFU_PMIC_THERMAL_DELTA", 120L)
        } else {
            getDeviceProtectedPrefs(context).getLong(
                "BFU_PMIC_THERMAL_DELTA",
                getInstance(context).getLong("PMIC_THERMAL_DELTA", 120L)
            )
        }
    }

    // =========================================================================
    // 8. Physical USB Gadget Controller Tripwire
    // =========================================================================
    fun setUsbTripwireEnabled(context: Context, isEnabled: Boolean) {
        getDeviceProtectedPrefs(context).edit().putBoolean("BFU_USB_TRIPWIRE_ENABLED", isEnabled).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putBoolean("USB_TRIPWIRE_ENABLED", isEnabled).apply()
        }
    }

    fun isUsbTripwireEnabled(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("BFU_USB_TRIPWIRE_ENABLED", false)
        } else {
            getDeviceProtectedPrefs(context).getBoolean("BFU_USB_TRIPWIRE_ENABLED", false) ||
                    getInstance(context).getBoolean("USB_TRIPWIRE_ENABLED", false)
        }
    }

    fun setUsbRequiredConsecutiveHits(context: Context, hits: Int) {
        getDeviceProtectedPrefs(context).edit().putInt("BFU_USB_REQUIRED_HITS", hits).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putInt("USB_REQUIRED_HITS", hits).apply()
        }
    }

    fun getUsbRequiredConsecutiveHits(context: Context): Int {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getInt("BFU_USB_REQUIRED_HITS", 2)
        } else {
            getDeviceProtectedPrefs(context).getInt(
                "BFU_USB_REQUIRED_HITS",
                getInstance(context).getInt("USB_REQUIRED_HITS", 2)
            )
        }
    }

    // =========================================================================
    // 9. Event Logging
    // =========================================================================
    fun logEvent(context: Context, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val newEntry = "$timestamp - $message"

        val prefs = getDeviceProtectedPrefs(context)
        val existingLogs = prefs.getStringSet(EVENT_LOG_KEY, mutableSetOf())?.toMutableList() ?: mutableListOf()

        existingLogs.add(0, newEntry)

        while (existingLogs.size > MAX_LOG_ENTRIES) {
            existingLogs.removeAt(existingLogs.size - 1)
        }

        prefs.edit().putStringSet(EVENT_LOG_KEY, existingLogs.toSet()).apply()
    }

    fun getLogs(context: Context): List<String> {
        return getDeviceProtectedPrefs(context).getStringSet(EVENT_LOG_KEY, setOf())?.sortedDescending() ?: emptyList()
    }

    fun clearLogs(context: Context) {
        getDeviceProtectedPrefs(context).edit().remove(EVENT_LOG_KEY).apply()
    }

    // =========================================================================
    // 10. Core Protection & Maintenance Mode
    // =========================================================================
    fun setProtectionEnabled(context: Context, isEnabled: Boolean) {
        getDeviceProtectedPrefs(context).edit().putBoolean("PROTECTION_ENABLED", isEnabled).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putBoolean("PROTECTION_ENABLED", isEnabled).apply()
        }
    }

    fun isProtectionEnabled(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("PROTECTION_ENABLED", true)
        } else {
            getInstance(context).getBoolean("PROTECTION_ENABLED", true)
        }
    }

    fun setMaintenanceMode(context: Context, isEnabled: Boolean) {
        getDeviceProtectedPrefs(context).edit().putBoolean("MAINTENANCE_MODE", isEnabled).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putBoolean("MAINTENANCE_MODE", isEnabled).apply()
        }
    }

    fun isMaintenanceMode(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("MAINTENANCE_MODE", false)
        } else {
            getInstance(context).getBoolean("MAINTENANCE_MODE", false)
        }
    }

    // =========================================================================
    // 11. Authentication, Decoy Users & Multi-User Honeypot
    // =========================================================================
    fun setNormalPin(context: Context, pin: String) {
        getInstance(context).edit().putString("NORMAL_PIN", pin).apply()
    }

    fun getNormalPin(context: Context): String? =
        getInstance(context).getString("NORMAL_PIN", null)

    fun setDuressPin(context: Context, pin: String) {
        getInstance(context).edit().putString("DURESS_PIN", pin).apply()
        getDeviceProtectedPrefs(context).edit().putString("BFU_DURESS_PIN", pin).apply()
        syncHookCredentials(context)
    }

    fun getDuressPin(context: Context): String? {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getString("BFU_DURESS_PIN", null)
        } else {
            getInstance(context).getString("DURESS_PIN", null)
        }
    }

    fun setWipePin(context: Context, pin: String) {
        getInstance(context).edit().putString("WIPE_PIN", pin).apply()
        getDeviceProtectedPrefs(context).edit().putString("BFU_WIPE_PIN", pin).apply()
        syncHookCredentials(context)
    }

    fun getWipePin(context: Context): String? {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getString("BFU_WIPE_PIN", null)
        } else {
            getInstance(context).getString("WIPE_PIN", null)
        }
    }

    fun setHoneypotPin(context: Context, pin: String) {
        getInstance(context).edit().putString("HONEYPOT_PIN", pin).apply()
        getDeviceProtectedPrefs(context).edit().putString("BFU_HONEYPOT_PIN", pin).apply()
        syncHookCredentials(context)
    }

    fun getHoneypotPin(context: Context): String? {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getString("BFU_HONEYPOT_PIN", null)
        } else {
            getInstance(context).getString("HONEYPOT_PIN", null)
        }
    }

    fun setDecoyUserId(context: Context, userId: Int) {
        getInstance(context).edit().putInt("DECOY_USER_ID", userId).apply()
        getDeviceProtectedPrefs(context).edit().putInt("BFU_DECOY_USER_ID", userId).apply()
        syncHookCredentials(context)
    }

    fun getDecoyUserId(context: Context): Int {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getInt("BFU_DECOY_USER_ID", -1)
        } else {
            getInstance(context).getInt("DECOY_USER_ID", -1)
        }
    }

    fun addHoneypotIntel(context: Context, info: String) {
        val current = getInstance(context).getStringSet("HONEYPOT_INTEL", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        current.add("${System.currentTimeMillis()}: $info")
        getInstance(context).edit().putStringSet("HONEYPOT_INTEL", current).apply()
    }

    fun getHoneypotIntel(context: Context): Set<String> =
        getInstance(context).getStringSet("HONEYPOT_INTEL", emptySet()) ?: emptySet()

    fun clearHoneypotIntel(context: Context) =
        getInstance(context).edit().remove("HONEYPOT_INTEL").apply()

    fun syncHookCredentials(context: Context) {
        val appContext = context.applicationContext
        val wipePin = getWipePin(appContext)
        val duressPin = getDuressPin(appContext)
        val honeypotPin = getHoneypotPin(appContext)
        val decoyUserId = getDecoyUserId(appContext)

        CoroutineScope(Dispatchers.IO).launch {
            CredentialBridge.syncCredentials(appContext, wipePin, duressPin, honeypotPin, decoyUserId)
        }
    }

    fun clearAllPins(context: Context) {
        getInstance(context).edit()
            .remove("NORMAL_PIN")
            .remove("DURESS_PIN")
            .remove("WIPE_PIN")
            .remove("HONEYPOT_PIN")
            .remove("DECOY_USER_ID")
            .apply()

        getDeviceProtectedPrefs(context).edit()
            .remove("BFU_DURESS_PIN")
            .remove("BFU_WIPE_PIN")
            .remove("BFU_HONEYPOT_PIN")
            .remove("BFU_DECOY_USER_ID")
            .apply()

        CoroutineScope(Dispatchers.IO).launch {
            CredentialBridge.clearCredentials(context.applicationContext)
        }
    }

    fun getFailedAttempts(context: Context): Int =
        getDeviceProtectedPrefs(context).getInt("FAILED_ATTEMPTS", 0)

    fun incrementFailedAttempts(context: Context) {
        val current = getFailedAttempts(context)
        getDeviceProtectedPrefs(context).edit().putInt("FAILED_ATTEMPTS", current + 1).apply()
    }

    fun resetFailedAttempts(context: Context) =
        getDeviceProtectedPrefs(context).edit().putInt("FAILED_ATTEMPTS", 0).apply()

    // =========================================================================
    // 12. Remote Controls & Telephony
    // =========================================================================
    fun setEmergencyContact(context: Context, contact: String) {
        getDeviceProtectedPrefs(context).edit().putString("BFU_EMERGENCY_CONTACT", contact.trim()).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putString("EMERGENCY_CONTACT", contact.trim()).apply()
        }
    }

    fun getEmergencyContact(context: Context): String? {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getString("BFU_EMERGENCY_CONTACT", null)
        } else {
            getDeviceProtectedPrefs(context).getString(
                "BFU_EMERGENCY_CONTACT",
                getInstance(context).getString("EMERGENCY_CONTACT", null)
            )
        }
    }

    fun setSmsMasterPassword(context: Context, password: String) {
        getInstance(context).edit().putString("SMS_MASTER_PASSWORD", password).apply()
        getDeviceProtectedPrefs(context).edit().putString("BFU_SMS_MASTER_PASSWORD", password).apply()
    }

    fun getSmsMasterPassword(context: Context): String? {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getString("BFU_SMS_MASTER_PASSWORD", null)
        } else {
            getInstance(context).getString("SMS_MASTER_PASSWORD", null)
        }
    }

    fun setRemoteInstallCode(context: Context, code: String) =
        getInstance(context).edit().putString("INSTALL_CODE", code).apply()

    fun getRemoteInstallCode(context: Context): String? =
        getInstance(context).getString("INSTALL_CODE", null)

    // =========================================================================
    // 13. Panic Features, Media & Environmental Triggers
    // =========================================================================
    fun setRecordVideoEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("RECORD_VIDEO", isEnabled).apply()

    fun isRecordVideoEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("RECORD_VIDEO", false)

    fun setAmbientAudioEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("AMBIENT_AUDIO_ENABLED", isEnabled).apply()

    fun isAmbientAudioEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("AMBIENT_AUDIO_ENABLED", false)

    fun setWipeDeviceEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("WIPE_DEVICE", isEnabled).apply()

    fun isWipeDeviceEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("WIPE_DEVICE", false)

    fun setIntruderSelfieEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("INTRUDER_SELFIE", isEnabled).apply()

    fun isIntruderSelfieEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("INTRUDER_SELFIE", false)

    fun setSaveSelfieToStorage(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("SAVE_SELFIE_TO_STORAGE", isEnabled).apply()

    fun isSaveSelfieToStorageEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("SAVE_SELFIE_TO_STORAGE", false)

    fun setSimChangeAlertEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("SIM_CHANGE", isEnabled).apply()

    fun isSimChangeAlertEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("SIM_CHANGE", false)

    fun setInitialSimSerial(context: Context, serial: String?) {
        getInstance(context).edit().putString("SIM_SERIAL", serial).apply()
        getDeviceProtectedPrefs(context).edit().putString("BFU_SIM_SERIAL", serial).apply()
    }

    fun getInitialSimSerial(context: Context): String? {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getString("BFU_SIM_SERIAL", null)
        } else {
            getInstance(context).getString("SIM_SERIAL", null)
        }
    }

    fun setShakeToPanicEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("SHAKE_TO_PANIC", isEnabled).apply()

    fun isShakeToPanicEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("SHAKE_TO_PANIC", false)

    fun setShakeSensitivity(context: Context, level: Int) =
        getInstance(context).edit().putInt("SHAKE_SENSITIVITY", level).apply()

    fun getShakeSensitivity(context: Context): Int =
        getInstance(context).getInt("SHAKE_SENSITIVITY", 3)

    fun setHardwareWipeEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("HARDWARE_WIPE_ENABLED", isEnabled).apply()

    fun isHardwareWipeEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("HARDWARE_WIPE_ENABLED", false)

    // =========================================================================
    // 14. Geographic Suicide & Custom Wipe Zones
    // =========================================================================
    fun setGeofenceSuicideEnabled(context: Context, isEnabled: Boolean) {
        getInstance(context).edit().putBoolean("GEOFENCE_SUICIDE_ENABLED", isEnabled).apply()
        getDeviceProtectedPrefs(context).edit().putBoolean("BFU_GEOFENCE_SUICIDE_ENABLED", isEnabled).apply()
    }

    fun isGeofenceSuicideEnabled(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("BFU_GEOFENCE_SUICIDE_ENABLED", false)
        } else {
            getInstance(context).getBoolean("GEOFENCE_SUICIDE_ENABLED", false)
        }
    }

    fun setGeofenceEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("GEOFENCE_ENABLED", isEnabled).apply()

    fun isGeofenceEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("GEOFENCE_ENABLED", false)

    fun setGeofenceLocation(context: Context, lat: Double, lon: Double) {
        getInstance(context).edit()
            .putLong("GEOFENCE_LAT", lat.toRawBits())
            .putLong("GEOFENCE_LON", lon.toRawBits())
            .apply()
    }

    fun getGeofenceLocation(context: Context): Pair<Double, Double>? {
        val prefs = getInstance(context)
        if (!prefs.contains("GEOFENCE_LAT") || !prefs.contains("GEOFENCE_LON")) return null
        val lat = Double.fromBits(prefs.getLong("GEOFENCE_LAT", 0))
        val lon = Double.fromBits(prefs.getLong("GEOFENCE_LON", 0))
        return lat to lon
    }

    fun getCustomWipeZones(context: Context): List<PolygonUtils.WipeZone> {
        val json = if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getString(CUSTOM_WIPE_ZONES_KEY, "") ?: ""
        } else {
            getInstance(context).getString(CUSTOM_WIPE_ZONES_KEY, "") ?: ""
        }
        return PolygonUtils.deserializeZones(json)
    }

    fun saveCustomWipeZones(context: Context, zones: List<PolygonUtils.WipeZone>) {
        val json = PolygonUtils.serializeZones(zones)
        getDeviceProtectedPrefs(context).edit().putString(CUSTOM_WIPE_ZONES_KEY, json).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putString(CUSTOM_WIPE_ZONES_KEY, json).apply()
        }
    }

    fun addCustomWipeZone(context: Context, zone: PolygonUtils.WipeZone) {
        val current = getCustomWipeZones(context).toMutableList()
        current.add(zone)
        saveCustomWipeZones(context, current)
    }

    fun removeCustomWipeZone(context: Context, zoneId: String) {
        val current = getCustomWipeZones(context).filter { it.id != zoneId }
        saveCustomWipeZones(context, current)
    }

    // =========================================================================
    // 15. Stealth Mode & In-App Security Settings
    // =========================================================================
    fun setAppHidden(context: Context, isHidden: Boolean) =
        getInstance(context).edit().putBoolean("APP_HIDDEN", isHidden).apply()

    fun isAppHidden(context: Context): Boolean =
        getInstance(context).getBoolean("APP_HIDDEN", false)

    fun setSecretDialerCode(context: Context, code: String) =
        getInstance(context).edit().putString("SECRET_DIALER_CODE", code).apply()

    fun getSecretDialerCode(context: Context): String? =
        getInstance(context).getString("SECRET_DIALER_CODE", null)

    fun setBiometricLockEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("BIOMETRIC_LOCK_ENABLED", isEnabled).apply()

    fun isBiometricLockEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("BIOMETRIC_LOCK_ENABLED", false)

    fun setTrustedVpnEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("TRUSTED_VPN_ENABLED", isEnabled).apply()

    fun isTrustedVpnEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("TRUSTED_VPN_ENABLED", false)

    // =========================================================================
    // 16. Dead-Man & Watchdog Timers
    // =========================================================================
    fun setWatchdogModeEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("WATCHDOG_ENABLED", isEnabled).apply()

    fun isWatchdogModeEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("WATCHDOG_ENABLED", false)

    fun setWatchdogInterval(context: Context, intervalMinutes: Int) =
        getInstance(context).edit().putInt("WATCHDOG_INTERVAL", intervalMinutes).apply()

    fun getWatchdogInterval(context: Context): Int =
        getInstance(context).getInt("WATCHDOG_INTERVAL", 30)

    fun setTripwireEnabled(context: Context, isEnabled: Boolean) {
        getDeviceProtectedPrefs(context).edit().putBoolean("BFU_TRIPWIRE_ENABLED", isEnabled).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putBoolean("TRIPWIRE_ENABLED", isEnabled).apply()
        }
    }

    fun isTripwireEnabled(context: Context): Boolean {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getBoolean("BFU_TRIPWIRE_ENABLED", false)
        } else {
            getDeviceProtectedPrefs(context).getBoolean("BFU_TRIPWIRE_ENABLED", false) ||
                    getInstance(context).getBoolean("TRIPWIRE_ENABLED", false)
        }
    }

    fun setTripwireDuration(context: Context, durationHours: Int) {
        getDeviceProtectedPrefs(context).edit().putInt("BFU_TRIPWIRE_DURATION", durationHours).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putInt("TRIPWIRE_DURATION", durationHours).apply()
        }
    }

    fun getTripwireDuration(context: Context): Int {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getInt("BFU_TRIPWIRE_DURATION", 24)
        } else {
            getDeviceProtectedPrefs(context).getInt(
                "BFU_TRIPWIRE_DURATION",
                getInstance(context).getInt("TRIPWIRE_DURATION", 24)
            )
        }
    }

    fun setLastTripwireCheckIn(context: Context, timestamp: Long) {
        getDeviceProtectedPrefs(context).edit().putLong("BFU_TRIPWIRE_LAST_CHECKIN", timestamp).apply()
        if (isUserUnlocked(context)) {
            getInstance(context).edit().putLong("TRIPWIRE_LAST_CHECKIN", timestamp).apply()
        }
    }

    fun getLastTripwireCheckIn(context: Context): Long {
        return if (!isUserUnlocked(context)) {
            getDeviceProtectedPrefs(context).getLong("BFU_TRIPWIRE_LAST_CHECKIN", 0L)
        } else {
            val deTimestamp = getDeviceProtectedPrefs(context).getLong("BFU_TRIPWIRE_LAST_CHECKIN", 0L)
            if (deTimestamp > 0L) deTimestamp else getInstance(context).getLong("TRIPWIRE_LAST_CHECKIN", 0L)
        }
    }

    // =========================================================================
    // 17. Root & Advanced Defense Profiles
    // =========================================================================
    fun setGpsSpoofingEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("ROOT_GPS_SPOOFING", isEnabled).apply()

    fun isGpsSpoofingEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("ROOT_GPS_SPOOFING", false)

    fun setDecoyGpsLocation(context: Context, location: String) =
        getInstance(context).edit().putString("ROOT_DECOY_GPS_LOCATION", location).apply()

    fun getDecoyGpsLocation(context: Context): String? =
        getInstance(context).getString("ROOT_DECOY_GPS_LOCATION", null)

    fun setSilentInstallEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("ROOT_SILENT_INSTALL", isEnabled).apply()

    fun isSilentInstallEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("ROOT_SILENT_INSTALL", false)

    fun setRemoteApkUrl(context: Context, url: String) =
        getInstance(context).edit().putString("ROOT_REMOTE_APK_URL", url).apply()

    fun getRemoteApkUrl(context: Context): String? =
        getInstance(context).getString("ROOT_REMOTE_APK_URL", null)

    fun setFirewallTripwireEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("ROOT_FIREWALL_TRIPWIRE", isEnabled).apply()

    fun isFirewallTripwireEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("ROOT_FIREWIRE_TRIPWIRE", false)

    fun setSecureWipeEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("ROOT_SECURE_WIPE", isEnabled).apply()

    fun isSecureWipeEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("ROOT_SECURE_WIPE", false)

    fun setSystemAppEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("ROOT_SYSTEM_APP", isEnabled).apply()

    fun isSystemAppEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("ROOT_SYSTEM_APP", false)

    fun setUnkillableServiceEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("ROOT_UNKILLABLE_SERVICE", isEnabled).apply()

    fun isUnkillableServiceEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("ROOT_UNKILLABLE_SERVICE", false)

    fun setProcessHiddenEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("ROOT_PROCESS_HIDDEN", isEnabled).apply()

    fun isProcessHiddenEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("ROOT_PROCESS_HIDDEN", false)

    fun setStealthScreenshotEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("ROOT_STEALTH_SCREENSHOT", isEnabled).apply()

    fun isStealthScreenshotEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("ROOT_STEALTH_SCREENSHOT", false)

    fun setKeyloggerEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("ROOT_KEYLOGGER", isEnabled).apply()

    fun isKeyloggerEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("ROOT_KEYLOGGER", false)

    fun setStealthMediaCaptureEnabled(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("ROOT_STEALTH_MEDIA", isEnabled).apply()

    fun isStealthMediaCaptureEnabled(context: Context): Boolean =
        getInstance(context).getBoolean("ROOT_STEALTH_MEDIA", false)

    fun appendKeylogData(context: Context, data: String) {
        val currentLogs = getKeylogData(context)
        getInstance(context).edit().putString("KEYLOG_DATA", currentLogs + data).apply()
    }

    fun getKeylogData(context: Context): String =
        getInstance(context).getString("KEYLOG_DATA", "") ?: ""

    fun clearKeylogData(context: Context) =
        getInstance(context).edit().remove("KEYLOG_DATA").apply()

    // =========================================================================
    // 18. SMTP Email Alert Configuration
    // =========================================================================
    fun setEmailHost(context: Context, host: String) =
        getInstance(context).edit().putString("EMAIL_HOST", host).apply()

    fun getEmailHost(context: Context): String? =
        getInstance(context).getString("EMAIL_HOST", null)

    fun setEmailPort(context: Context, port: Int) =
        getInstance(context).edit().putInt("EMAIL_PORT", port).apply()

    fun getEmailPort(context: Context): Int =
        getInstance(context).getInt("EMAIL_PORT", 0)

    fun setEmailUsername(context: Context, username: String) =
        getInstance(context).edit().putString("EMAIL_USERNAME", username).apply()

    fun getEmailUsername(context: Context): String? =
        getInstance(context).getString("EMAIL_USERNAME", null)

    fun setEmailPassword(context: Context, password: String) =
        getInstance(context).edit().putString("EMAIL_PASSWORD", password).apply()

    fun getEmailPassword(context: Context): String? =
        getInstance(context).getString("EMAIL_PASSWORD", null)

    fun setEnableSslTls(context: Context, isEnabled: Boolean) =
        getInstance(context).edit().putBoolean("EMAIL_SSL_TLS", isEnabled).apply()

    fun isEnableSslTls(context: Context): Boolean =
        getInstance(context).getBoolean("EMAIL_SSL_TLS", true)
}

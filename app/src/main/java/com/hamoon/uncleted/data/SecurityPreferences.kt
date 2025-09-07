package com.hamoon.uncleted.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.text.SimpleDateFormat
import java.util.*

/**
 * A secure singleton object for managing all persistent application settings.
 *
 * --- PERFORMANCE & COMPILATION FIX ---
 * - The SharedPreferences instance is now cached (singleton pattern) to prevent slow re-initialization.
 * - Uses the modern, non-deprecated `MasterKey.Builder`.
 * - Event logging logic is now handled internally to fix compilation errors and improve encapsulation.
 * - ### FIX: getInstance() changed from private to internal to be accessible within the module. ###
 */
object SecurityPreferences {

    @Volatile
    private var instance: SharedPreferences? = null
    private val LOCK = Any()
    private const val PREFS_FILE_NAME = "secure_app_prefs"
    private const val EVENT_LOG_KEY = "event_log"
    private const val MAX_LOG_ENTRIES = 100

    // ### FIX: Changed from 'private' to 'internal' ###
    // This allows other classes in the 'app' module to get the SharedPreferences instance.
    internal fun getInstance(context: Context): SharedPreferences {
        return instance ?: synchronized(LOCK) {
            instance ?: createEncryptedPrefs(context.applicationContext).also {
                instance = it
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        // Use the modern, non-deprecated MasterKey.Builder
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

    // --- Event Logging (Moved from EventLogger) ---
    fun logEvent(context: Context, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val newEntry = "$timestamp - $message"

        val prefs = getInstance(context)
        val existingLogs = prefs.getStringSet(EVENT_LOG_KEY, mutableSetOf())?.toMutableList() ?: mutableListOf()

        existingLogs.add(0, newEntry) // Add to the top

        // Trim old logs
        while (existingLogs.size > MAX_LOG_ENTRIES) {
            existingLogs.removeAt(existingLogs.size - 1)
        }

        prefs.edit().putStringSet(EVENT_LOG_KEY, existingLogs.toSet()).apply()
    }

    fun getLogs(context: Context): List<String> {
        return getInstance(context).getStringSet(EVENT_LOG_KEY, setOf())?.sortedDescending() ?: emptyList()
    }


    // --- Core Protection ---
    // REVISED: Protection is now always enabled. The master switch has been removed from the UI.
    // The setter is a no-op, and the getter always returns true to ensure
    // all other parts of the app that rely on this flag behave correctly.
    fun setProtectionEnabled(context: Context, isEnabled: Boolean) { /* No-op */ }
    fun isProtectionEnabled(context: Context): Boolean = true

    fun setMaintenanceMode(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("MAINTENANCE_MODE", isEnabled).apply()
    fun isMaintenanceMode(context: Context): Boolean = getInstance(context).getBoolean("MAINTENANCE_MODE", false)

    // --- Authentication ---
    fun setNormalPin(context: Context, pin: String) = getInstance(context).edit().putString("NORMAL_PIN", pin).apply()
    fun getNormalPin(context: Context): String? = getInstance(context).getString("NORMAL_PIN", null)

    fun setDuressPin(context: Context, pin: String) = getInstance(context).edit().putString("DURESS_PIN", pin).apply()
    fun getDuressPin(context: Context): String? = getInstance(context).getString("DURESS_PIN", null)

    fun setWipePin(context: Context, pin: String) = getInstance(context).edit().putString("WIPE_PIN", pin).apply()
    fun getWipePin(context: Context): String? = getInstance(context).getString("WIPE_PIN", null)

    fun getFailedAttempts(context: Context): Int = getInstance(context).getInt("FAILED_ATTEMPTS", 0)
    fun incrementFailedAttempts(context: Context) {
        val current = getFailedAttempts(context)
        getInstance(context).edit().putInt("FAILED_ATTEMPTS", current + 1).apply()
    }
    fun resetFailedAttempts(context: Context) = getInstance(context).edit().putInt("FAILED_ATTEMPTS", 0).apply()

    // --- Remote Control ---
    fun setEmergencyContact(context: Context, contact: String) = getInstance(context).edit().putString("EMERGENCY_CONTACT", contact).apply()
    fun getEmergencyContact(context: Context): String? = getInstance(context).getString("EMERGENCY_CONTACT", null)

    fun setSmsMasterPassword(context: Context, password: String) = getInstance(context).edit().putString("SMS_MASTER_PASSWORD", password).apply()
    fun getSmsMasterPassword(context: Context): String? = getInstance(context).getString("SMS_MASTER_PASSWORD", null)

    fun setRemoteInstallCode(context: Context, code: String) = getInstance(context).edit().putString("INSTALL_CODE", code).apply()
    fun getRemoteInstallCode(context: Context): String? = getInstance(context).getString("INSTALL_CODE", null)


    // --- Panic Features ---
    fun setRecordVideoEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("RECORD_VIDEO", isEnabled).apply()
    fun isRecordVideoEnabled(context: Context): Boolean = getInstance(context).getBoolean("RECORD_VIDEO", false)

    fun setAmbientAudioEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("AMBIENT_AUDIO_ENABLED", isEnabled).apply()
    fun isAmbientAudioEnabled(context: Context): Boolean = getInstance(context).getBoolean("AMBIENT_AUDIO_ENABLED", false)

    fun setWipeDeviceEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("WIPE_DEVICE", isEnabled).apply()
    fun isWipeDeviceEnabled(context: Context): Boolean = getInstance(context).getBoolean("WIPE_DEVICE", false)

    fun setIntruderSelfieEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("INTRUDER_SELFIE", isEnabled).apply()
    fun isIntruderSelfieEnabled(context: Context): Boolean = getInstance(context).getBoolean("INTRUDER_SELFIE", false)

    fun setSaveSelfieToStorage(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("SAVE_SELFIE_TO_STORAGE", isEnabled).apply()
    fun isSaveSelfieToStorageEnabled(context: Context): Boolean = getInstance(context).getBoolean("SAVE_SELFIE_TO_STORAGE", false)

    fun setSimChangeAlertEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("SIM_CHANGE", isEnabled).apply()
    fun isSimChangeAlertEnabled(context: Context): Boolean = getInstance(context).getBoolean("SIM_CHANGE", false)

    fun setInitialSimSerial(context: Context, serial: String?) = getInstance(context).edit().putString("SIM_SERIAL", serial).apply()
    fun getInitialSimSerial(context: Context): String? = getInstance(context).getString("SIM_SERIAL", null)

    fun setFakeShutdownEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("FAKE_SHUTDOWN", isEnabled).apply()
    fun isFakeShutdownEnabled(context: Context): Boolean = getInstance(context).getBoolean("FAKE_SHUTDOWN", false)

    fun setShakeToPanicEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("SHAKE_TO_PANIC", isEnabled).apply()
    fun isShakeToPanicEnabled(context: Context): Boolean = getInstance(context).getBoolean("SHAKE_TO_PANIC", false)

    fun setShakeSensitivity(context: Context, level: Int) = getInstance(context).edit().putInt("SHAKE_SENSITIVITY", level).apply()
    fun getShakeSensitivity(context: Context): Int = getInstance(context).getInt("SHAKE_SENSITIVITY", 3) // Default middle sensitivity

    // --- Stealth Mode ---
    fun setAppHidden(context: Context, isHidden: Boolean) = getInstance(context).edit().putBoolean("APP_HIDDEN", isHidden).apply()
    fun isAppHidden(context: Context): Boolean = getInstance(context).getBoolean("APP_HIDDEN", false)

    fun setSecretDialerCode(context: Context, code: String) = getInstance(context).edit().putString("SECRET_DIALER_CODE", code).apply()
    fun getSecretDialerCode(context: Context): String? = getInstance(context).getString("SECRET_DIALER_CODE", null)

    // --- Advanced Automated Features ---
    fun setWatchdogModeEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("WATCHDOG_ENABLED", isEnabled).apply()
    fun isWatchdogModeEnabled(context: Context): Boolean = getInstance(context).getBoolean("WATCHDOG_ENABLED", false)

    fun setWatchdogInterval(context: Context, intervalMinutes: Int) = getInstance(context).edit().putInt("WATCHDOG_INTERVAL", intervalMinutes).apply()
    fun getWatchdogInterval(context: Context): Int = getInstance(context).getInt("WATCHDOG_INTERVAL", 30) // Default 30 minutes

    fun setTripwireEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("TRIPWIRE_ENABLED", isEnabled).apply()
    fun isTripwireEnabled(context: Context): Boolean = getInstance(context).getBoolean("TRIPWIRE_ENABLED", false)

    fun setTripwireDuration(context: Context, durationHours: Int) = getInstance(context).edit().putInt("TRIPWIRE_DURATION", durationHours).apply()
    fun getTripwireDuration(context: Context): Int = getInstance(context).getInt("TRIPWIRE_DURATION", 24) // Default 24 hours

    fun setLastTripwireCheckIn(context: Context, timestamp: Long) = getInstance(context).edit().putLong("TRIPWIRE_LAST_CHECKIN", timestamp).apply()
    fun getLastTripwireCheckIn(context: Context): Long = getInstance(context).getLong("TRIPWIRE_LAST_CHECKIN", 0L)

    fun setGeofenceEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("GEOFENCE_ENABLED", isEnabled).apply()
    fun isGeofenceEnabled(context: Context): Boolean = getInstance(context).getBoolean("GEOFENCE_ENABLED", false)

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

    // --- ROOT-ONLY FEATURES ---
    fun setGpsSpoofingEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("ROOT_GPS_SPOOFING", isEnabled).apply()
    fun isGpsSpoofingEnabled(context: Context): Boolean = getInstance(context).getBoolean("ROOT_GPS_SPOOFING", false)

    fun setDecoyGpsLocation(context: Context, location: String) = getInstance(context).edit().putString("ROOT_DECOY_GPS_LOCATION", location).apply()
    fun getDecoyGpsLocation(context: Context): String? = getInstance(context).getString("ROOT_DECOY_GPS_LOCATION", null)

    fun setSilentInstallEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("ROOT_SILENT_INSTALL", isEnabled).apply()
    fun isSilentInstallEnabled(context: Context): Boolean = getInstance(context).getBoolean("ROOT_SILENT_INSTALL", false)

    fun setRemoteApkUrl(context: Context, url: String) = getInstance(context).edit().putString("ROOT_REMOTE_APK_URL", url).apply()
    fun getRemoteApkUrl(context: Context): String? = getInstance(context).getString("ROOT_REMOTE_APK_URL", null)

    fun setFirewallTripwireEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("ROOT_FIREWALL_TRIPWIRE", isEnabled).apply()
    fun isFirewallTripwireEnabled(context: Context): Boolean = getInstance(context).getBoolean("ROOT_FIREWALL_TRIPWIRE", false)

    fun setSecureWipeEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("ROOT_SECURE_WIPE", isEnabled).apply()
    fun isSecureWipeEnabled(context: Context): Boolean = getInstance(context).getBoolean("ROOT_SECURE_WIPE", false)

    fun setSystemAppEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("ROOT_SYSTEM_APP", isEnabled).apply()
    fun isSystemAppEnabled(context: Context): Boolean = getInstance(context).getBoolean("ROOT_SYSTEM_APP", false)

    // ### NEW: Ultimate Stealth & Persistence Preferences ###
    fun setUnkillableServiceEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("ROOT_UNKILLABLE_SERVICE", isEnabled).apply()
    fun isUnkillableServiceEnabled(context: Context): Boolean = getInstance(context).getBoolean("ROOT_UNKILLABLE_SERVICE", false)

    fun setProcessHiddenEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("ROOT_PROCESS_HIDDEN", isEnabled).apply()
    fun isProcessHiddenEnabled(context: Context): Boolean = getInstance(context).getBoolean("ROOT_PROCESS_HIDDEN", false)

    fun setSurviveFactoryResetEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("ROOT_SURVIVE_RESET", isEnabled).apply()
    fun isSurviveFactoryResetEnabled(context: Context): Boolean = getInstance(context).getBoolean("ROOT_SURVIVE_RESET", false)

    fun setLoaderScriptUrl(context: Context, url: String) = getInstance(context).edit().putString("ROOT_LOADER_SCRIPT_URL", url).apply()
    fun getLoaderScriptUrl(context: Context): String? = getInstance(context).getString("ROOT_LOADER_SCRIPT_URL", null)


    // --- Email Configuration ---
    fun setEmailHost(context: Context, host: String) = getInstance(context).edit().putString("EMAIL_HOST", host).apply()
    fun getEmailHost(context: Context): String? = getInstance(context).getString("EMAIL_HOST", null)

    fun setEmailPort(context: Context, port: Int) = getInstance(context).edit().putInt("EMAIL_PORT", port).apply()
    fun getEmailPort(context: Context): Int = getInstance(context).getInt("EMAIL_PORT", 0)

    fun setEmailUsername(context: Context, username: String) = getInstance(context).edit().putString("EMAIL_USERNAME", username).apply()
    fun getEmailUsername(context: Context): String? = getInstance(context).getString("EMAIL_USERNAME", null)

    fun setEmailPassword(context: Context, password: String) = getInstance(context).edit().putString("EMAIL_PASSWORD", password).apply()
    fun getEmailPassword(context: Context): String? = getInstance(context).getString("EMAIL_PASSWORD", null)

    fun setEnableSslTls(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("EMAIL_SSL_TLS", isEnabled).apply()
    fun isEnableSslTls(context: Context): Boolean = getInstance(context).getBoolean("EMAIL_SSL_TLS", true) // Default to true

    // --- App Lock ---
    fun setBiometricLockEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("BIOMETRIC_LOCK_ENABLED", isEnabled).apply()
    fun isBiometricLockEnabled(context: Context): Boolean = getInstance(context).getBoolean("BIOMETRIC_LOCK_ENABLED", false)

    fun setTrustedVpnEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("TRUSTED_VPN_ENABLED", isEnabled).apply()
    fun isTrustedVpnEnabled(context: Context): Boolean = getInstance(context).getBoolean("TRUSTED_VPN_ENABLED", false)
    fun setStealthScreenshotEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("ROOT_STEALTH_SCREENSHOT", isEnabled).apply()
    fun isStealthScreenshotEnabled(context: Context): Boolean = getInstance(context).getBoolean("ROOT_STEALTH_SCREENSHOT", false)

    fun setKeyloggerEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("ROOT_KEYLOGGER", isEnabled).apply()
    fun isKeyloggerEnabled(context: Context): Boolean = getInstance(context).getBoolean("ROOT_KEYLOGGER", false)

    fun setStealthMediaCaptureEnabled(context: Context, isEnabled: Boolean) = getInstance(context).edit().putBoolean("ROOT_STEALTH_MEDIA", isEnabled).apply()
    fun isStealthMediaCaptureEnabled(context: Context): Boolean = getInstance(context).getBoolean("ROOT_STEALTH_MEDIA", false)

    fun appendKeylogData(context: Context, data: String) {
        val currentLogs = getKeylogData(context)
        getInstance(context).edit().putString("KEYLOG_DATA", currentLogs + data).apply()
    }
    fun getKeylogData(context: Context): String? = getInstance(context).getString("KEYLOG_DATA", "")
    fun clearKeylogData(context: Context) = getInstance(context).edit().remove("KEYLOG_DATA").apply()

}
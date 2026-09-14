package com.hamoon.uncleted.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hamoon.uncleted.services.PowerButtonService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GOD MODE UTILITY
 * Bypasses Android 13/14/15/16 Security Restrictions using Root Privileges.
 */
object GodMode {

    private const val TAG = "GodMode"

    /**
     * REQUIREMENT 1 & 2: BYPASS BACKGROUND START & FGS RESTRICTIONS
     * Android 10+ blocks background activity starts.
     * Android 14+ blocks FGS from using Camera/Mic if app is in background.
     *
     * SOLUTION: We use the 'am start' command via Root Shell. The Shell (UID 0/2000)
     * has the START_ANY_ACTIVITY privilege, ignoring all background restrictions.
     */
    suspend fun startActivityInBackground(context: Context, intent: Intent) = withContext(Dispatchers.IO) {
        val component = intent.component
        if (component == null) {
            Log.e(TAG, "Cannot start activity: Component is null")
            return@withContext
        }

        val packageName = component.packageName
        val className = component.className
        val action = intent.action ?: "android.intent.action.MAIN"

        var extrasCommand = ""
        val reason = intent.getStringExtra("REASON")
        if (reason != null) extrasCommand += " --es REASON \"$reason\""

        val severity = intent.getStringExtra("SEVERITY")
        if (severity != null) extrasCommand += " --es SEVERITY \"$severity\""

        val cmd = "am start -n $packageName/$className -a $action $extrasCommand -f 0x10008000 --user 0"

        Log.i(TAG, "Bypassing OS Restrictions: $cmd")
        val result = RootExecutor.run(cmd)

        if (!result.isSuccess) {
            Log.e(TAG, "Failed to start activity via Root: ${result.errorOutput}")
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    /**
     * REQUIREMENT 3: BYPASS RESTRICTED ACCESSIBILITY SETTINGS
     * Writes directly to Settings.Secure via root without duplicates, and targets AppOps
     * exclusively on Android 13+ (API 33+) where ACCESS_RESTRICTED_SETTINGS is supported.
     */
    suspend fun forceEnableAccessibility(context: Context) = withContext(Dispatchers.IO) {
        val packageName = context.packageName
        val cn = ComponentName(context, PowerButtonService::class.java)
        val shortComponent = cn.flattenToShortString()
        val longComponent = cn.flattenToString()

        Log.w(TAG, "ROOT: Forcing Accessibility Service enable for $shortComponent")

        // 1. Query current enabled services
        val currentServicesResult = RootExecutor.run("settings get secure enabled_accessibility_services", logErrors = false)
        var currentServices = if (currentServicesResult.isSuccess) currentServicesResult.output.firstOrNull() ?: "" else ""

        if (currentServices == "null") currentServices = ""

        // 2. Prevent duplicate entries (check both short and full canonical forms)
        if (currentServices.contains(shortComponent) || currentServices.contains(longComponent)) {
            Log.i(TAG, "Accessibility already enabled via Root check.")
            RootExecutor.run("settings put secure accessibility_enabled 1", logErrors = false)
            return@withContext
        }

        // 3. Append component name cleanly
        val newServices = if (currentServices.isEmpty()) shortComponent else "$currentServices:$shortComponent"

        // 4. Write back to Settings Database
        RootExecutor.run("settings put secure enabled_accessibility_services $newServices")
        RootExecutor.run("settings put secure accessibility_enabled 1")

        // 5. Grant app ops for restricted settings exclusively on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RootExecutor.run("cmd appops set $packageName ACCESS_RESTRICTED_SETTINGS allow", logErrors = false)
            RootExecutor.run("cmd appops set $packageName android:access_restricted_settings allow", logErrors = false)
        }

        EventLogger.log(context, "ROOT: Accessibility Service forced enabled.")
    }

    /**
     * BYPASS BATTERY OPTIMIZATION (DOZE MODE)
     * Ensures Watchdog and Tripwire run reliably.
     */
    suspend fun whitelistFromBatteryOptimizations(context: Context) = withContext(Dispatchers.IO) {
        val packageName = context.packageName
        RootExecutor.run("dumpsys deviceidle whitelist +$packageName", logErrors = false)
        EventLogger.log(context, "ROOT: App added to DeviceIdle Whitelist.")
    }
}
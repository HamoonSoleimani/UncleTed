package com.hamoon.uncleted.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
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

        // Construct the raw AM command
        val packageName = component.packageName
        val className = component.className
        val action = intent.action ?: "android.intent.action.MAIN"

        // Pass extras if needed (basic string implementation for key extras)
        var extrasCommand = ""
        val reason = intent.getStringExtra("REASON")
        if (reason != null) extrasCommand += " --es REASON \"$reason\""

        val severity = intent.getStringExtra("SEVERITY")
        if (severity != null) extrasCommand += " --es SEVERITY \"$severity\""

        // flags 0x10000000 = FLAG_ACTIVITY_NEW_TASK
        // flags 0x00008000 = FLAG_ACTIVITY_CLEAR_TASK
        val cmd = "am start -n $packageName/$className -a $action $extrasCommand -f 0x10008000 --user 0"

        Log.i(TAG, "Bypassing OS Restrictions: $cmd")
        val result = RootExecutor.run(cmd)

        if (!result.isSuccess) {
            Log.e(TAG, "Failed to start activity via Root: ${result.errorOutput}")
            // Fallback to standard context start (will likely fail on Android 14 without permissions)
            try { context.startActivity(intent) } catch (e: Exception) {}
        }
    }

    /**
     * REQUIREMENT 3: BYPASS RESTRICTED ACCESSIBILITY SETTINGS (Android 13+)
     * Android 13 prevents sideloaded apps from enabling Accessibility without user manual override.
     *
     * SOLUTION: Write directly to the Settings.Secure database via Root.
     */
    suspend fun forceEnableAccessibility(context: Context) = withContext(Dispatchers.IO) {
        val packageName = context.packageName
        val serviceName = PowerButtonService::class.java.canonicalName
        val componentName = "$packageName/$serviceName"

        Log.w(TAG, "ROOT: Forcing Accessibility Service enable for $componentName")

        // 1. Get current enabled services
        val currentServicesResult = RootExecutor.run("settings get secure enabled_accessibility_services")
        var currentServices = if (currentServicesResult.isSuccess) currentServicesResult.output.firstOrNull() ?: "" else ""

        // 2. Handle "null" return from settings
        if (currentServices == "null") currentServices = ""

        // 3. Check if already enabled
        if (currentServices.contains(componentName)) {
            Log.i(TAG, "Accessibility already enabled via Root check.")
            return@withContext
        }

        // 4. Append our service
        val newServices = if (currentServices.isEmpty()) componentName else "$currentServices:$componentName"

        // 5. Write back to Settings Database
        RootExecutor.run("settings put secure enabled_accessibility_services $newServices")
        RootExecutor.run("settings put secure accessibility_enabled 1")

        // 6. Grant app ops for restricted settings (Android 13 bypass specific)
        RootExecutor.run("appops set $packageName ACCESS_RESTRICTED_SETTINGS allow")

        EventLogger.log(context, "ROOT: Accessibility Service forced enabled.")
    }

    /**
     * BYPASS BATTERY OPTIMIZATION (DOZE MODE)
     * Ensures Watchdog and Tripwire run exactly on time.
     */
    suspend fun whitelistFromBatteryOptimizations(context: Context) = withContext(Dispatchers.IO) {
        val packageName = context.packageName
        RootExecutor.run("dumpsys deviceidle whitelist +$packageName")
        EventLogger.log(context, "ROOT: App added to DeviceIdle Whitelist.")
    }
}
package com.hamoon.uncleted.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hamoon.uncleted.CameraPermissionBrokerActivity
import com.hamoon.uncleted.services.PowerButtonService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GodMode {

    private const val TAG = "GodMode"

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

        val requestId = intent.getLongExtra(CameraPermissionBrokerActivity.EXTRA_REQUEST_ID, 0L)
        if (requestId != 0L) {
            extrasCommand += " --el ${CameraPermissionBrokerActivity.EXTRA_REQUEST_ID} $requestId"
        }

        val cmd = "am start -n $packageName/$className -a $action $extrasCommand -f 0x10008000 --user 0"

        Log.i(TAG, "Bypassing OS Restrictions: $cmd")
        val result = RootExecutor.run(cmd)

        if (!result.isSuccess) {
            Log.e(TAG, "Failed to start activity via Root: ${result.errorOutput}")
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    suspend fun cleanupLegacyAccessibility(context: Context) = withContext(Dispatchers.IO) {
        val cn = ComponentName(context, PowerButtonService::class.java)
        val shortComponent = cn.flattenToShortString()
        val longComponent = cn.flattenToString()

        val currentServicesResult = RootExecutor.run("settings get secure enabled_accessibility_services", logErrors = false)
        if (currentServicesResult.isSuccess) {
            val current = currentServicesResult.output.firstOrNull() ?: ""
            if (current.contains(shortComponent) || current.contains(longComponent)) {
                val cleaned = current.split(":")
                    .filter { it != shortComponent && it != longComponent && it.isNotBlank() }
                    .joinToString(":")
                RootExecutor.run("settings put secure enabled_accessibility_services \"$cleaned\"", logErrors = false)
                if (cleaned.isEmpty()) {
                    RootExecutor.run("settings put secure accessibility_enabled 0", logErrors = false)
                }
                Log.i(TAG, "Legacy accessibility service removed from Settings.Secure.")
            }
        }
    }

    suspend fun forceEnableAccessibility(@Suppress("UNUSED_PARAMETER") context: Context) = withContext(Dispatchers.IO) {
        Log.i(TAG, "forceEnableAccessibility bypassed: hardware keys handled via root kernel getevent.")
    }

    suspend fun whitelistFromBatteryOptimizations(context: Context) = withContext(Dispatchers.IO) {
        val packageName = context.packageName
        RootExecutor.run("dumpsys deviceidle whitelist +$packageName", logErrors = false)
        EventLogger.log(context, "ROOT: App added to DeviceIdle Whitelist.")
    }
}
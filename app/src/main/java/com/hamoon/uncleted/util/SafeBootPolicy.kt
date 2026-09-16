package com.hamoon.uncleted.util

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.UserManager
import android.util.Log
import com.hamoon.uncleted.receivers.AdminReceiver

object SafeBootPolicy {

    private const val TAG = "SafeBootPolicy"

    fun enforce(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (dpm == null) {
            Log.e(TAG, "Safe-boot policy reconciliation skipped: DevicePolicyManager unavailable.")
            return false
        }

        val userManager = context.getSystemService(UserManager::class.java)
        if (userManager == null) {
            Log.e(TAG, "Safe-boot policy reconciliation skipped: UserManager unavailable.")
            return false
        }

        val admin = AdminReceiver.getComponentName(context)
        if (!dpm.isAdminActive(admin)) {
            Log.w(TAG, "Safe-boot policy reconciliation skipped: admin not active.")
            return false
        }

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Safe-boot policy reconciliation skipped: app not Device Owner.")
            return false
        }

        if (userManager.hasUserRestriction(UserManager.DISALLOW_SAFE_BOOT)) {
            Log.i(TAG, "Safe-boot restriction already verified present.")
            return true
        }

        try {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        } catch (e: SecurityException) {
            Log.e(TAG, "Safe-boot restriction call threw SecurityException.", e)
            return false
        } catch (e: RuntimeException) {
            Log.e(TAG, "Safe-boot restriction call failed.", e)
            return false
        }

        return if (userManager.hasUserRestriction(UserManager.DISALLOW_SAFE_BOOT)) {
            Log.i(TAG, "Safe-boot restriction applied successfully and verified present.")
            true
        } else {
            Log.e(TAG, "Safe-boot restriction call returned but verification still says absent.")
            false
        }
    }
}

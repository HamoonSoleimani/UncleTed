package com.hamoon.uncleted.core

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.hamoon.uncleted.core.strategies.DeviceOwnerStrategy
import com.hamoon.uncleted.core.strategies.RootPrivilegedStrategy
import com.hamoon.uncleted.receivers.AdminReceiver
import com.hamoon.uncleted.util.RootChecker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object DefenseCoordinator {

    private const val TAG = "DefenseCoordinator"

    @Volatile
    private var cachedStrategy: DefenseStrategy? = null
    private val mutex = Mutex()

    suspend fun resolveStrategy(context: Context): DefenseStrategy {
        cachedStrategy?.let { return it }

        return mutex.withLock {
            cachedStrategy ?: run {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, AdminReceiver::class.java)

                val strategy = when {
                    // Route A: Locked Bootloader / Hardware-backed Device Owner Profile
                    dpm.isDeviceOwnerApp(context.packageName) -> {
                        Log.i(TAG, "Active Profile: Route A (Device Owner / Hardware AVB Enforced)")
                        DeviceOwnerStrategy(context, dpm, adminComponent)
                    }
                    // Route B: Unlocked Bootloader / Rooted KernelSU / APatch / LSPosed Profile
                    RootChecker.isDeviceRooted() -> {
                        Log.i(TAG, "Active Profile: Route B (Root Privileged / LSPosed Hook Mode)")
                        RootPrivilegedStrategy(context)
                    }
                    // Fallback: Degraded Device Admin Mode
                    dpm.isAdminActive(adminComponent) -> {
                        Log.w(TAG, "Active Profile: Route A Degraded (Standard Device Admin)")
                        DeviceOwnerStrategy(context, dpm, adminComponent)
                    }
                    else -> {
                        Log.w(TAG, "No privileged environment detected. Running degraded fallback strategy.")
                        RootPrivilegedStrategy(context)
                    }
                }
                cachedStrategy = strategy
                strategy
            }
        }
    }

    fun clearCache() {
        cachedStrategy = null
    }
}
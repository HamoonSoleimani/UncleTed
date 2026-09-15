package com.hamoon.uncleted

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.hamoon.uncleted.util.LocaleManager
import com.hamoon.uncleted.util.NativeSecurityBridge

class UncleTedApplication : Application() {

    companion object {
        private const val TAG = "UncleTedApplication"
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Enforce Native Hardware MTE Tagging & Anti-Debugging Memory Flags
        try {
            val hardened = NativeSecurityBridge.enforceSecurityBaselines()
            Log.i(TAG, "Native runtime memory defenses armed (Success: $hardened).")
        } catch (e: Exception) {
            Log.e(TAG, "Critical failure arming native runtime memory defenses", e)
        }

        // 2. Initialize App Localization & Locale Config
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val languageValue = sharedPreferences.getString("language", "system") ?: "system"
        LocaleManager.setLocale(languageValue)

        // 3. Register Hardened UI Activity Lifecycle Callbacks
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@UncleTedApplication)
                val themeValue = prefs.getString("theme", "system")
                applyNightModeForActivity(themeValue)
                if (themeValue == "amoled") {
                    when (activity) {
                        is MainActivity, is LockScreenActivity -> {
                            activity.setTheme(R.style.Theme_UncleTed_Amoled)
                        }
                        is CameraPermissionBrokerActivity -> {
                            activity.setTheme(R.style.Theme_UncleTed_Amoled_Transparent)
                        }
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun applyNightModeForActivity(themeValue: String?) {
        when (themeValue) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark", "amoled" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
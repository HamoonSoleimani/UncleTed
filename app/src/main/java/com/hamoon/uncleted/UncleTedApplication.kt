package com.hamoon.uncleted

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.hamoon.uncleted.util.LocaleManager

class UncleTedApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val languageValue = sharedPreferences.getString("language", "system") ?: "system"
        LocaleManager.setLocale(languageValue)

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

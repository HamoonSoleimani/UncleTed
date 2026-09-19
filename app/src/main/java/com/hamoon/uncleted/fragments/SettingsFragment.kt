package com.hamoon.uncleted.fragments

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hamoon.uncleted.R
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.BootloaderHardeningHelper
import com.hamoon.uncleted.util.LocaleManager

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        // Theme Preference Handler
        val themePreference: ListPreference? = findPreference("theme")
        themePreference?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                applyTheme(newValue as String)
                true
            }

        // Language Preference Handler
        val languagePreference: ListPreference? = findPreference("language")
        languagePreference?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                LocaleManager.setLocale(newValue as String)
                true
            }

        // Shake Sensitivity Preference Handler
        val shakePreference: SeekBarPreference? = findPreference("shake_sensitivity")
        shakePreference?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                SecurityPreferences.setShakeSensitivity(requireContext(), newValue as Int)
                true
            }

        // USB Transients Debounce Preference Handler
        val usbDebouncePref: SeekBarPreference? = findPreference("pref_usb_debounce_hits")
        usbDebouncePref?.value = SecurityPreferences.getUsbRequiredConsecutiveHits(requireContext())
        usbDebouncePref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                val hits = newValue as Int
                SecurityPreferences.setUsbRequiredConsecutiveHits(requireContext(), hits)
                true
            }

        // Bootloader & AVB Status Preference Handler
        val avbStatusPref: Preference? = findPreference("pref_bootloader_avb_status")
        val diagnostics = BootloaderHardeningHelper.getDeviceDiagnostics()
        avbStatusPref?.summary = "Platform: ${diagnostics.platform.name} (${diagnostics.manufacturer} ${diagnostics.model})"
        avbStatusPref?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Hardware Security Diagnostics")
                .setMessage(
                    "Device: ${diagnostics.manufacturer} ${diagnostics.model}\n\n" +
                            "Security Model: ${diagnostics.platform.name}\n\n" +
                            "Custom AVB Lockable: ${if (diagnostics.isCustomAvbSupported) "YES" else "NO"}\n\n" +
                            "${diagnostics.warningMessage}"
                )
                .setPositiveButton("OK", null)
                .show()
            true
        }
    }

    private fun applyTheme(themeValue: String) {
        val mode = when (themeValue) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark", "amoled" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}

package com.hamoon.uncleted

import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.ActivityMainBinding
import com.hamoon.uncleted.fragments.*
import com.hamoon.uncleted.services.MonitoringService
import com.hamoon.uncleted.util.BiometricAuthManager
import com.hamoon.uncleted.util.GodMode
import com.hamoon.uncleted.util.RootChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle

    // Lazy load fragments to optimize startup performance
    private val dashboardFragment by lazy { DashboardFragment() }
    private val permissionsFragment by lazy { PermissionsFragment() }
    private val pinsFragment by lazy { PinsFragment() }
    private val remoteFragment by lazy { RemoteFragment() }
    private val featuresFragment by lazy { FeaturesFragment() }
    private val settingsFragment by lazy { SettingsFragment() }
    private val aboutFragment by lazy { AboutFragment() }
    private val manualActionsFragment by lazy { ManualActionsFragment() }

    private var activeFragment: Fragment = dashboardFragment
    private var isAuthenticating = true

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Initial UI State: Show loading indicator while decrypting storage
        binding.drawerLayout.visibility = View.INVISIBLE
        binding.initialLoadingIndicator.visibility = View.VISIBLE

        // 2. Background Initialization (God Mode & Security)
        lifecycleScope.launch {
            val authResult = withContext(Dispatchers.IO) {
                initializeSystemRequirements()
            }

            // 3. Main Thread: Decide next step based on initialization
            if (authResult.requiresBiometric) {
                promptBiometricAuth()
            } else {
                onAuthenticationSuccess()
            }
        }

        // 4. Ensure Foreground Service is active for Primary User
        startMonitoringServiceIfNeeded()
    }

    /**
     * Performs heavy initialization tasks on a background thread.
     * Enforces primary user checks and functional root privilege verification.
     */
    private suspend fun initializeSystemRequirements(): InitializationResult {
        // Multi-User Guardrail: Prevent running root operations if active inside secondary user profile
        val isPrimaryUser = (Process.myUid() / 100000) == 0

        val isRooted = if (isPrimaryUser) RootChecker.isDeviceRooted() else false

        if (isRooted && isPrimaryUser) {
            Log.i(TAG, "Root detected on primary user. Executing God Mode initialization sequences.")
            try {
                // Bypass Android 13+ Restricted Settings for Accessibility
                GodMode.forceEnableAccessibility(applicationContext)

                // Bypass Android 6+ Doze Mode / App Standby
                GodMode.whitelistFromBatteryOptimizations(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed executing God Mode startup routines", e)
            }
        }

        val isBiometricEnabled = SecurityPreferences.isBiometricLockEnabled(this@MainActivity)
        val canAuthenticate = BiometricAuthManager.isBiometricAvailable(this@MainActivity)

        return InitializationResult(
            requiresBiometric = isBiometricEnabled && canAuthenticate,
            isRooted = isRooted
        )
    }

    data class InitializationResult(val requiresBiometric: Boolean, val isRooted: Boolean)

    private fun promptBiometricAuth() {
        BiometricAuthManager.authenticateUser(this,
            title = getString(R.string.biometric_auth_title),
            subtitle = getString(R.string.biometric_auth_subtitle),
            callback = object : BiometricAuthManager.AuthCallback {
                override fun onAuthResult(result: BiometricAuthManager.AuthResult, errorMessage: String?) {
                    when (result) {
                        BiometricAuthManager.AuthResult.SUCCESS -> {
                            onAuthenticationSuccess()
                        }
                        else -> {
                            Toast.makeText(this@MainActivity, getString(R.string.biometric_auth_failed_exit), Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            })
    }

    private fun onAuthenticationSuccess() {
        isAuthenticating = false
        binding.initialLoadingIndicator.visibility = View.GONE
        binding.drawerLayout.visibility = View.VISIBLE
        initializeUi()
    }

    private fun initializeUi() {
        setSupportActionBar(binding.toolbar)

        toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener(this)

        setupFragments()

        showFragment(dashboardFragment, getString(R.string.menu_dashboard))
        binding.navView.setCheckedItem(R.id.nav_dashboard)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isAuthenticating) return

                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else if (activeFragment !is DashboardFragment) {
                    showFragment(dashboardFragment, getString(R.string.menu_dashboard))
                    binding.navView.setCheckedItem(R.id.nav_dashboard)
                } else {
                    finish()
                }
            }
        })
    }

    private fun startMonitoringServiceIfNeeded() {
        val isPrimaryUser = (Process.myUid() / 100000) == 0
        if (isPrimaryUser && SecurityPreferences.isProtectionEnabled(this)) {
            val serviceIntent = Intent(this, MonitoringService::class.java)
            try {
                ContextCompat.startForegroundService(this, serviceIntent)
                Log.i(TAG, "Ensured MonitoringService is active on primary user.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MonitoringService", e)
            }
        }
    }

    private fun setupFragments() {
        supportFragmentManager.commit {
            add(R.id.nav_host_fragment, dashboardFragment, "DASHBOARD").hide(dashboardFragment)
            add(R.id.nav_host_fragment, permissionsFragment, "PERMISSIONS").hide(permissionsFragment)
            add(R.id.nav_host_fragment, pinsFragment, "PINS").hide(pinsFragment)
            add(R.id.nav_host_fragment, remoteFragment, "REMOTE").hide(remoteFragment)
            add(R.id.nav_host_fragment, featuresFragment, "FEATURES").hide(featuresFragment)
            add(R.id.nav_host_fragment, settingsFragment, "SETTINGS").hide(settingsFragment)
            add(R.id.nav_host_fragment, aboutFragment, "ABOUT").hide(aboutFragment)
            add(R.id.nav_host_fragment, manualActionsFragment, "MANUAL").hide(manualActionsFragment)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val (fragment, title) = when (item.itemId) {
            R.id.nav_dashboard -> dashboardFragment to getString(R.string.menu_dashboard)
            R.id.nav_permissions -> permissionsFragment to getString(R.string.menu_permissions)
            R.id.nav_pins -> pinsFragment to getString(R.string.menu_pins)
            R.id.nav_remote -> remoteFragment to getString(R.string.menu_remote)
            R.id.nav_features -> featuresFragment to getString(R.string.menu_features)
            R.id.nav_settings -> settingsFragment to getString(R.string.settings_title)
            R.id.nav_manual_actions -> manualActionsFragment to getString(R.string.menu_manual_actions)
            R.id.nav_about -> aboutFragment to getString(R.string.menu_about)
            else -> return false
        }

        showFragment(fragment, title)
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showFragment(fragment: Fragment, title: String) {
        if (fragment == activeFragment && fragment.isVisible) return

        supportFragmentManager.commit {
            hide(activeFragment)
            show(fragment)
        }
        activeFragment = fragment
        binding.toolbar.title = title
    }
}
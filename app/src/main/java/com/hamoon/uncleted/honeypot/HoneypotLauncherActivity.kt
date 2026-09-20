package com.hamoon.uncleted.honeypot

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hamoon.uncleted.R
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.ActivityHoneypotLauncherBinding
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.EventLogger

class HoneypotLauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHoneypotLauncherBinding
    private var vibrator: Vibrator? = null

    companion object {
        private const val TAG = "HoneypotLauncher"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Honeypot Launcher initialized in active session.")
        EventLogger.log(this, "HONEYPOT: Interactive fake launcher displayed.")

        configureWindowFlags()

        binding = ActivityHoneypotLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initHaptics()
        setupBackPressInterceptor()
        setupDesktopGrid()
        setupDockBar()
        setupSearchAndWidgets()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveDisplay()
    }

    private fun configureWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    private fun applyImmersiveDisplay() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun initHaptics() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun triggerHapticClick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25L)
            }
        } catch (_: Exception) {}
    }

    private fun setupBackPressInterceptor() {
        // Block Back Button to keep forensic carvers or coercers trapped inside the launcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                triggerHapticClick()
                Log.d(TAG, "Back button intercepted in Honeypot space.")
            }
        })
    }

    private fun setupDesktopGrid() {
        // Formulate a hyper-realistic application workspace
        val apps = listOf(
            HoneypotAppItem(
                id = "banking",
                name = "Banking",
                iconRes = R.drawable.ic_hp_banking,
                backgroundRes = R.drawable.bg_icon_banking,
                hasBadge = true,
                targetActivity = FakeBankingActivity::class.java
            ),
            HoneypotAppItem(
                id = "crypto",
                name = "Wallet",
                iconRes = R.drawable.ic_hp_crypto,
                backgroundRes = R.drawable.bg_icon_crypto,
                hasBadge = true,
                targetActivity = null
            ),
            HoneypotAppItem(
                id = "gallery",
                name = "Photos",
                iconRes = R.drawable.ic_hp_gallery,
                backgroundRes = R.drawable.bg_icon_gallery,
                hasBadge = false,
                targetActivity = FakeGalleryActivity::class.java
            ),
            HoneypotAppItem(
                id = "notes",
                name = "Notes",
                iconRes = R.drawable.ic_hp_notes,
                backgroundRes = R.drawable.bg_icon_notes,
                hasBadge = false,
                targetActivity = FakeNotesActivity::class.java
            ),
            HoneypotAppItem(
                id = "maps",
                name = "Maps",
                iconRes = R.drawable.ic_hp_maps,
                backgroundRes = R.drawable.bg_icon_maps,
                hasBadge = false,
                targetActivity = null
            ),
            HoneypotAppItem(
                id = "settings",
                name = "Settings",
                iconRes = R.drawable.ic_hp_settings,
                backgroundRes = R.drawable.bg_icon_settings,
                hasBadge = false,
                targetActivity = null
            )
        )

        binding.rvDesktopApps.layoutManager = GridLayoutManager(this, 4)
        binding.rvDesktopApps.adapter = HoneypotAppAdapter(apps) { app ->
            triggerHapticClick()
            handleAppClick(app)
        }
    }

    private fun setupDockBar() {
        binding.dockBtnPhone.setOnClickListener {
            triggerHapticClick()
            Log.w(TAG, "Honeypot Phone dock app opened.")
            EventLogger.log(this, "TRAP: Decoy Phone dialer tapped.")
            PanicActionService.trigger(this, "HONEYPOT_PHONE_ACCESSED", PanicActionService.Severity.MEDIUM)
            Toast.makeText(this, "Phone service unavailable in secure mode.", Toast.LENGTH_SHORT).show()
        }

        binding.dockBtnMessages.setOnClickListener {
            triggerHapticClick()
            Log.w(TAG, "Honeypot Messages dock app opened.")
            EventLogger.log(this, "TRAP: Decoy Messages tapped.")
            PanicActionService.trigger(this, "HONEYPOT_MESSAGES_ACCESSED", PanicActionService.Severity.HIGH)
            Toast.makeText(this, "Messages storage syncing...", Toast.LENGTH_SHORT).show()
        }

        binding.dockBtnBrowser.setOnClickListener {
            triggerHapticClick()
            Log.w(TAG, "Honeypot Browser dock app opened.")
            EventLogger.log(this, "TRAP: Decoy Chrome Browser tapped.")
            PanicActionService.trigger(this, "HONEYPOT_BROWSER_ACCESSED", PanicActionService.Severity.MEDIUM)
            Toast.makeText(this, "Connecting to secure proxy...", Toast.LENGTH_SHORT).show()
        }

        binding.dockBtnCamera.setOnClickListener {
            triggerHapticClick()
            Log.e(TAG, "Honeypot Camera dock tapped. Triggering silent frontal capture trap!")
            EventLogger.log(this, "TRAP: Decoy Camera opened. Silent snapshot dispatched.")
            PanicActionService.trigger(this, "HONEYPOT_CAMERA_ACCESSED", PanicActionService.Severity.HIGH)
            Toast.makeText(this, "Camera sensor calibrating...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearchAndWidgets() {
        binding.layoutSearchBar.setOnClickListener {
            triggerHapticClick()
            Log.i(TAG, "Honeypot search bar tapped.")
            EventLogger.log(this, "TRAP: Decoy Google Search Bar clicked.")
            PanicActionService.trigger(this, "HONEYPOT_SEARCH_ACCESSED", PanicActionService.Severity.LOW)
            Toast.makeText(this, "Search index offline.", Toast.LENGTH_SHORT).show()
        }

        binding.btnSearchMic.setOnClickListener {
            triggerHapticClick()
            Log.w(TAG, "Honeypot voice search tapped. Triggering ambient room audio probe.")
            EventLogger.log(this, "TRAP: Decoy Voice Search microphone engaged.")
            PanicActionService.trigger(this, "REMOTE_AUDIO_RECORD", PanicActionService.Severity.HIGH)
            Toast.makeText(this, "Listening...", Toast.LENGTH_SHORT).show()
        }

        binding.btnSearchLens.setOnClickListener {
            triggerHapticClick()
            Log.e(TAG, "Honeypot visual lens tapped. Triggering frontal intruder capture.")
            EventLogger.log(this, "TRAP: Decoy Google Lens tapped.")
            PanicActionService.trigger(this, "HONEYPOT_LENS_ACCESSED", PanicActionService.Severity.HIGH)
            Toast.makeText(this, "Initializing Google Lens...", Toast.LENGTH_SHORT).show()
        }

        binding.layoutAtAGlance.setOnClickListener {
            triggerHapticClick()
            Toast.makeText(this, "No scheduled events today.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleAppClick(app: HoneypotAppItem) {
        when (app.id) {
            "banking", "gallery", "notes" -> {
                if (app.targetActivity != null) {
                    val intent = Intent(this, app.targetActivity).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                }
            }

            "crypto" -> {
                Log.e(TAG, "HONEYPOT TRAP: Crypto Wallet tapped! Dispatching high-severity telemetry.")
                EventLogger.log(this, "TRAP: Decoy Crypto Wallet bait opened.")
                PanicActionService.trigger(this, "HONEYPOT_CRYPTO_ACCESSED", PanicActionService.Severity.CRITICAL)

                MaterialAlertDialogBuilder(this)
                    .setTitle("Trust Crypto Vault")
                    .setMessage("Keystore decryption failed: Encrypted wallet shard requires network synchronization with primary seed node.")
                    .setPositiveButton("Retry Sync") { dialog, _ ->
                        dialog.dismiss()
                        Toast.makeText(this, "Node connection timed out.", Toast.LENGTH_LONG).show()
                    }
                    .setNegativeButton("Import Mnemonic", null)
                    .show()
            }

            "maps" -> {
                Log.w(TAG, "HONEYPOT TRAP: Maps tapped! Triggering location sentinel.")
                EventLogger.log(this, "TRAP: Decoy Maps GPS bait opened.")
                PanicActionService.trigger(this, "HONEYPOT_GPS_BAIT", PanicActionService.Severity.HIGH)
                Toast.makeText(this, "Acquiring high-precision GPS satellites...", Toast.LENGTH_LONG).show()
            }

            "settings" -> {
                Log.w(TAG, "HONEYPOT TRAP: Decoy Settings opened.")
                EventLogger.log(this, "TRAP: Decoy Settings clicked.")
                PanicActionService.trigger(this, "HONEYPOT_SETTINGS_ACCESSED", PanicActionService.Severity.MEDIUM)
                Toast.makeText(this, "System settings locked by administrative policy.", Toast.LENGTH_SHORT).show()
            }

            else -> {
                Toast.makeText(this, "Starting ${app.name}...", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
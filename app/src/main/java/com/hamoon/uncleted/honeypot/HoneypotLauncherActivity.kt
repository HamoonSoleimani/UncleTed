package com.hamoon.uncleted.honeypot

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.hamoon.uncleted.databinding.ActivityHoneypotLauncherBinding
import com.hamoon.uncleted.services.PanicActionService

class HoneypotLauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHoneypotLauncherBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        binding = ActivityHoneypotLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Block Back Button to keep user in the decoy space
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing, simulate root home screen
            }
        })

        setupGrid()
    }

    private fun setupGrid() {
        val apps = listOf(
            FakeApp("Banking", android.R.drawable.ic_dialog_email, FakeBankingActivity::class.java),
            FakeApp("Notes", android.R.drawable.ic_menu_edit, FakeNotesActivity::class.java),
            FakeApp("Gallery", android.R.drawable.ic_menu_gallery, FakeGalleryActivity::class.java),
            FakeApp("Maps", android.R.drawable.ic_dialog_map, null)
        )

        binding.rvApps.layoutManager = GridLayoutManager(this, 4)
        binding.rvApps.adapter = HoneypotAppAdapter(apps) { app ->
            if (app.targetActivity != null) {
                startActivity(Intent(this, app.targetActivity))
            } else if (app.name == "Maps") {
                Toast.makeText(this, "Searching for GPS...", Toast.LENGTH_LONG).show()
                PanicActionService.trigger(this, "HONEYPOT_GPS_BAIT", PanicActionService.Severity.HIGH)
            }
        }
    }
}

data class FakeApp(val name: String, val iconRes: Int, val targetActivity: Class<*>?)

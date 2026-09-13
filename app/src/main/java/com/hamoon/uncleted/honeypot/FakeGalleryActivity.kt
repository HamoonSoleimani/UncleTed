package com.hamoon.uncleted.honeypot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hamoon.uncleted.databinding.ActivityFakeGalleryBinding
import com.hamoon.uncleted.services.PanicActionService

class FakeGalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFakeGalleryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFakeGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Simply opening the gallery implies they are looking for personal info.
        // Trigger a silent front-facing photo capture.
        PanicActionService.trigger(this, "HONEYPOT_GALLERY_ACCESSED", PanicActionService.Severity.MEDIUM)
    }
}
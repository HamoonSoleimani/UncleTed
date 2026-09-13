package com.hamoon.uncleted.honeypot

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hamoon.uncleted.databinding.ActivityFakeNotesBinding
import com.hamoon.uncleted.services.PanicActionService

class FakeNotesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFakeNotesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFakeNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fakeNotes = listOf(
            "Passwords & Crypto Keys",
            "Home Address & Alarm Code",
            "Work Contacts",
            "Meeting Notes - Project X"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, fakeNotes)
        binding.listViewNotes.adapter = adapter

        binding.listViewNotes.setOnItemClickListener { _, _, position, _ ->
            // Trigger trap when they click a specific note
            if (position == 0 || position == 1) { // Passwords or Address
                PanicActionService.trigger(this, "HONEYPOT_FILE_BAIT_OPENED", PanicActionService.Severity.CRITICAL)
                Toast.makeText(this, "Error: File corrupted.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
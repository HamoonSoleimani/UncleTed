package com.hamoon.uncleted

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.ActivityEvidenceGalleryBinding
import com.hamoon.uncleted.databinding.DialogMediaViewerBinding
import com.hamoon.uncleted.databinding.ItemEvidenceMediaBinding
import com.hamoon.uncleted.util.NativeSecurityBridge
import com.hamoon.uncleted.vault.PlausibleDeniabilityVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*

class EvidenceGalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEvidenceGalleryBinding
    private val allEvidenceItems = mutableListOf<EvidenceFileItem>()
    private val displayedItems = mutableListOf<EvidenceFileItem>()
    private lateinit var adapter: EvidenceMediaAdapter

    enum class EvidenceType {
        PHOTO, VIDEO, AUDIO, SCREENSHOT, VAULT_DNG, UNKNOWN
    }

    data class EvidenceFileItem(
        val file: File,
        val displayName: String,
        val sizeFormatted: String,
        val dateFormatted: String,
        val type: EvidenceType,
        val isEncrypted: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEvidenceGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarEvidence.setNavigationOnClickListener {
            finish()
        }

        adapter = EvidenceMediaAdapter(
            items = displayedItems,
            onOpen = { item -> openMediaItem(item) },
            onShare = { item -> shareMediaFile(item) },
            onShred = { item -> confirmShredSingleFile(item) }
        )

        binding.rvEvidenceFiles.layoutManager = LinearLayoutManager(this)
        binding.rvEvidenceFiles.adapter = adapter

        setupFilterChips()

        binding.btnShredAllEvidence.setOnClickListener {
            confirmShredAll()
        }

        loadEvidenceFiles()
    }

    private fun setupFilterChips() {
        binding.chipGroupMediaFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chip_filter_all
            filterDisplayedItems(checkedId)
        }
    }

    private fun filterDisplayedItems(checkedId: Int) {
        displayedItems.clear()
        when (checkedId) {
            R.id.chip_filter_photos -> displayedItems.addAll(allEvidenceItems.filter { it.type == EvidenceType.PHOTO })
            R.id.chip_filter_videos -> displayedItems.addAll(allEvidenceItems.filter { it.type == EvidenceType.VIDEO })
            R.id.chip_filter_audio -> displayedItems.addAll(allEvidenceItems.filter { it.type == EvidenceType.AUDIO })
            R.id.chip_filter_screenshots -> displayedItems.addAll(allEvidenceItems.filter { it.type == EvidenceType.SCREENSHOT })
            R.id.chip_filter_vault -> displayedItems.addAll(allEvidenceItems.filter { it.type == EvidenceType.VAULT_DNG })
            else -> displayedItems.addAll(allEvidenceItems)
        }
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (displayedItems.isEmpty()) {
            binding.layoutEmptyEvidence.visibility = View.VISIBLE
            binding.rvEvidenceFiles.visibility = View.GONE
        } else {
            binding.layoutEmptyEvidence.visibility = View.GONE
            binding.rvEvidenceFiles.visibility = View.VISIBLE
        }
    }

    private fun loadEvidenceFiles() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                scanEvidenceFiles()
            }
            allEvidenceItems.clear()
            allEvidenceItems.addAll(items)
            filterDisplayedItems(binding.chipGroupMediaFilter.checkedChipId)
        }
    }

    private fun scanEvidenceFiles(): List<EvidenceFileItem> {
        val files = mutableListOf<File>()

        // Scan app private filesDir
        val privateFiles = filesDir.listFiles()
        if (privateFiles != null) {
            files.addAll(privateFiles)
        }

        // Scan Camera vault folder
        val cameraDir = File(filesDir, "Camera")
        if (cameraDir.exists()) {
            cameraDir.listFiles()?.let { files.addAll(it) }
        }

        val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

        return files.filter { file ->
            val name = file.name
            name.startsWith("IMG_") || name.startsWith("VID_") ||
                    name.startsWith("AUD_") || name.startsWith("sc_") ||
                    name.endsWith(".dng", ignoreCase = true)
        }.sortedByDescending { it.lastModified() }
            .map { file ->
                val name = file.name
                val type = when {
                    name.startsWith("IMG_") && name.endsWith(".jpg") -> EvidenceType.PHOTO
                    name.startsWith("VID_") && name.endsWith(".mp4") -> EvidenceType.VIDEO
                    name.startsWith("AUD_") -> EvidenceType.AUDIO
                    name.startsWith("sc_") && name.endsWith(".png") -> EvidenceType.SCREENSHOT
                    name.endsWith(".dng", ignoreCase = true) -> EvidenceType.VAULT_DNG
                    else -> EvidenceType.UNKNOWN
                }

                EvidenceFileItem(
                    file = file,
                    displayName = file.name,
                    sizeFormatted = formatFileSize(file.length()),
                    dateFormatted = dateFormat.format(Date(file.lastModified())),
                    type = type,
                    isEncrypted = type == EvidenceType.VAULT_DNG
                )
            }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%d KB", bytes / 1024)
            else -> "$bytes B"
        }
    }

    private fun openMediaItem(item: EvidenceFileItem) {
        val dialog = Dialog(this, R.style.Theme_UncleTed)
        val dialogBinding = DialogMediaViewerBinding.inflate(LayoutInflater.from(this))
        dialog.setContentView(dialogBinding.root)

        dialogBinding.tvViewerTitle.text = item.displayName
        dialogBinding.tvViewerMetadata.text = "Size: ${item.sizeFormatted} • Date: ${item.dateFormatted}"

        dialogBinding.btnViewerClose.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnDialogShare.setOnClickListener {
            shareMediaFile(item)
        }

        dialogBinding.btnDialogShred.setOnClickListener {
            dialog.dismiss()
            confirmShredSingleFile(item)
        }

        var mediaPlayer: MediaPlayer? = null
        val handler = Handler(Looper.getMainLooper())
        var updateProgressRunnable: Runnable? = null

        when (item.type) {
            EvidenceType.PHOTO, EvidenceType.SCREENSHOT -> {
                dialogBinding.ivPhotoViewer.visibility = View.VISIBLE
                val bitmap = decodeSampledBitmap(item.file, 1080, 1920)
                if (bitmap != null) {
                    dialogBinding.ivPhotoViewer.setImageBitmap(bitmap)
                } else {
                    Toast.makeText(this, "Failed rendering image buffer.", Toast.LENGTH_SHORT).show()
                }
            }

            EvidenceType.VIDEO -> {
                dialogBinding.vvVideoPlayer.visibility = View.VISIBLE
                val videoUri = Uri.fromFile(item.file)
                val mediaController = MediaController(this)
                mediaController.setAnchorView(dialogBinding.vvVideoPlayer)
                dialogBinding.vvVideoPlayer.setMediaController(mediaController)
                dialogBinding.vvVideoPlayer.setVideoURI(videoUri)
                dialogBinding.vvVideoPlayer.setOnPreparedListener {
                    dialogBinding.vvVideoPlayer.start()
                }
            }

            EvidenceType.AUDIO -> {
                dialogBinding.layoutAudioPlayer.visibility = View.VISIBLE
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@EvidenceGalleryActivity, Uri.fromFile(item.file))
                    prepare()
                }

                val totalDuration = mediaPlayer.duration
                dialogBinding.seekAudioProgress.max = totalDuration
                dialogBinding.tvAudioTimer.text = "00:00 / ${formatAudioTime(totalDuration)}"

                dialogBinding.btnAudioPlayToggle.setOnClickListener {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.pause()
                        dialogBinding.btnAudioPlayToggle.text = "Play Audio"
                    } else {
                        mediaPlayer.start()
                        dialogBinding.btnAudioPlayToggle.text = "Pause Audio"
                    }
                }

                updateProgressRunnable = object : Runnable {
                    override fun run() {
                        if (mediaPlayer.isPlaying) {
                            val currentPos = mediaPlayer.currentPosition
                            dialogBinding.seekAudioProgress.progress = currentPos
                            dialogBinding.tvAudioTimer.text = "${formatAudioTime(currentPos)} / ${formatAudioTime(totalDuration)}"
                        }
                        handler.postDelayed(this, 250)
                    }
                }
                handler.post(updateProgressRunnable)

                dialogBinding.seekAudioProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            mediaPlayer.seekTo(progress)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }

            EvidenceType.VAULT_DNG -> {
                dialogBinding.scrollVaultText.visibility = View.VISIBLE
                dialogBinding.tvDecodedVaultContent.text = "Vault container encoded as DNG RAW photo.\nInitiating hardware cryptographic decoding step..."

                lifecycleScope.launch {
                    val label = SecurityPreferences.getVaultSecretLabel(this@EvidenceGalleryActivity)
                    val decrypted = withContext(Dispatchers.IO) {
                        PlausibleDeniabilityVault.extractSecretBlob(this@EvidenceGalleryActivity, label)
                    }

                    if (decrypted != null) {
                        val textContent = String(decrypted, Charsets.UTF_8)
                        NativeSecurityBridge.zeroByteArray(decrypted)
                        dialogBinding.tvDecodedVaultContent.text = "--- DECRYPTED VAULT PAYLOAD (Anchor: $label) ---\n\n$textContent"
                    } else {
                        dialogBinding.tvDecodedVaultContent.text = "Decryption failed: Carrier DNG file missing or StrongBox KeyMint master suicide key revoked."
                    }
                }
            }

            else -> {
                Toast.makeText(this, "Unsupported file format.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.setOnDismissListener {
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer.stop()
                }
                mediaPlayer?.release()
            } catch (_: Exception) {}
            updateProgressRunnable?.let { handler.removeCallbacks(it) }
        }

        dialog.show()
    }

    private fun formatAudioTime(millis: Int): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            null
        }
    }

    private fun shareMediaFile(item: EvidenceFileItem) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                item.file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = when (item.type) {
                    EvidenceType.PHOTO, EvidenceType.SCREENSHOT -> "image/*"
                    EvidenceType.VIDEO -> "video/*"
                    EvidenceType.AUDIO -> "audio/*"
                    else -> "*/*"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Export Evidence File"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmShredSingleFile(item: EvidenceFileItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm File Shred")
            .setMessage("Permanently zero-fill and delete '${item.displayName}'? This file will be unrecoverable.")
            .setPositiveButton("Shred Now") { _, _ ->
                shredFile(item.file)
                loadEvidenceFiles()
                Toast.makeText(this, "File zeroed and shredded.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmShredAll() {
        if (allEvidenceItems.isEmpty()) {
            Toast.makeText(this, "No evidence files to shred.", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ SHRED ALL EVIDENCE FILES ⚠️")
            .setMessage("This will zero-fill and unlink all ${allEvidenceItems.size} recorded photos, videos, audio clips, and screenshots. Proceed?")
            .setPositiveButton("SHRED ALL NOW") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        for (item in allEvidenceItems) {
                            shredFile(item.file)
                        }
                    }
                    loadEvidenceFiles()
                    Toast.makeText(this@EvidenceGalleryActivity, "All evidence files permanently wiped.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Abort", null)
            .show()
    }

    private fun shredFile(file: File) {
        try {
            if (file.exists() && file.canWrite()) {
                val len = file.length()
                val zeroBytes = ByteArray(4096)
                RandomAccessFile(file, "rws").use { raf ->
                    var written = 0L
                    while (written < len) {
                        val toWrite = minOf(zeroBytes.size.toLong(), len - written).toInt()
                        raf.write(zeroBytes, 0, toWrite)
                        written += toWrite
                    }
                    raf.fd.sync()
                }
                file.delete()
            }
        } catch (_: Exception) {
            file.delete()
        }
    }

    inner class EvidenceMediaAdapter(
        private val items: List<EvidenceFileItem>,
        private val onOpen: (EvidenceFileItem) -> Unit,
        private val onShare: (EvidenceFileItem) -> Unit,
        private val onShred: (EvidenceFileItem) -> Unit
    ) : RecyclerView.Adapter<EvidenceMediaAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemEvidenceMediaBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemEvidenceMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.binding.tvMediaFilename.text = item.displayName
            holder.binding.tvMediaDetails.text = "${item.dateFormatted} • ${item.sizeFormatted}"

            when (item.type) {
                EvidenceType.PHOTO -> holder.binding.ivMediaTypeIcon.setImageResource(R.drawable.ic_hp_camera)
                EvidenceType.VIDEO -> holder.binding.ivMediaTypeIcon.setImageResource(R.drawable.ic_video_24)
                EvidenceType.AUDIO -> holder.binding.ivMediaTypeIcon.setImageResource(R.drawable.ic_hp_mic)
                EvidenceType.SCREENSHOT -> holder.binding.ivMediaTypeIcon.setImageResource(R.drawable.ic_touch_app_24)
                EvidenceType.VAULT_DNG -> holder.binding.ivMediaTypeIcon.setImageResource(R.drawable.ic_lock_24)
                else -> holder.binding.ivMediaTypeIcon.setImageResource(R.drawable.ic_info_24)
            }

            if (item.isEncrypted) {
                holder.binding.chipEncryptionStatus.visibility = View.VISIBLE
                holder.binding.btnOpenMedia.text = "Decode Vault"
            } else {
                holder.binding.chipEncryptionStatus.visibility = View.GONE
                holder.binding.btnOpenMedia.text = if (item.type == EvidenceType.AUDIO || item.type == EvidenceType.VIDEO) "Play" else "View"
            }

            holder.binding.btnOpenMedia.setOnClickListener { onOpen(item) }
            holder.binding.btnShareMedia.setOnClickListener { onShare(item) }
            holder.binding.btnShredMedia.setOnClickListener { onShred(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}

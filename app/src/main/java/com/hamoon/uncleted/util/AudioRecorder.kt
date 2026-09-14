package com.hamoon.uncleted.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object AudioRecorder {
    private const val TAG = "AudioRecorder"
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

    suspend fun recordAudio(context: Context, durationSeconds: Int): File? {
        if (!PermissionUtils.hasRecordAudioPermission(context)) {
            Log.e(TAG, "RECORD_AUDIO permission not granted.")
            return null
        }

        // Correct container extension: MPEG-4 / AAC must be saved as .m4a
        val audioFile = File(
            context.filesDir,
            "AUD_${SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())}.m4a"
        )

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            Log.i(TAG, "Ambient audio recording started. Duration: ${durationSeconds}s")
            delay(durationSeconds * 1000L)
            try {
                recorder.stop()
            } catch (stopEx: RuntimeException) {
                Log.w(TAG, "Recording stopped prematurely: ${stopEx.message}")
            }
            Log.i(TAG, "Ambient audio recording finished. File: ${audioFile.absolutePath}")
            audioFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record audio", e)
            null
        } finally {
            try {
                recorder.reset()
                recorder.release()
            } catch (_: Exception) {}
        }
    }
}
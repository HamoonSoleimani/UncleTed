package com.hamoon.uncleted.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

object CameraHandler {
    private const val TAG = "CameraHandler"
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

    private suspend fun getCameraProvider(context: Context): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                if (continuation.isActive) {
                    continuation.resume(providerFuture.get())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera provider", e)
                if (continuation.isActive) {
                    continuation.cancel(e)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    suspend fun takePhoto(context: Context, lifecycleOwner: LifecycleOwner, lensFacing: Int): File? = withContext(Dispatchers.Main) {
        try {
            val cameraProvider = getCameraProvider(context)
            val imageCapture = ImageCapture.Builder().build()

            try { cameraProvider.unbindAll() } catch (_: Exception) {}

            val cameraSelector = try {
                CameraSelector.Builder().requireLensFacing(lensFacing).build()
            } catch (e: Exception) {
                Log.e(TAG, "Camera lens $lensFacing not available.", e)
                return@withContext null
            }

            if (!cameraProvider.hasCamera(cameraSelector)) {
                Log.e(TAG, "No camera found for selector.")
                return@withContext null
            }

            val camera = try {
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)
            } catch (bindEx: Exception) {
                Log.e(TAG, "bindToLifecycle rejected by CameraService: ${bindEx.message}")
                return@withContext null
            }

            val photoFile = File(
                context.filesDir,
                "IMG_${SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())}.jpg"
            )

            return@withContext suspendCancellableCoroutine { continuation ->
                // Monitor camera state transitions to fail fast if rejected by CameraService
                // (e.g. validateClientPermissionsLocked: Callers from device user are not allowed)
                camera.cameraInfo.cameraState.observe(lifecycleOwner) { state ->
                    if (state.type == CameraState.Type.CLOSED && state.error != null) {
                        Log.e(TAG, "Camera closed with error: ${state.error?.code}. Aborting capture.")
                        if (continuation.isActive) continuation.resume(null)
                    }
                }

                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(photoFile).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            Log.i(TAG, "Photo capture succeeded: ${outputFileResults.savedUri}")
                            if (continuation.isActive) continuation.resume(photoFile)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not take photo: ${e.message}", e)
            return@withContext null
        } finally {
            try { getCameraProvider(context).unbindAll() } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun recordVideo(context: Context, lifecycleOwner: LifecycleOwner, durationSeconds: Int, lensFacing: Int): File? = withContext(Dispatchers.Main) {
        try {
            val cameraProvider = getCameraProvider(context)

            try { cameraProvider.unbindAll() } catch (_: Exception) {}

            val qualitySelector = QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = try {
                CameraSelector.Builder().requireLensFacing(lensFacing).build()
            } catch (e: Exception) {
                Log.e(TAG, "Camera lens $lensFacing not available for video.", e)
                return@withContext null
            }

            if (!cameraProvider.hasCamera(cameraSelector)) {
                Log.e(TAG, "No camera found for video selector.")
                return@withContext null
            }

            val camera = try {
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, videoCapture)
            } catch (bindEx: Exception) {
                Log.e(TAG, "bindToLifecycle for video rejected: ${bindEx.message}")
                return@withContext null
            }

            val videoFile = File(
                context.filesDir,
                "VID_${SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())}.mp4"
            )

            return@withContext suspendCancellableCoroutine { continuation ->
                camera.cameraInfo.cameraState.observe(lifecycleOwner) { state ->
                    if (state.type == CameraState.Type.CLOSED && state.error != null) {
                        Log.e(TAG, "Camera closed during video init: ${state.error?.code}")
                        if (continuation.isActive) continuation.resume(null)
                    }
                }

                var recording: Recording? = null

                val listener = androidx.core.util.Consumer<VideoRecordEvent> { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> {
                            Log.i(TAG, "Video recording started. Timer set for ${durationSeconds}s.")
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (recordEvent.hasError()) {
                                Log.e(TAG, "Video capture error: ${recordEvent.error}", recordEvent.cause)
                                if (continuation.isActive) continuation.resume(null)
                            } else {
                                Log.i(TAG, "Video capture succeeded: ${recordEvent.outputResults.outputUri}")
                                if (continuation.isActive) continuation.resume(videoFile)
                            }
                        }
                    }
                }

                try {
                    recording = videoCapture.output
                        .prepareRecording(context, FileOutputOptions.Builder(videoFile).build())
                        .withAudioEnabled()
                        .start(ContextCompat.getMainExecutor(context), listener)
                } catch (se: SecurityException) {
                    Log.e(TAG, "Missing permission for video recording", se)
                    if (continuation.isActive) continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                CoroutineScope(Dispatchers.Main).launch {
                    delay(durationSeconds * 1000L)
                    try {
                        recording?.stop()
                        Log.d(TAG, "Stop signal sent to recorder.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping recording", e)
                    }
                }

                continuation.invokeOnCancellation {
                    try { recording?.stop() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not record video: ${e.message}", e)
            return@withContext null
        } finally {
            try { getCameraProvider(context).unbindAll() } catch (_: Exception) {}
        }
    }
}
package com.hamoon.uncleted.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

object CameraHandler {
    private const val TAG = "CameraHandler"
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    private const val CAMERA_OPEN_TIMEOUT_MS = 6000L
    private const val HAL_COOLDOWN_DELAY_MS = 400L

    // Global mutex to prevent concurrent captures from destroying each other's sessions
    private val hardwareCameraLock = Mutex()

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

    suspend fun takePhoto(context: Context, lifecycleOwner: LifecycleOwner, lensFacing: Int): File? = hardwareCameraLock.withLock {
        withContext(Dispatchers.Main) {
            var cameraProvider: ProcessCameraProvider? = null
            try {
                cameraProvider = getCameraProvider(context)
                try { cameraProvider.unbindAll() } catch (_: Exception) {}
                delay(150)

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = try {
                    CameraSelector.Builder().requireLensFacing(lensFacing).build()
                } catch (e: Exception) {
                    Log.e(TAG, "Camera lens $lensFacing not available.", e)
                    return@withContext null
                }

                if (!cameraProvider.hasCamera(cameraSelector)) {
                    Log.e(TAG, "No camera found for selector: $lensFacing")
                    return@withContext null
                }

                val camera = try {
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)
                } catch (bindEx: Exception) {
                    Log.e(TAG, "bindToLifecycle rejected by CameraService: ${bindEx.message}")
                    return@withContext null
                }

                val isCameraReady = withTimeoutOrNull(CAMERA_OPEN_TIMEOUT_MS) {
                    suspendCancellableCoroutine<Boolean> { continuation ->
                        val currentState = camera.cameraInfo.cameraState.value
                        if (currentState?.type == CameraState.Type.OPEN) {
                            if (continuation.isActive) continuation.resume(true)
                            return@suspendCancellableCoroutine
                        }

                        val observer = object : Observer<CameraState> {
                            override fun onChanged(value: CameraState) {
                                if (value.type == CameraState.Type.OPEN) {
                                    camera.cameraInfo.cameraState.removeObserver(this)
                                    if (continuation.isActive) continuation.resume(true)
                                } else if (value.type == CameraState.Type.CLOSED && value.error != null) {
                                    camera.cameraInfo.cameraState.removeObserver(this)
                                    if (continuation.isActive) continuation.resume(false)
                                }
                            }
                        }
                        camera.cameraInfo.cameraState.observe(lifecycleOwner, observer)
                        continuation.invokeOnCancellation {
                            camera.cameraInfo.cameraState.removeObserver(observer)
                        }
                    }
                } ?: false

                if (!isCameraReady) {
                    Log.e(TAG, "Camera hardware failed to enter OPEN state within timeout. Aborting still capture.")
                    return@withContext null
                }

                delay(250)

                val photoFile = File(
                    context.filesDir,
                    "IMG_${SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())}.jpg"
                )

                return@withContext suspendCancellableCoroutine { continuation ->
                    imageCapture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(photoFile).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                Log.i(TAG, "Photo capture succeeded: ${outputFileResults.savedUri ?: photoFile.absolutePath}")
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
                try { cameraProvider?.unbindAll() } catch (_: Exception) {}
                delay(HAL_COOLDOWN_DELAY_MS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun recordVideo(context: Context, lifecycleOwner: LifecycleOwner, durationSeconds: Int, lensFacing: Int): File? = hardwareCameraLock.withLock {
        withContext(Dispatchers.Main) {
            var cameraProvider: ProcessCameraProvider? = null
            try {
                cameraProvider = getCameraProvider(context)
                try { cameraProvider.unbindAll() } catch (_: Exception) {}
                delay(150)

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

                val isCameraReady = withTimeoutOrNull(CAMERA_OPEN_TIMEOUT_MS) {
                    suspendCancellableCoroutine<Boolean> { continuation ->
                        val currentState = camera.cameraInfo.cameraState.value
                        if (currentState?.type == CameraState.Type.OPEN) {
                            if (continuation.isActive) continuation.resume(true)
                            return@suspendCancellableCoroutine
                        }

                        val observer = object : Observer<CameraState> {
                            override fun onChanged(value: CameraState) {
                                if (value.type == CameraState.Type.OPEN) {
                                    camera.cameraInfo.cameraState.removeObserver(this)
                                    if (continuation.isActive) continuation.resume(true)
                                } else if (value.type == CameraState.Type.CLOSED && value.error != null) {
                                    camera.cameraInfo.cameraState.removeObserver(this)
                                    if (continuation.isActive) continuation.resume(false)
                                }
                            }
                        }
                        camera.cameraInfo.cameraState.observe(lifecycleOwner, observer)
                        continuation.invokeOnCancellation {
                            camera.cameraInfo.cameraState.removeObserver(observer)
                        }
                    }
                } ?: false

                if (!isCameraReady) {
                    Log.e(TAG, "Camera device failed to enter OPEN state for recording.")
                    return@withContext null
                }

                delay(250)

                val videoFile = File(
                    context.filesDir,
                    "VID_${SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())}.mp4"
                )

                return@withContext suspendCancellableCoroutine { continuation ->
                    var recording: Recording? = null
                    var isFinalized = false

                    val listener = androidx.core.util.Consumer<VideoRecordEvent> { recordEvent ->
                        when (recordEvent) {
                            is VideoRecordEvent.Start -> {
                                Log.i(TAG, "Video recording stream actively producing data. Timer set for ${durationSeconds}s.")
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(durationSeconds * 1000L)
                                    if (!isFinalized) {
                                        val activeRecording = recording
                                        if (activeRecording != null) {
                                            try {
                                                activeRecording.stop()
                                                Log.d(TAG, "Stop command sent to active recording.")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error stopping recording: ${e.message}")
                                            }
                                        }
                                    }
                                }
                            }
                            is VideoRecordEvent.Finalize -> {
                                isFinalized = true
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
                        val pendingRecording = videoCapture.output
                            .prepareRecording(context, FileOutputOptions.Builder(videoFile).build())

                        if (PermissionUtils.hasRecordAudioPermission(context)) {
                            pendingRecording.withAudioEnabled()
                        }

                        recording = pendingRecording.start(ContextCompat.getMainExecutor(context), listener)
                    } catch (se: SecurityException) {
                        Log.e(TAG, "Missing permission for video recording", se)
                        if (continuation.isActive) continuation.resume(null)
                        return@suspendCancellableCoroutine
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed starting video recording pipeline: ${e.message}", e)
                        if (continuation.isActive) continuation.resume(null)
                        return@suspendCancellableCoroutine
                    }

                    continuation.invokeOnCancellation {
                        if (!isFinalized) {
                            val currentRecording = recording
                            if (currentRecording != null) {
                                try { currentRecording.stop() } catch (_: Exception) {}
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not record video: ${e.message}", e)
                return@withContext null
            } finally {
                try { cameraProvider?.unbindAll() } catch (_: Exception) {}
                delay(HAL_COOLDOWN_DELAY_MS)
            }
        }
    }
}
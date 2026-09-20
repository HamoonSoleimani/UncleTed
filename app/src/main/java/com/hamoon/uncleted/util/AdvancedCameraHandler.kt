package com.hamoon.uncleted.util

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.lifecycle.LifecycleOwner
import com.hamoon.uncleted.CameraPermissionBrokerActivity
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

object AdvancedCameraHandler {

    private const val TAG = "AdvancedCameraHandler"
    private const val CAPTURE_TIMEOUT_MS = 25000L
    private const val CAMERA_SWITCH_HAL_COOLDOWN_MS = 450L

    data class CameraCapture(
        val frontPhoto: File?,
        val backPhoto: File?,
        val frontVideo: File?,
        val backVideo: File?,
        val location: Location?,
        val timestamp: Long = System.currentTimeMillis()
    )

    suspend fun performFullCapture(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        videoDurationSeconds: Int = 10,
        requestId: Long = 0L
    ): CameraCapture = withContext(Dispatchers.IO) {

        if (SecurityPreferences.isStealthMediaCaptureEnabled(context)) {
            RootActions.suppressPrivacyIndicators(true)
        }

        val frontEnabled = SecurityPreferences.isFrontCameraCaptureEnabled(context)
        val backEnabled = SecurityPreferences.isBackCameraCaptureEnabled(context)

        try {
            val location = getCurrentLocation(context)

            val frontPhoto = if (frontEnabled) {
                Log.i(TAG, "Initiating front camera photo capture...")
                withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                    CameraHandler.takePhoto(context, lifecycleOwner, CameraSelector.LENS_FACING_FRONT)
                }
            } else null

            delay(CAMERA_SWITCH_HAL_COOLDOWN_MS)

            val backPhoto = if (backEnabled && hasBackCamera(context)) {
                Log.i(TAG, "Initiating back camera photo capture...")
                withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                    CameraHandler.takePhoto(context, lifecycleOwner, CameraSelector.LENS_FACING_BACK)
                }
            } else null

            delay(CAMERA_SWITCH_HAL_COOLDOWN_MS)

            val frontVideo = if (frontEnabled && videoDurationSeconds > 0) {
                Log.i(TAG, "Initiating front camera video capture ($videoDurationSeconds s)...")
                withTimeoutOrNull((videoDurationSeconds + 12) * 1000L) {
                    CameraHandler.recordVideo(
                        context, lifecycleOwner, videoDurationSeconds, CameraSelector.LENS_FACING_FRONT
                    )
                }
            } else null

            delay(CAMERA_SWITCH_HAL_COOLDOWN_MS)

            val backVideo = if (backEnabled && videoDurationSeconds > 0 && hasBackCamera(context)) {
                Log.i(TAG, "Initiating back camera video capture ($videoDurationSeconds s)...")
                withTimeoutOrNull((videoDurationSeconds + 12) * 1000L) {
                    CameraHandler.recordVideo(
                        context, lifecycleOwner, videoDurationSeconds, CameraSelector.LENS_FACING_BACK
                    )
                }
            } else null

            return@withContext CameraCapture(
                frontPhoto = frontPhoto,
                backPhoto = backPhoto,
                frontVideo = frontVideo,
                backVideo = backVideo,
                location = location
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing camera capture pipeline: ${e.message}", e)
            return@withContext CameraCapture(null, null, null, null, null)
        } finally {
            Log.d(TAG, "Camera capture routine completed.")
            try {
                val completionIntent = Intent(CameraPermissionBrokerActivity.ACTION_MEDIA_CAPTURE_COMPLETED).apply {
                    setPackage(context.packageName)
                    putExtra(CameraPermissionBrokerActivity.EXTRA_REQUEST_ID, requestId)
                }
                context.sendBroadcast(completionIntent)
                Log.d(TAG, "Dispatched ACTION_MEDIA_CAPTURE_COMPLETED broadcast with requestId: $requestId")
            } catch (broadcastEx: Exception) {
                Log.e(TAG, "Failed to broadcast capture completion: ${broadcastEx.message}")
            }
        }
    }

    private fun hasBackCamera(context: Context): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.cameraIdList.any { cameraId ->
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for back camera", e)
            false
        }
    }

    private suspend fun getCurrentLocation(context: Context): Location? {
        return try {
            LocationTracker.getCurrentLocationDetailed(context)?.location
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }
}

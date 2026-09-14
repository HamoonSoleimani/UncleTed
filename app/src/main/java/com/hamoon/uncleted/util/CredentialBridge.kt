package com.hamoon.uncleted.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object CredentialBridge {

    private const val TAG = "CredentialBridge"
    private const val CONFIG_DIR = "/data/system/uncleted"
    private const val CONFIG_FILE = "$CONFIG_DIR/credentials.cfg"

    /**
     * Synchronizes Wipe, Duress, Honeypot PINs and native Decoy User ID to the platform storage partition
     * accessible by system_server before and after first unlock (BFU compatible).
     */
    suspend fun syncCredentials(
        context: Context,
        wipePin: String?,
        duressPin: String?,
        honeypotPin: String? = null,
        decoyUserId: Int = -1
    ): Boolean = withContext(Dispatchers.IO) {
        val content = buildString {
            append("wipe_pin=").append(wipePin ?: "").append("\n")
            append("duress_pin=").append(duressPin ?: "").append("\n")
            append("honeypot_pin=").append(honeypotPin ?: "").append("\n")
            append("decoy_user_id=").append(decoyUserId).append("\n")
            append("updated_at=").append(System.currentTimeMillis()).append("\n")
        }

        // Must use Device-Protected (DE) storage context so temp file writes succeed Before First Unlock (BFU)
        val deContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }

        val tempFile = File(deContext.cacheDir, "credentials.tmp")
        try {
            FileOutputStream(tempFile).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write temp credentials file: ${e.message}")
            return@withContext false
        }

        if (RootChecker.isDeviceRooted()) {
            val script = listOf(
                "mkdir -p $CONFIG_DIR",
                "cp -f ${tempFile.absolutePath} $CONFIG_FILE",
                "chown 1000:1000 $CONFIG_DIR $CONFIG_FILE",
                "chmod 755 $CONFIG_DIR",
                "chmod 644 $CONFIG_FILE",
                "chcon u:object_r:system_data_file:s0 $CONFIG_DIR 2>/dev/null || true",
                "chcon u:object_r:system_data_file:s0 $CONFIG_FILE 2>/dev/null || true",
                "rm -f ${tempFile.absolutePath}"
            )

            val results = RootExecutor.runMultiple(script)
            val success = results.any { it.isSuccess }

            if (success) {
                Log.i(TAG, "Successfully synced credentials and Decoy User ID to $CONFIG_FILE.")
                EventLogger.log(context, "Credentials and Decoy User ID synced to platform partition.")
                return@withContext true
            } else {
                Log.w(TAG, "Root copy command failed.")
            }
        }

        try {
            val dir = File(CONFIG_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(CONFIG_FILE)
            FileOutputStream(file).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            file.setReadable(true, false)
            tempFile.delete()
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Direct platform write failed: ${e.message}")
        }

        return@withContext false
    }

    suspend fun syncCredentials(context: Context): Boolean {
        val wipe = SecurityPreferences.getWipePin(context)
        val duress = SecurityPreferences.getDuressPin(context)
        val honeypot = SecurityPreferences.getHoneypotPin(context)
        val decoyId = SecurityPreferences.getDecoyUserId(context)
        return syncCredentials(context, wipe, duress, honeypot, decoyId)
    }

    suspend fun clearCredentials(context: Context): Boolean = withContext(Dispatchers.IO) {
        EventLogger.log(context, "Platform credentials bridge cleared.")
        val deContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
        val tempFile = File(deContext.cacheDir, "credentials.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        if (RootChecker.isDeviceRooted()) {
            RootExecutor.run("rm -f $CONFIG_FILE", logErrors = false)
            return@withContext true
        }
        try {
            val file = File(CONFIG_FILE)
            if (file.exists()) file.delete()
            return@withContext true
        } catch (_: Exception) {
            return@withContext false
        }
    }
}
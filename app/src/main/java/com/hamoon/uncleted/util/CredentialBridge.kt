package com.hamoon.uncleted.util

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom

object CredentialBridge {

    private const val TAG = "CredentialBridge"
    private const val CONFIG_DIR = "/data/system/uncleted"
    private const val CONFIG_FILE = "$CONFIG_DIR/credentials.cfg"
    private const val SALT_BYTES = 16

    private val syncMutex = Mutex()

    /**
     * Synchronizes credentials to /data/system/uncleted/credentials.cfg safely with mutex locking
     * and unique temporary file descriptors to prevent concurrency collisions during boot.
     */
    suspend fun syncCredentials(
        context: Context,
        wipePin: String?,
        duressPin: String?,
        honeypotPin: String? = null,
        decoyUserId: Int = -1
    ): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val random = SecureRandom()

            val (wipeHash, wipeSalt) = hashPin(wipePin, random)
            val (duressHash, duressSalt) = hashPin(duressPin, random)
            val (honeypotHash, honeypotSalt) = hashPin(honeypotPin, random)

            val content = buildString {
                append("wipe_pin_hash=").append(wipeHash).append("\n")
                append("wipe_pin_salt=").append(wipeSalt).append("\n")
                append("duress_pin_hash=").append(duressHash).append("\n")
                append("duress_pin_salt=").append(duressSalt).append("\n")
                append("honeypot_pin_hash=").append(honeypotHash).append("\n")
                append("honeypot_pin_salt=").append(honeypotSalt).append("\n")

                append("wipe_pin=").append(wipePin ?: "").append("\n")
                append("duress_pin=").append(duressPin ?: "").append("\n")
                append("honeypot_pin=").append(honeypotPin ?: "").append("\n")

                append("decoy_user_id=").append(decoyUserId).append("\n")
                append("updated_at=").append(System.currentTimeMillis()).append("\n")
            }

            val deContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }

            val uniqueTmpName = "credentials_${System.nanoTime()}_${random.nextInt(100000)}.tmp"
            val tempFile = File(deContext.cacheDir, uniqueTmpName)

            try {
                FileOutputStream(tempFile).use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing temporary platform credentials: ${e.message}")
                return@withLock false
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
                    Log.i(TAG, "Credentials bridge synced successfully to $CONFIG_FILE.")
                    EventLogger.log(context, "BRIDGE: Credentials synced to platform partition.")
                    return@withLock true
                } else {
                    Log.w(TAG, "Root execution failed while deploying bridge credentials.")
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
                return@withLock true
            } catch (e: Exception) {
                Log.e(TAG, "Direct platform credentials write failed: ${e.message}")
            }

            return@withLock false
        }
    }

    suspend fun syncCredentials(context: Context): Boolean {
        val wipe = SecurityPreferences.getWipePin(context)
        val duress = SecurityPreferences.getDuressPin(context)
        val honeypot = SecurityPreferences.getHoneypotPin(context)
        val decoyId = SecurityPreferences.getDecoyUserId(context)
        return syncCredentials(context, wipe, duress, honeypot, decoyId)
    }

    suspend fun clearCredentials(context: Context): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            EventLogger.log(context, "BRIDGE: Clearing platform credential hashes.")
            val deContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }

            deContext.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("credentials_") && file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }

            if (RootChecker.isDeviceRooted()) {
                RootExecutor.run("rm -f $CONFIG_FILE", logErrors = false)
                return@withLock true
            }
            try {
                val file = File(CONFIG_FILE)
                if (file.exists()) file.delete()
                return@withLock true
            } catch (_: Exception) {
                return@withLock false
            }
        }
    }

    private fun hashPin(pin: String?, random: SecureRandom): Pair<String, String> {
        if (pin.isNullOrEmpty()) {
            return Pair("", "")
        }

        val saltBytes = ByteArray(SALT_BYTES)
        random.nextBytes(saltBytes)

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltBytes)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        digest.update(saltBytes)
        val hashBytes = digest.digest()

        val hashBase64 = Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        val saltBase64 = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        return Pair(hashBase64, saltBase64)
    }
}
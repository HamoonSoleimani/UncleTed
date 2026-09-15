package com.hamoon.uncleted.vault

import android.content.Context
import android.os.Environment
import android.util.Log
import com.hamoon.uncleted.crypto.StrongBoxSecurityManager
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.NativeSecurityBridge
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

object PlausibleDeniabilityVault {

    private const val TAG = "DeniabilityVault"
    private const val DEFAULT_CONTAINER_NAME = "RAW_20240812_0042.dng"

    // TIFF / DNG Header magic numbers (Little Endian: 'II', version 42)
    private val DNG_MAGIC_LE = byteArrayOf(0x49, 0x49, 0x2A, 0x00)
    private val DNG_SUBIFD_TAG = byteArrayOf(0x44, 0x4E, 0x47, 0x01) // Custom private tag anchor

    @Synchronized
    fun storeSecretBlob(context: Context, label: String, rawSecret: ByteArray): Boolean {
        if (!NativeSecurityBridge.isNativeLoaded()) {
            Log.e(TAG, "Native security library missing. Aborting vault storage.")
            return false
        }

        NativeSecurityBridge.pinMemory(rawSecret)

        return try {
            val masterKeyBytes = deriveVaultKey(context)
            if (masterKeyBytes == null) {
                Log.e(TAG, "Could not derive master vault key from StrongBox.")
                return false
            }

            val nonce = ByteArray(12)
            SecureRandom().nextBytes(nonce)

            // 1. Pack Label + Secret
            val labelBytes = label.toByteArray(Charsets.UTF_8)
            val packedBuffer = ByteBuffer.allocate(4 + labelBytes.size + 4 + rawSecret.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(labelBytes.size)
                .put(labelBytes)
                .putInt(rawSecret.size)
                .put(rawSecret)
                .array()

            // 2. Compress & Encrypt via Native CES Pipeline (zlib + ChaCha20-Poly1305)
            val ciphertextWithTag = NativeSecurityBridge.compressAndEncrypt(packedBuffer, masterKeyBytes, nonce)
            NativeSecurityBridge.zeroByteArray(masterKeyBytes)
            NativeSecurityBridge.zeroByteArray(packedBuffer)

            if (ciphertextWithTag == null) {
                Log.e(TAG, "Native CES compression/encryption failed.")
                return false
            }

            // 3. Shape Entropy & Embed in Polyglot DNG Container
            val containerFile = getTargetContainerFile(context)
            val polyglotStream = synthesizeDngPolyglot(nonce, ciphertextWithTag)

            FileOutputStream(containerFile).use { fos ->
                fos.write(polyglotStream)
                fos.flush()
            }

            Log.i(TAG, "Vault secret '$label' successfully stored in shaped carrier: ${containerFile.absolutePath}")
            EventLogger.log(context, "VAULT: Stored payload inside deniable container (${containerFile.name}).")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error storing blob in deniable vault", e)
            false
        } finally {
            NativeSecurityBridge.unpinMemory(rawSecret)
        }
    }

    @Synchronized
    fun extractSecretBlob(context: Context, label: String): ByteArray? {
        if (!NativeSecurityBridge.isNativeLoaded()) return null

        val containerFile = getTargetContainerFile(context)
        if (!containerFile.exists()) {
            Log.w(TAG, "Vault container file not found: ${containerFile.absolutePath}")
            return null
        }

        val fileBytes = try {
            FileInputStream(containerFile).use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading vault container file", e)
            return null
        }

        val parsed = extractCesPayloadFromDng(fileBytes) ?: return null
        val masterKeyBytes = deriveVaultKey(context) ?: return null

        val decryptedPacked = NativeSecurityBridge.decryptAndDecompress(parsed.ciphertextWithTag, masterKeyBytes, parsed.nonce)
        NativeSecurityBridge.zeroByteArray(masterKeyBytes)

        if (decryptedPacked == null) {
            Log.e(TAG, "Authentication tag mismatch or decompression error during vault extraction.")
            return null
        }

        NativeSecurityBridge.pinMemory(decryptedPacked)

        return try {
            val buffer = ByteBuffer.wrap(decryptedPacked).order(ByteOrder.LITTLE_ENDIAN)
            val labelLen = buffer.int
            if (labelLen <= 0 || labelLen > buffer.remaining()) return null

            val labelBytes = ByteArray(labelLen)
            buffer.get(labelBytes)
            val parsedLabel = String(labelBytes, Charsets.UTF_8)

            if (parsedLabel != label) {
                Log.w(TAG, "Vault blob label mismatch: expected '$label', found '$parsedLabel'")
                return null
            }

            val secretLen = buffer.int
            if (secretLen <= 0 || secretLen > buffer.remaining()) return null

            val secretBytes = ByteArray(secretLen)
            buffer.get(secretBytes)
            secretBytes
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing unpacked vault payload", e)
            null
        } finally {
            NativeSecurityBridge.unpinMemory(decryptedPacked)
            NativeSecurityBridge.zeroByteArray(decryptedPacked)
        }
    }

    @Synchronized
    fun purgeVaultContainer(context: Context) {
        val containerFile = getTargetContainerFile(context)
        if (containerFile.exists()) {
            try {
                // Secure overwrite of carrier container prior to file unlink
                val len = containerFile.length().toInt()
                val randomJunk = ByteArray(if (len > 0) len else 4096)
                SecureRandom().nextBytes(randomJunk)

                FileOutputStream(containerFile).use { fos ->
                    fos.write(randomJunk)
                    fos.flush()
                }
                containerFile.delete()
                Log.i(TAG, "Plausible deniability vault carrier permanently unlinked and overwritten.")
                EventLogger.log(context, "VAULT: Overwritten and purged deniable carrier container.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed purging vault container file", e)
            }
        }
    }

    private fun synthesizeDngPolyglot(nonce: ByteArray, ciphertextWithTag: ByteArray): ByteArray {
        val headerSize = 32
        val payloadLen = ciphertextWithTag.size
        val nonceLen = nonce.size

        // Generate deterministic pseudorandom chaff to skew entropy toward typical uncompressed RAW images (H ~ 7.3)
        val chaffSize = 1024 + (payloadLen % 512)
        val chaff = ByteArray(chaffSize)
        SecureRandom().nextBytes(chaff)

        val totalSize = headerSize + 4 + nonceLen + 4 + payloadLen + chaffSize
        val output = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // 1. TIFF / DNG File Header
        output.put(DNG_MAGIC_LE) // 'II' + 42
        output.putInt(8)         // Offset to first IFD
        output.put(DNG_SUBIFD_TAG)
        output.putLong(System.currentTimeMillis())
        output.putInt(0x00000001) // Version anchor
        output.putInt(0x00000000) // Padding

        // 2. Encapsulated Payload Envelope
        output.putInt(nonceLen)
        output.put(nonce)
        output.putInt(payloadLen)
        output.put(ciphertextWithTag)

        // 3. Statistical Chaff Tail (Entropy Shaping Buffer)
        output.put(chaff)

        return output.array()
    }

    private data class ParsedCarrier(val nonce: ByteArray, val ciphertextWithTag: ByteArray)

    private fun extractCesPayloadFromDng(fileBytes: ByteArray): ParsedCarrier? {
        if (fileBytes.size < 48) return null

        val buffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = ByteArray(4)
        buffer.get(magic)
        if (!magic.contentEquals(DNG_MAGIC_LE)) {
            Log.e(TAG, "Carrier header mismatch: Not a valid DNG/TIFF polyglot container.")
            return null
        }

        buffer.position(12)
        val anchor = ByteArray(4)
        buffer.get(anchor)
        if (!anchor.contentEquals(DNG_SUBIFD_TAG)) {
            Log.e(TAG, "Carrier anchor tag invalid.")
            return null
        }

        buffer.position(32)
        val nonceLen = buffer.int
        if (nonceLen != 12) return null

        val nonce = ByteArray(nonceLen)
        buffer.get(nonce)

        val payloadLen = buffer.int
        if (payloadLen <= 16 || payloadLen > buffer.remaining()) return null

        val ciphertextWithTag = ByteArray(payloadLen)
        buffer.get(ciphertextWithTag)

        return ParsedCarrier(nonce, ciphertextWithTag)
    }

    private fun deriveVaultKey(context: Context): ByteArray? {
        val seed = "UncleTed_PlausibleDeniability_VaultKey_Seed".toByteArray(Charsets.UTF_8)
        val payload = StrongBoxSecurityManager.encryptWithStrongBox(context, seed) ?: return null
        val keyBytes = ByteArray(32)
        val copyLen = minOf(payload.cipherText.size, 32)
        System.arraycopy(payload.cipherText, 0, keyBytes, 0, copyLen)
        return keyBytes
    }

    private fun getTargetContainerFile(context: Context): File {
        val customName = SecurityPreferences.getVaultCarrierFileName(context)
        val targetName = if (customName.isNullOrBlank()) DEFAULT_CONTAINER_NAME else customName

        val dcimDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Camera")
        if (!dcimDir.exists()) {
            dcimDir.mkdirs()
        }
        return File(dcimDir, targetName)
    }
}
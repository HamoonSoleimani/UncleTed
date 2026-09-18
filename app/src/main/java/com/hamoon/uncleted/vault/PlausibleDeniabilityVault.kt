package com.hamoon.uncleted.vault

import android.content.Context
import android.os.Environment
import android.util.Log
import com.hamoon.uncleted.crypto.CryptoPreferences
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.NativeSecurityBridge
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.Inflater

object PlausibleDeniabilityVault {

    private const val TAG = "DeniabilityVault"
    private const val DEFAULT_CONTAINER_NAME = "RAW_20240812_0042.dng"

    private val TIFF_MAGIC_LE = byteArrayOf(0x49, 0x49, 0x2A, 0x00) // "II*\0"
    private const val PRIVATE_PAYLOAD_TAG_ID: Short = 0xC634.toShort() // DNG PrivateData Tag
    private val VAULT_HKDF_INFO = "UncleTed_DNG_Polyglot_Vault_v2".toByteArray(Charsets.UTF_8)
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val KEY_SIZE = 32

    private val lock = Any()

    @Synchronized
    fun storeSecretBlob(context: Context, label: String, rawSecret: ByteArray): Boolean {
        synchronized(lock) {
            NativeSecurityBridge.pinMemory(rawSecret)

            return try {
                val masterSeed = CryptoPreferences.getOrGenerateVaultMasterSeed(context)
                if (masterSeed == null) {
                    Log.e(TAG, "Failed retrieving master vault seed from hardware Keystore.")
                    return false
                }

                val salt = ByteArray(SALT_SIZE)
                val nonce = ByteArray(NONCE_SIZE)
                SecureRandom().nextBytes(salt)
                SecureRandom().nextBytes(nonce)

                val derivedKey = deriveKeyFromSeed(masterSeed, salt)
                NativeSecurityBridge.zeroByteArray(masterSeed)

                val labelBytes = label.toByteArray(Charsets.UTF_8)
                val packedBuffer = ByteBuffer.allocate(4 + labelBytes.size + 4 + rawSecret.size)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(labelBytes.size)
                    .put(labelBytes)
                    .putInt(rawSecret.size)
                    .put(rawSecret)
                    .array()

                val ciphertextWithTag = if (NativeSecurityBridge.isNativeLoaded()) {
                    NativeSecurityBridge.compressAndEncrypt(packedBuffer, derivedKey, nonce)
                } else {
                    compressAndEncryptFallback(packedBuffer, derivedKey, nonce)
                }

                NativeSecurityBridge.zeroByteArray(derivedKey)
                NativeSecurityBridge.zeroByteArray(packedBuffer)

                if (ciphertextWithTag == null) {
                    Log.e(TAG, "Compression and encryption pipeline failed during vault store.")
                    return false
                }

                val containerFile = getTargetContainerFile(context)
                val polyglotDngStream = synthesizeAuthenticDng(salt, nonce, ciphertextWithTag)

                FileOutputStream(containerFile).use { fos ->
                    fos.write(polyglotDngStream)
                    fos.flush()
                }

                Log.i(TAG, "Vault secret '$label' successfully stored in container: ${containerFile.absolutePath}")
                EventLogger.log(context, "VAULT: Stored secret '$label' inside plausible container (${containerFile.name}).")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error storing secret in vault", e)
                false
            } finally {
                NativeSecurityBridge.unpinMemory(rawSecret)
            }
        }
    }

    @Synchronized
    fun extractSecretBlob(context: Context, label: String): ByteArray? {
        synchronized(lock) {
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

            val parsed = extractPayloadFromDng(fileBytes)
            if (parsed == null) {
                Log.e(TAG, "Failed to parse valid DNG structure or metadata tag from carrier.")
                return null
            }

            val masterSeed = CryptoPreferences.getOrGenerateVaultMasterSeed(context)
            if (masterSeed == null) {
                Log.e(TAG, "Failed retrieving master vault seed from hardware Keystore.")
                return null
            }

            val derivedKey = deriveKeyFromSeed(masterSeed, parsed.salt)
            NativeSecurityBridge.zeroByteArray(masterSeed)

            val decryptedPacked = if (NativeSecurityBridge.isNativeLoaded()) {
                NativeSecurityBridge.decryptAndDecompress(parsed.ciphertextWithTag, derivedKey, parsed.nonce)
            } else {
                decryptAndDecompressFallback(parsed.ciphertextWithTag, derivedKey, parsed.nonce)
            }

            NativeSecurityBridge.zeroByteArray(derivedKey)

            if (decryptedPacked == null) {
                Log.e(TAG, "Decryption authentication failed or tag mismatch during extraction.")
                return null
            }

            NativeSecurityBridge.pinMemory(decryptedPacked)

            return try {
                val buffer = ByteBuffer.wrap(decryptedPacked).order(ByteOrder.LITTLE_ENDIAN)
                val labelLen = buffer.int
                if (labelLen <= 0 || labelLen > buffer.remaining()) {
                    Log.w(TAG, "Malformed label length in decrypted payload.")
                    return null
                }

                val labelBytes = ByteArray(labelLen)
                buffer.get(labelBytes)
                val parsedLabel = String(labelBytes, Charsets.UTF_8)

                if (parsedLabel != label) {
                    Log.w(TAG, "Vault blob label mismatch: expected '$label', found '$parsedLabel'")
                    return null
                }

                val secretLen = buffer.int
                if (secretLen <= 0 || secretLen > buffer.remaining()) {
                    Log.w(TAG, "Malformed secret length in decrypted payload.")
                    return null
                }

                val secretBytes = ByteArray(secretLen)
                buffer.get(secretBytes)
                NativeSecurityBridge.pinMemory(secretBytes)
                secretBytes
            } catch (e: Exception) {
                Log.e(TAG, "Error unpacking extracted vault payload", e)
                null
            } finally {
                NativeSecurityBridge.unpinMemory(decryptedPacked)
                NativeSecurityBridge.zeroByteArray(decryptedPacked)
            }
        }
    }

    @Synchronized
    fun purgeVaultContainer(context: Context) {
        synchronized(lock) {
            val containerFile = getTargetContainerFile(context)
            if (containerFile.exists()) {
                try {
                    val len = containerFile.length().toInt()
                    val randomJunk = ByteArray(if (len > 0) len else 8192)
                    SecureRandom().nextBytes(randomJunk)

                    FileOutputStream(containerFile).use { fos ->
                        fos.write(randomJunk)
                        fos.flush()
                    }
                    containerFile.delete()
                    Log.i(TAG, "Plausible deniability vault container sanitized and unlinked.")
                    EventLogger.log(context, "VAULT: Overwritten and unlinked deniable carrier container.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed purging vault container file", e)
                }
            }
        }
    }

    private fun deriveKeyFromSeed(masterSeed: ByteArray, salt: ByteArray): ByteArray {
        val derivedKey = ByteArray(KEY_SIZE)
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(masterSeed, salt, VAULT_HKDF_INFO))
        hkdf.generateBytes(derivedKey, 0, KEY_SIZE)
        return derivedKey
    }

    private fun synthesizeAuthenticDng(salt: ByteArray, nonce: ByteArray, ciphertextWithTag: ByteArray): ByteArray {
        val payloadEnvelope = ByteBuffer.allocate(4 + salt.size + 4 + nonce.size + 4 + ciphertextWithTag.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(salt.size)
            .put(salt)
            .putInt(nonce.size)
            .put(nonce)
            .putInt(ciphertextWithTag.size)
            .put(ciphertextWithTag)
            .array()

        val sensorChaffSize = 4096 + (ciphertextWithTag.size % 1024)
        val sensorChaff = ByteArray(sensorChaffSize)
        SecureRandom().nextBytes(sensorChaff)

        val ifdOffset = 8
        val numEntries: Short = 7
        val ifdSize = 2 + (numEntries * 12) + 4

        val payloadOffset = ifdOffset + ifdSize
        val payloadLen = payloadEnvelope.size

        val totalSize = payloadOffset + payloadLen + sensorChaffSize
        val output = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // TIFF Header
        output.put(TIFF_MAGIC_LE)
        output.putInt(ifdOffset)

        // IFD0 Entries
        output.putShort(numEntries)
        writeIfdEntry(output, 0x00FE.toShort(), 4, 1, 0) // NewSubfileType
        writeIfdEntry(output, 0x0100.toShort(), 3, 1, 4032) // ImageWidth
        writeIfdEntry(output, 0x0101.toShort(), 3, 1, 3024) // ImageLength
        writeIfdEntry(output, 0x0102.toShort(), 3, 1, 16) // BitsPerSample
        writeIfdEntry(output, 0x0103.toShort(), 3, 1, 1) // Compression (Uncompressed)
        writeIfdEntry(output, 0xC612.toShort(), 1, 4, 0x01040000) // DNGVersion
        writeIfdEntry(output, PRIVATE_PAYLOAD_TAG_ID, 7, payloadLen, payloadOffset) // DNGPrivateData
        output.putInt(0) // Next IFD Offset (None)

        // Payload & Sensor Chaff
        output.put(payloadEnvelope)
        output.put(sensorChaff)

        return output.array()
    }

    private fun writeIfdEntry(buffer: ByteBuffer, tag: Short, type: Short, count: Int, valueOrOffset: Int) {
        buffer.putShort(tag)
        buffer.putShort(type)
        buffer.putInt(count)
        buffer.putInt(valueOrOffset)
    }

    private data class ParsedCarrier(
        val salt: ByteArray,
        val nonce: ByteArray,
        val ciphertextWithTag: ByteArray
    )

    private fun extractPayloadFromDng(fileBytes: ByteArray): ParsedCarrier? {
        if (fileBytes.size < 64) return null

        val buffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = ByteArray(4)
        buffer.get(magic)
        if (!magic.contentEquals(TIFF_MAGIC_LE)) {
            Log.e(TAG, "Carrier header mismatch: Not a Little-Endian TIFF/DNG structure.")
            return null
        }

        val ifdOffset = buffer.int
        if (ifdOffset <= 0 || ifdOffset >= fileBytes.size - 2) return null

        buffer.position(ifdOffset)
        val numEntries = buffer.short
        if (numEntries <= 0 || numEntries > 100) return null

        var payloadOffset = -1
        var payloadLength = -1

        for (i in 0 until numEntries) {
            val tag = buffer.short
            buffer.short // type
            val count = buffer.int
            val valueOrOffset = buffer.int

            if (tag == PRIVATE_PAYLOAD_TAG_ID) {
                payloadLength = count
                payloadOffset = valueOrOffset
                break
            }
        }

        if (payloadOffset <= 0 || payloadLength <= (4 + SALT_SIZE + 4 + NONCE_SIZE + 4) || payloadOffset + payloadLength > fileBytes.size) {
            Log.e(TAG, "DNG PrivateData payload tag not found or out of bounds.")
            return null
        }

        buffer.position(payloadOffset)

        val saltLen = buffer.int
        if (saltLen != SALT_SIZE) return null
        val salt = ByteArray(saltLen)
        buffer.get(salt)

        val nonceLen = buffer.int
        if (nonceLen != NONCE_SIZE) return null
        val nonce = ByteArray(nonceLen)
        buffer.get(nonce)

        val cipherLen = buffer.int
        if (cipherLen <= 16 || cipherLen > buffer.remaining()) return null
        val ciphertextWithTag = ByteArray(cipherLen)
        buffer.get(ciphertextWithTag)

        return ParsedCarrier(salt, nonce, ciphertextWithTag)
    }

    private fun getTargetContainerFile(context: Context): File {
        val customName = SecurityPreferences.getVaultCarrierFileName(context)
        val targetName = if (customName.isBlank()) DEFAULT_CONTAINER_NAME else customName

        val externalPictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val cameraDir = File(externalPictures, "Camera")
        if (!cameraDir.exists()) {
            cameraDir.mkdirs()
        }

        return if (cameraDir.exists() && cameraDir.canWrite()) {
            File(cameraDir, targetName)
        } else {
            val internalCamera = File(context.filesDir, "Camera")
            if (!internalCamera.exists()) internalCamera.mkdirs()
            File(internalCamera, targetName)
        }
    }

    private fun compressAndEncryptFallback(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray? {
        return try {
            val deflater = Deflater(Deflater.BEST_COMPRESSION)
            deflater.setInput(plaintext)
            deflater.finish()

            val compressedStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                compressedStream.write(buffer, 0, count)
            }
            deflater.end()
            val compressedData = compressedStream.toByteArray()

            val cipher = ChaCha20Poly1305()
            cipher.init(true, ParametersWithIV(KeyParameter(key), nonce))

            val output = ByteArray(cipher.getOutputSize(compressedData.size))
            val len = cipher.processBytes(compressedData, 0, compressedData.size, output, 0)
            cipher.doFinal(output, len)

            output
        } catch (e: Exception) {
            Log.e(TAG, "Fallback encryption failed", e)
            null
        }
    }

    private fun decryptAndDecompressFallback(ciphertextWithTag: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray? {
        return try {
            val cipher = ChaCha20Poly1305()
            cipher.init(false, ParametersWithIV(KeyParameter(key), nonce))

            val decryptedCompressed = ByteArray(cipher.getOutputSize(ciphertextWithTag.size))
            val len = cipher.processBytes(ciphertextWithTag, 0, ciphertextWithTag.size, decryptedCompressed, 0)
            cipher.doFinal(decryptedCompressed, len)

            val inflater = Inflater()
            inflater.setInput(decryptedCompressed)

            val decompressedStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                decompressedStream.write(buffer, 0, count)
            }
            inflater.end()

            decompressedStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Fallback decryption failed", e)
            null
        }
    }
}
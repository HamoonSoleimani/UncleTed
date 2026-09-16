package com.hamoon.uncleted.canary

import android.content.Context
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.hamoon.uncleted.crypto.PostQuantumEngine
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.NativeSecurityBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater

object CovertCanarySender {

    private const val TAG = "CovertCanarySender"
    private val MEDIA_TYPE_OHTTP = "message/bhttp".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun dispatchCovertDuress(
        context: Context,
        triggerReason: String,
        location: Location? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!SecurityPreferences.isOhttpCanaryEnabled(context)) {
            Log.d(TAG, "Covert OHTTP canary disabled in preferences.")
            return@withContext false
        }

        val relayUrl = SecurityPreferences.getOhttpRelayUrl(context)
        val gatewayPubKeyBase64 = SecurityPreferences.getOhttpGatewayPublicKey(context)

        if (gatewayPubKeyBase64.isNullOrBlank()) {
            Log.w(TAG, "Covert canary gateway public key not configured; skipping OHTTP dispatch.")
            return@withContext false
        }

        val recipientPublicKey = PostQuantumEngine.HybridPublicKey.decodeFromBase64(gatewayPubKeyBase64)
        if (recipientPublicKey == null) {
            Log.e(TAG, "Corrupted OHTTP gateway hybrid public key in preferences.")
            return@withContext false
        }

        Log.i(TAG, "Formulating RFC 9458 Oblivious HTTP covert duress canary payload...")

        val profile = SecurityPreferences.getOhttpMasqueradeProfile(context)
        val telemetryJson = synthesizeTelemetryPayload(context, triggerReason, location, profile)
        val plainBytes = telemetryJson.toByteArray(Charsets.UTF_8)
        NativeSecurityBridge.pinMemory(plainBytes)

        val encapsulatedPacket = try {
            val encapsulation = PostQuantumEngine.encapsulate(recipientPublicKey)
            if (encapsulation == null) {
                Log.e(TAG, "Hybrid KEM encapsulation failed during canary formulation.")
                return@withContext false
            }

            val nonce = ByteArray(12)
            SecureRandom().nextBytes(nonce)

            val ciphertextWithTag = if (NativeSecurityBridge.isNativeLoaded()) {
                NativeSecurityBridge.compressAndEncrypt(
                    plainBytes,
                    encapsulation.sharedKey256,
                    nonce
                )
            } else {
                compressAndEncryptFallback(plainBytes, encapsulation.sharedKey256, nonce)
            }

            NativeSecurityBridge.zeroByteArray(encapsulation.sharedKey256)

            if (ciphertextWithTag == null) {
                Log.e(TAG, "Encryption of canary payload failed.")
                return@withContext false
            }

            // Pack binary OHTTP frame:
            // [2-byte Wire Encapsulation Length] + [Wire Encapsulation] + [12-byte Nonce] + [Ciphertext + Tag]
            val encapLen = encapsulation.wireCiphertext.size
            val frameBytes = ByteArray(2 + encapLen + 12 + ciphertextWithTag.size)

            frameBytes[0] = ((encapLen shr 8) and 0xFF).toByte()
            frameBytes[1] = (encapLen and 0xFF).toByte()

            System.arraycopy(encapsulation.wireCiphertext, 0, frameBytes, 2, encapLen)
            System.arraycopy(nonce, 0, frameBytes, 2 + encapLen, 12)
            System.arraycopy(ciphertextWithTag, 0, frameBytes, 2 + encapLen + 12, ciphertextWithTag.size)

            frameBytes
        } catch (e: Exception) {
            Log.e(TAG, "Error assembling OHTTP frame", e)
            return@withContext false
        } finally {
            NativeSecurityBridge.unpinMemory(plainBytes)
            NativeSecurityBridge.zeroByteArray(plainBytes)
        }

        val requestBody = encapsulatedPacket.toRequestBody(MEDIA_TYPE_OHTTP)

        val requestBuilder = Request.Builder()
            .url(relayUrl)
            .post(requestBody)
            .header("Accept", "message/bhttp")
            .header("Accept-Encoding", "gzip, deflate, br")

        if (profile == "firebase_analytics") {
            requestBuilder
                .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 14; Build/UP1A.231005.007)")
                .header("X-Android-Package", "com.google.android.gms")
                .header("X-Firebase-Client", "fire-analytics/21.5.0")
        } else {
            requestBuilder
                .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 14; Pixel 8 Build/UD1A.230805.019)")
                .header("X-Unity-Version", "2022.3.10f1")
                .header("X-Android-Package", "com.google.android.gms")
        }

        return@withContext try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val code = response.code
                if (response.isSuccessful || code == 200 || code == 204) {
                    Log.i(TAG, "Covert OHTTP canary successfully dispatched through CDN relay ($relayUrl). Status: $code")
                    EventLogger.log(context, "CANARY: Covert OHTTP distress packet transmitted via CDN relay ($code).")
                    true
                } else {
                    Log.w(TAG, "OHTTP relay returned non-success response code: $code")
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Covert OHTTP dispatch failed (relay offline or blocked): ${e.message}")
            false
        }
    }

    suspend fun dispatchTestProbe(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val relayUrl = SecurityPreferences.getOhttpRelayUrl(context)
        val gatewayKey = SecurityPreferences.getOhttpGatewayPublicKey(context)

        if (gatewayKey.isNullOrBlank()) {
            return@withContext Pair(false, "Gateway Hybrid Public Key is missing.")
        }

        if (PostQuantumEngine.HybridPublicKey.decodeFromBase64(gatewayKey) == null) {
            return@withContext Pair(false, "Gateway Hybrid Public Key format is invalid.")
        }

        val success = dispatchCovertDuress(context, "OPERATOR_MANUAL_TEST_PROBE", null)
        return@withContext if (success) {
            Pair(true, "Test canary packet accepted by CDN relay: $relayUrl")
        } else {
            Pair(false, "Relay unreachable or rejected packet. Check network connection and URL.")
        }
    }

    private fun synthesizeTelemetryPayload(
        context: Context,
        reason: String,
        location: Location?,
        profile: String
    ): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        val root = JSONObject().apply {
            put("event_type", if (profile == "firebase_analytics") "app_exception_trace" else "diagnostic_trace_telemetry")
            put("client_timestamp", System.currentTimeMillis())
            put("elapsed_realtime", SystemClock.elapsedRealtime())
            put("device_model", Build.MODEL)
            put("device_fingerprint", Build.FINGERPRINT)
            put("battery_level", batteryLevel)

            val payloadObj = JSONObject().apply {
                put("alert_trigger", reason)
                if (location != null) {
                    val locObj = JSONObject().apply {
                        put("lat", location.latitude)
                        put("lon", location.longitude)
                        put("accuracy", location.accuracy.toDouble())
                        put("altitude", if (location.hasAltitude()) location.altitude else 0.0)
                        put("speed", if (location.hasSpeed()) location.speed.toDouble() else 0.0)
                        put("time", location.time)
                    }
                    put("location", locObj)
                } else {
                    put("location", JSONObject.NULL)
                }
            }
            put("payload", payloadObj)
        }
        return root.toString()
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
            Log.e(TAG, "Fallback canary encryption failed", e)
            null
        }
    }
}
package com.hamoon.uncleted.canary

import android.content.Context
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.util.Base64
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
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

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

        val telemetryJson = synthesizeTelemetryPayload(context, triggerReason, location)
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

            // Compress & Encrypt payload via Native CES Engine (RFC 8439 ChaCha20-Poly1305 + Deflate)
            val ciphertextWithTag = NativeSecurityBridge.compressAndEncrypt(
                plainBytes,
                encapsulation.sharedKey256,
                nonce
            )
            NativeSecurityBridge.zeroByteArray(encapsulation.sharedKey256)

            if (ciphertextWithTag == null) {
                Log.e(TAG, "Native encryption of canary payload failed.")
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

        // Transmit opaque binary body over TLS 1.3 to OHTTP CDN relay
        val requestBody = encapsulatedPacket.toRequestBody(MEDIA_TYPE_OHTTP)

        val request = Request.Builder()
            .url(relayUrl)
            .post(requestBody)
            // Emulate authentic Android telemetry request headers
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 14; Pixel 8 Build/UD1A.230805.019)")
            .header("Accept", "message/bhttp")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("X-Unity-Version", "2022.3.10f1")
            .header("X-Android-Package", "com.google.android.gms")
            .build()

        return@withContext try {
            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                if (response.isSuccessful || code == 200 || code == 204) {
                    Log.i(TAG, "Covert OHTTP canary successfully dispatched through CDN relay ($relayUrl). Status: $code")
                    EventLogger.log(context, "CANARY: Covert OHTTP distress packet transmitted via CDN relay.")
                    true
                } else {
                    Log.w(TAG, "OHTTP relay returned non-success response code: $code")
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Covert OHTTP dispatch failed (network offline or relay blocked): ${e.message}")
            false
        }
    }

    private fun synthesizeTelemetryPayload(
        context: Context,
        reason: String,
        location: Location?
    ): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        val root = JSONObject().apply {
            put("event_type", "diagnostic_trace_telemetry")
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
}
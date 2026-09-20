package com.hamoon.uncleted.crypto

import android.content.Context
import android.util.Base64
import android.util.Log
import com.hamoon.uncleted.util.NativeSecurityBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.math.ec.ECPoint
import org.json.JSONObject
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

object OprfClientEngine {

    private const val TAG = "OprfClientEngine"
    private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

    private val ecParameters = SECNamedCurves.getByName("secp256r1")
    private val curve = ecParameters.curve
    private val groupOrderN: BigInteger = ecParameters.n
    private val generatorG: ECPoint = ecParameters.g

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    data class BlindingContext(
        val blindScalar: BigInteger,
        val blindedPoint: ECPoint
    )

    /**
     * Blinds the user's PIN:
     * 1. Hash PIN to a non-zero scalar: s = SHA256(PIN) mod N
     * 2. Map to group element: M = G * s
     * 3. Pick random scalar r in [1, N-1]
     * 4. Blinded point: B = M * r
     */
    fun blindPin(pin: String): BlindingContext {
        val digest = MessageDigest.getInstance("SHA-256")
        val pinHashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        var s = BigInteger(1, pinHashBytes).mod(groupOrderN)
        if (s == BigInteger.ZERO) {
            s = BigInteger.ONE
        }

        val mPoint = generatorG.multiply(s).normalize()

        val random = SecureRandom()
        var r: BigInteger
        do {
            r = BigInteger(256, random).mod(groupOrderN)
        } while (r <= BigInteger.ONE)

        val blindedPoint = mPoint.multiply(r).normalize()
        return BlindingContext(r, blindedPoint)
    }

    /**
     * Executes blind handshake with remote OPRF evaluation sentinel.
     * Server calculates: E = B * k_remote
     * Client unblinds: S = E * (r^-1 mod N) = (M * r * k_remote) * r^-1 = M * k_remote
     */
    suspend fun evaluateRemoteOprf(context: Context, pin: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!OprfPreferences.isOprfEnabled(context)) {
            Log.d(TAG, "OPRF key decoupling is disabled in settings.")
            return@withContext null
        }

        val serverUrl = OprfPreferences.getServerUrl(context)
        val blindingCtx = blindPin(pin)

        try {
            val blindedEncoded = blindingCtx.blindedPoint.getEncoded(true)
            val blindedBase64 = Base64.encodeToString(blindedEncoded, Base64.NO_WRAP)

            val jsonPayload = JSONObject().apply {
                put("blinded_element", blindedBase64)
                put("timestamp", System.currentTimeMillis())
            }

            val request = Request.Builder()
                .url(serverUrl)
                .post(jsonPayload.toString().toRequestBody(MEDIA_TYPE_JSON))
                .header("User-Agent", "UncleTed-OPRF/v10.0.1")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "OPRF evaluation failed. Server returned HTTP ${response.code}. Failing closed.")
                return@withContext null
            }

            val responseBody = response.body?.string() ?: return@withContext null
            val respJson = JSONObject(responseBody)
            val evaluatedBase64 = respJson.getString("evaluated_element")
            val evaluatedBytes = Base64.decode(evaluatedBase64, Base64.NO_WRAP)

            val evaluatedPoint = curve.decodePoint(evaluatedBytes).normalize()

            // Unblind: r^-1 mod N
            val rInverse = blindingCtx.blindScalar.modInverse(groupOrderN)
            val unblindedPoint = evaluatedPoint.multiply(rInverse).normalize()

            val sharedSecretBytes = unblindedPoint.getEncoded(true)
            val outputDigest = MessageDigest.getInstance("SHA-256")
            val prfOutput = outputDigest.digest(sharedSecretBytes)

            NativeSecurityBridge.pinMemory(prfOutput)
            return@withContext prfOutput
        } catch (e: Exception) {
            Log.e(TAG, "OPRF network/crypto failure: ${e.message}. Failing closed.")
            return@withContext null
        }
    }

    /**
     * Combines the local key share with the remote OPRF evaluation:
     * K_master = HKDF-SHA512(K_local XOR OPRF(PIN, K_remote))
     * Fails closed: returns null if remote server is unreachable.
     */
    suspend fun deriveDecoupledMasterKey(context: Context, pin: String): ByteArray? {
        val oprfOutput = evaluateRemoteOprf(context, pin)
        if (oprfOutput == null) {
            Log.e(TAG, "OPRF handshake failed or was rejected. Refusing key delivery (Fail-Closed).")
            return null
        }

        val localShareHex = OprfPreferences.getLocalKeyShare(context)
        val localShare = if (!localShareHex.isNullOrBlank()) {
            hexToBytes(localShareHex)
        } else {
            val freshShare = ByteArray(32)
            SecureRandom().nextBytes(freshShare)
            OprfPreferences.setLocalKeyShare(context, bytesToHex(freshShare))
            freshShare
        }

        NativeSecurityBridge.pinMemory(localShare)

        val masterSecret = ByteArray(32)
        for (i in 0 until 32) {
            masterSecret[i] = (localShare[i].toInt() xor oprfOutput[i].toInt()).toByte()
        }

        NativeSecurityBridge.zeroByteArray(oprfOutput)
        NativeSecurityBridge.unpinMemory(oprfOutput)
        NativeSecurityBridge.zeroByteArray(localShare)
        NativeSecurityBridge.unpinMemory(localShare)

        val derivedKey = ByteArray(32)
        val hkdf = HKDFBytesGenerator(SHA512Digest())
        hkdf.init(HKDFParameters(masterSecret, "UncleTed_OPRF_Domain_Salt_v2".toByteArray(Charsets.UTF_8), "Master_Decoupled_Key".toByteArray(Charsets.UTF_8)))
        hkdf.generateBytes(derivedKey, 0, 32)

        NativeSecurityBridge.zeroByteArray(masterSecret)
        NativeSecurityBridge.pinMemory(derivedKey)
        return derivedKey
    }

    private fun hexToBytes(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789ABCDEF"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            result.append(hexChars[i shr 4])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }
}

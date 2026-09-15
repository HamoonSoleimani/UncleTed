package com.hamoon.uncleted.crypto

import android.util.Base64
import android.util.Log
import com.hamoon.uncleted.util.NativeSecurityBridge
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberKEMExtractor
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberKEMGenerator
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberKeyGenerationParameters
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberKeyPairGenerator
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberParameters
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberPrivateKeyParameters
import org.bouncycastle.pqc.crypto.crystals.kyber.KyberPublicKeyParameters
import java.security.SecureRandom

object PostQuantumEngine {

    private const val TAG = "PostQuantumEngine"
    private const val X25519_KEY_SIZE = 32
    private val HKDF_SALT = "UncleTed_FIPS203_MLKEM768_X25519_Salt".toByteArray(Charsets.UTF_8)
    private val HKDF_INFO = "UncleTed_Master_Symmetric_Vault_Secret".toByteArray(Charsets.UTF_8)

    data class HybridPublicKey(
        val x25519Public: ByteArray,
        val kyberPublic: ByteArray
    ) {
        fun encodeToBase64(): String {
            val combined = ByteArray(4 + x25519Public.size + kyberPublic.size)
            combined[0] = ((x25519Public.size shr 24) and 0xFF).toByte()
            combined[1] = ((x25519Public.size shr 16) and 0xFF).toByte()
            combined[2] = ((x25519Public.size shr 8) and 0xFF).toByte()
            combined[3] = (x25519Public.size and 0xFF).toByte()

            System.arraycopy(x25519Public, 0, combined, 4, x25519Public.size)
            System.arraycopy(kyberPublic, 0, combined, 4 + x25519Public.size, kyberPublic.size)

            return Base64.encodeToString(combined, Base64.NO_WRAP)
        }

        companion object {
            fun decodeFromBase64(base64Str: String): HybridPublicKey? {
                return try {
                    val raw = Base64.decode(base64Str, Base64.NO_WRAP)
                    if (raw.size < 36) return null

                    val xLen = ((raw[0].toInt() and 0xFF) shl 24) or
                            ((raw[1].toInt() and 0xFF) shl 16) or
                            ((raw[2].toInt() and 0xFF) shl 8) or
                            (raw[3].toInt() and 0xFF)

                    if (xLen != X25519_KEY_SIZE || raw.size <= 4 + xLen) return null

                    val xBytes = ByteArray(xLen)
                    System.arraycopy(raw, 4, xBytes, 0, xLen)

                    val kLen = raw.size - 4 - xLen
                    val kBytes = ByteArray(kLen)
                    System.arraycopy(raw, 4 + xLen, kBytes, 0, kLen)

                    HybridPublicKey(xBytes, kBytes)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed decoding hybrid public key", e)
                    null
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as HybridPublicKey
            if (!x25519Public.contentEquals(other.x25519Public)) return false
            if (!kyberPublic.contentEquals(other.kyberPublic)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = x25519Public.contentHashCode()
            result = 31 * result + kyberPublic.contentHashCode()
            return result
        }
    }

    data class HybridPrivateKey(
        val x25519Private: ByteArray,
        val kyberPrivate: ByteArray
    ) {
        fun destroy() {
            NativeSecurityBridge.zeroByteArray(x25519Private)
            NativeSecurityBridge.zeroByteArray(kyberPrivate)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as HybridPrivateKey
            if (!x25519Private.contentEquals(other.x25519Private)) return false
            if (!kyberPrivate.contentEquals(other.kyberPrivate)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = x25519Private.contentHashCode()
            result = 31 * result + kyberPrivate.contentHashCode()
            return result
        }
    }

    data class HybridKeyPair(
        val public: HybridPublicKey,
        val private: HybridPrivateKey
    )

    data class HybridEncapsulation(
        val sharedKey256: ByteArray,
        val wireCiphertext: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as HybridEncapsulation
            if (!sharedKey256.contentEquals(other.sharedKey256)) return false
            if (!wireCiphertext.contentEquals(other.wireCiphertext)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = sharedKey256.contentHashCode()
            result = 31 * result + wireCiphertext.contentHashCode()
            return result
        }
    }

    fun generateHybridKeyPair(): HybridKeyPair {
        val random = SecureRandom()

        // 1. Classical Layer: Curve25519 (X25519)
        val x25519Gen = X25519KeyPairGenerator()
        x25519Gen.init(X25519KeyGenerationParameters(random))
        val x25519Pair = x25519Gen.generateKeyPair()
        val xPub = (x25519Pair.public as X25519PublicKeyParameters).encoded
        val xPriv = (x25519Pair.private as X25519PrivateKeyParameters).encoded

        // 2. Post-Quantum Layer: NIST FIPS 203 ML-KEM-768 (Kyber768)
        val kyberGen = KyberKeyPairGenerator()
        kyberGen.init(KyberKeyGenerationParameters(random, KyberParameters.kyber768))
        val kyberPair = kyberGen.generateKeyPair()
        val kPub = (kyberPair.public as KyberPublicKeyParameters).encoded
        val kPriv = (kyberPair.private as KyberPrivateKeyParameters).encoded

        return HybridKeyPair(
            public = HybridPublicKey(xPub, kPub),
            private = HybridPrivateKey(xPriv, kPriv)
        )
    }

    fun encapsulate(recipientKey: HybridPublicKey): HybridEncapsulation? {
        val random = SecureRandom()

        return try {
            // 1. Ephemeral X25519 Derivation
            val ephemeralXGen = X25519KeyPairGenerator()
            ephemeralXGen.init(X25519KeyGenerationParameters(random))
            val ephemeralXPair = ephemeralXGen.generateKeyPair()
            val ephemeralXPub = (ephemeralXPair.public as X25519PublicKeyParameters).encoded
            val ephemeralXPriv = ephemeralXPair.private as X25519PrivateKeyParameters

            val agreement = X25519Agreement()
            agreement.init(ephemeralXPriv)
            val recipientXPubParams = X25519PublicKeyParameters(recipientKey.x25519Public, 0)
            val classicalSecret = ByteArray(agreement.agreementSize)
            agreement.calculateAgreement(recipientXPubParams, classicalSecret, 0)

            // 2. Post-Quantum ML-KEM-768 Encapsulation
            val recipientKyberParams = KyberPublicKeyParameters(KyberParameters.kyber768, recipientKey.kyberPublic)
            val kyberKemGen = KyberKEMGenerator(random)
            val kyberSecretWithEncapsulation = kyberKemGen.generateEncapsulated(recipientKyberParams)
            val pqSecret = kyberSecretWithEncapsulation.secret
            val pqCiphertext = kyberSecretWithEncapsulation.encapsulation

            // 3. Combine Secrets via HKDF-SHA512
            val combinedSecrets = ByteArray(classicalSecret.size + pqSecret.size)
            System.arraycopy(classicalSecret, 0, combinedSecrets, 0, classicalSecret.size)
            System.arraycopy(pqSecret, 0, combinedSecrets, classicalSecret.size, pqSecret.size)

            val derivedMasterKey = ByteArray(32)
            val hkdf = HKDFBytesGenerator(SHA512Digest())
            hkdf.init(HKDFParameters(combinedSecrets, HKDF_SALT, HKDF_INFO))
            hkdf.generateBytes(derivedMasterKey, 0, derivedMasterKey.size)

            // Zero sensitive intermediate secrets
            NativeSecurityBridge.zeroByteArray(classicalSecret)
            NativeSecurityBridge.zeroByteArray(pqSecret)
            NativeSecurityBridge.zeroByteArray(combinedSecrets)

            // Wire packet: [32-byte ephemeral X25519 public key] + [ML-KEM-768 ciphertext]
            val wireBytes = ByteArray(ephemeralXPub.size + pqCiphertext.size)
            System.arraycopy(ephemeralXPub, 0, wireBytes, 0, ephemeralXPub.size)
            System.arraycopy(pqCiphertext, 0, wireBytes, ephemeralXPub.size, pqCiphertext.size)

            HybridEncapsulation(
                sharedKey256 = derivedMasterKey,
                wireCiphertext = wireBytes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Hybrid KEM encapsulation failed", e)
            null
        }
    }

    fun decapsulate(wireCiphertext: ByteArray, privateKey: HybridPrivateKey): ByteArray? {
        if (wireCiphertext.size <= X25519_KEY_SIZE) {
            Log.e(TAG, "Malformed hybrid wire ciphertext: buffer too short.")
            return null
        }

        return try {
            // 1. Recover Ephemeral X25519 Public Key
            val ephemeralXPubBytes = ByteArray(X25519_KEY_SIZE)
            System.arraycopy(wireCiphertext, 0, ephemeralXPubBytes, 0, X25519_KEY_SIZE)

            val agreement = X25519Agreement()
            val myXPrivParams = X25519PrivateKeyParameters(privateKey.x25519Private, 0)
            agreement.init(myXPrivParams)
            val ephemeralXPubParams = X25519PublicKeyParameters(ephemeralXPubBytes, 0)
            val classicalSecret = ByteArray(agreement.agreementSize)
            agreement.calculateAgreement(ephemeralXPubParams, classicalSecret, 0)

            // 2. Extract ML-KEM-768 Shared Secret
            val pqCipherLen = wireCiphertext.size - X25519_KEY_SIZE
            val pqCiphertext = ByteArray(pqCipherLen)
            System.arraycopy(wireCiphertext, X25519_KEY_SIZE, pqCiphertext, 0, pqCipherLen)

            val myKyberPrivParams = KyberPrivateKeyParameters(KyberParameters.kyber768, privateKey.kyberPrivate)
            val extractor = KyberKEMExtractor(myKyberPrivParams)
            val pqSecret = extractor.extractSecret(pqCiphertext)

            // 3. Combine Secrets via HKDF-SHA512
            val combinedSecrets = ByteArray(classicalSecret.size + pqSecret.size)
            System.arraycopy(classicalSecret, 0, combinedSecrets, 0, classicalSecret.size)
            System.arraycopy(pqSecret, 0, combinedSecrets, classicalSecret.size, pqSecret.size)

            val derivedMasterKey = ByteArray(32)
            val hkdf = HKDFBytesGenerator(SHA512Digest())
            hkdf.init(HKDFParameters(combinedSecrets, HKDF_SALT, HKDF_INFO))
            hkdf.generateBytes(derivedMasterKey, 0, derivedMasterKey.size)

            // Zero sensitive intermediate secrets
            NativeSecurityBridge.zeroByteArray(classicalSecret)
            NativeSecurityBridge.zeroByteArray(pqSecret)
            NativeSecurityBridge.zeroByteArray(combinedSecrets)

            derivedMasterKey
        } catch (e: Exception) {
            Log.e(TAG, "Hybrid KEM decapsulation failed", e)
            null
        }
    }
}
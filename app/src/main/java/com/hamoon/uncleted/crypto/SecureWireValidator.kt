package com.hamoon.uncleted.crypto

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.nio.ByteBuffer
import kotlin.math.abs

class SecureWireValidator(
    private val trustedPublicKeyBytes: ByteArray,
    private val allowedDriftMs: Long = 120_000L
) {

    data class CommandPacket(
        val opCode: Byte,
        val sequence: Long,
        val timestamp: Long,
        val args: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CommandPacket
            if (opCode != other.opCode) return false
            if (sequence != other.sequence) return false
            if (timestamp != other.timestamp) return false
            if (!args.contentEquals(other.args)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = opCode.toInt()
            result = 31 * result + sequence.hashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + args.contentHashCode()
            return result
        }
    }

    companion object {
        const val WIRE_PACKET_SIZE = 85
        const val SIGNED_HEADER_SIZE = 21
        const val SIGNATURE_SIZE = 64
    }

    /**
     * Verifies the 85-byte Ed25519 signed binary wire payload.
     * Rejects packets that have invalid signatures, expired timestamps, or non-incrementing sequence IDs.
     */
    fun verifyAndParse(rawBinaryPayload: ByteArray, lastRecordedSequence: Long): CommandPacket? {
        if (rawBinaryPayload.size != WIRE_PACKET_SIZE) {
            return null
        }

        if (trustedPublicKeyBytes.size != 32) {
            return null
        }

        val buffer = ByteBuffer.wrap(rawBinaryPayload)
        val timestamp = buffer.long
        val sequence = buffer.long
        val opCode = buffer.get()
        val args = ByteArray(4)
        buffer.get(args)

        val signature = ByteArray(SIGNATURE_SIZE)
        buffer.get(signature)

        // 1. Anti-Replay: Timestamp within allowed drift window
        val now = System.currentTimeMillis()
        if (abs(now - timestamp) > allowedDriftMs) {
            return null
        }

        // 2. Anti-Replay: Monotonic sequence strictly greater than last recorded
        if (sequence <= lastRecordedSequence) {
            return null
        }

        // 3. Signature verification over the 21-byte envelope
        val signedData = ByteBuffer.allocate(SIGNED_HEADER_SIZE)
            .putLong(timestamp)
            .putLong(sequence)
            .put(opCode)
            .put(args)
            .array()

        return try {
            val verifier = Ed25519Signer()
            val pubKeyParams = Ed25519PublicKeyParameters(trustedPublicKeyBytes, 0)
            verifier.init(false, pubKeyParams)
            verifier.update(signedData, 0, signedData.size)

            if (verifier.verifySignature(signature)) {
                CommandPacket(opCode, sequence, timestamp, args)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
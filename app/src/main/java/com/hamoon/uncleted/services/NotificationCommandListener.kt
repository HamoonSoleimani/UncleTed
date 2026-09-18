package com.hamoon.uncleted.services

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import com.hamoon.uncleted.LockScreenActivity
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.crypto.CryptoPreferences
import com.hamoon.uncleted.crypto.OneTimeTokenManager
import com.hamoon.uncleted.crypto.SecureWireValidator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.EventLogger
import com.hamoon.uncleted.util.RootActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationCommandListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val combinedMessage = "$title $text $bigText $subText".trim()
        if (combinedMessage.isEmpty()) return

        // 1. Single-Use Emergency Recovery Token (OTC)
        if (combinedMessage.contains("!UT:OTC-")) {
            val token = extractToken(combinedMessage)
            if (token.isNotEmpty()) {
                cancelNotification(sbn.key)
                val isValid = OneTimeTokenManager.validateAndBurnToken(applicationContext, token)
                if (isValid) {
                    Log.e(TAG, "AUTHENTICATED OTC TOKEN RECEIVED VIA NOTIFICATION: Burning and triggering wipe.")
                    EventLogger.log(applicationContext, "NOTIFICATION DISPATCH: Single-use emergency recovery token executed.")
                    CoroutineScope(Dispatchers.IO).launch {
                        val strategy = DefenseCoordinator.resolveStrategy(applicationContext)
                        strategy.executeWipe("NOTIFICATION_OTC_WIPE")
                        PanicActionService.trigger(applicationContext, "REMOTE_WIPE", PanicActionService.Severity.CRITICAL)
                    }
                } else {
                    Log.w(TAG, "Rejected invalid or burned OTC token received in notification.")
                }
                return
            }
        }

        // 2. Ed25519 Cryptographic Binary Envelope
        if (combinedMessage.contains("!UT:")) {
            val base64Payload = extractEd25519Payload(combinedMessage)
            if (base64Payload.isNotEmpty()) {
                cancelNotification(sbn.key)

                val rawBytes = try {
                    Base64.decode(base64Payload, Base64.NO_WRAP)
                } catch (e: Exception) {
                    Log.e(TAG, "Malformed Base64 payload in notification", e)
                    return
                }

                if (rawBytes.size != SecureWireValidator.WIRE_PACKET_SIZE) return

                val trustedPubKeyBase64 = CryptoPreferences.getTrustedPublicKey(applicationContext) ?: return
                val trustedPubKeyBytes = try {
                    Base64.decode(trustedPubKeyBase64, Base64.NO_WRAP)
                } catch (e: Exception) {
                    return
                }

                val lastSeq = CryptoPreferences.getLastRecordedSequence(applicationContext)
                val validator = SecureWireValidator(trustedPubKeyBytes)
                val verifiedPacket = validator.verifyAndParse(rawBytes, lastSeq)

                if (verifiedPacket != null) {
                    Log.i(TAG, "ED25519 SIGNATURE VERIFIED VIA NOTIFICATION: OpCode=${verifiedPacket.opCode}")
                    EventLogger.log(applicationContext, "NOTIFICATION DISPATCH: Ed25519 packet verified (OpCode: ${verifiedPacket.opCode})")

                    CryptoPreferences.setLastRecordedSequence(applicationContext, verifiedPacket.sequence)

                    CoroutineScope(Dispatchers.IO).launch {
                        dispatchCryptographicOpCode(applicationContext, verifiedPacket)
                    }
                }
                return
            }
        }

        // 3. Permissive Cleartext Fallback (eSIM Data-Only Burner Fallback)
        if (CryptoPreferences.isCleartextSmsAllowed(applicationContext) && combinedMessage.contains("UNCLETED", ignoreCase = true)) {
            val masterPassword = SecurityPreferences.getSmsMasterPassword(applicationContext)
            if (!masterPassword.isNullOrEmpty()) {
                val parts = combinedMessage.split("\\s+".toRegex())
                val uncleTedIndex = parts.indexOfFirst { it.equals("UNCLETED", ignoreCase = true) }
                if (uncleTedIndex != -1 && parts.size >= uncleTedIndex + 3) {
                    val command = parts[uncleTedIndex + 1].uppercase()
                    val password = parts[uncleTedIndex + 2]

                    if (password == masterPassword) {
                        cancelNotification(sbn.key)
                        val args = if (parts.size > uncleTedIndex + 3) parts.subList(uncleTedIndex + 3, parts.size) else emptyList()
                        handleAuthenticatedNotificationCommand(applicationContext, command, args)
                    }
                }
            }
        }
    }

    private fun extractToken(text: String): String {
        val regex = Regex("!UT:OTC-[A-Z0-9-]+")
        return regex.find(text)?.value ?: ""
    }

    private fun extractEd25519Payload(text: String): String {
        val start = text.indexOf("!UT:")
        if (start == -1) return ""
        val sub = text.substring(start + 4)
        return sub.split("\\s+".toRegex())[0]
    }

    private suspend fun dispatchCryptographicOpCode(context: Context, packet: SecureWireValidator.CommandPacket) {
        val strategy = DefenseCoordinator.resolveStrategy(context)
        when (packet.opCode.toInt()) {
            0x01 -> {
                strategy.executeWipe("NOTIFICATION_ED25519_WIPE")
                PanicActionService.trigger(context, "REMOTE_WIPE", PanicActionService.Severity.CRITICAL)
            }
            0x02 -> {
                strategy.setUsbDataPortEnabled(false)
                strategy.evictMemoryKeysAndLock()
            }
            0x03 -> {
                strategy.disableBiometrics(true)
                strategy.evictMemoryKeysAndLock()
            }
            0x04 -> {
                PanicActionService.trigger(context, "REMOTE_EVIDENCE", PanicActionService.Severity.HIGH)
            }
        }
    }

    private fun handleAuthenticatedNotificationCommand(context: Context, command: String, args: List<String>) {
        EventLogger.log(context, "NOTIFICATION DISPATCH: Cleartext command '$command' verified.")
        when (command) {
            "WIPE" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val strategy = DefenseCoordinator.resolveStrategy(context)
                    strategy.executeWipe("NOTIFICATION_CLEARTEXT_WIPE")
                    PanicActionService.trigger(context, "REMOTE_WIPE", PanicActionService.Severity.CRITICAL)
                }
            }
            "EVIDENCE" -> {
                PanicActionService.trigger(context, "REMOTE_EVIDENCE", PanicActionService.Severity.HIGH)
            }
            "SIREN" -> {
                PanicActionService.trigger(context, "REMOTE_SIREN", PanicActionService.Severity.HIGH)
            }
            "LOCK" -> {
                val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                context.startActivity(lockIntent)
            }
            "LOCATE" -> {
                PanicActionService.trigger(context, "MANUAL_LOCATION", PanicActionService.Severity.LOW)
            }
            "AUDIO" -> {
                val duration = args.firstOrNull()?.toIntOrNull() ?: 60
                PanicActionService.pendingAudioDuration = duration
                PanicActionService.trigger(context, "REMOTE_AUDIO_RECORD", PanicActionService.Severity.HIGH)
            }
            "REBOOT" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    RootActions.rebootDevice(context)
                }
            }
        }
    }
}
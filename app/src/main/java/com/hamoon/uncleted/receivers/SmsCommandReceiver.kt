package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Base64
import android.util.Log
import com.hamoon.uncleted.LockScreenActivity
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.crypto.CryptoPreferences
import com.hamoon.uncleted.crypto.OneTimeTokenManager
import com.hamoon.uncleted.crypto.SecureWireValidator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsCommandReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsCommandReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val fullBodyBuilder = StringBuilder()
        var senderNum: String? = null

        for (sms in messages) {
            fullBodyBuilder.append(sms.messageBody ?: "")
            if (senderNum == null) {
                senderNum = sms.originatingAddress
            }
        }

        val body = fullBodyBuilder.toString().trim()
        if (body.isEmpty()) return

        // =========================================================================
        // ROUTE 1: PRINTABLE ONE-TIME EMERGENCY TOKEN (OTC)
        // =========================================================================
        if (body.startsWith("!UT:OTC-")) {
            try { abortBroadcast() } catch (_: Exception) {}
            purgeSmsFromDatabase(context, body)

            val isValidToken = OneTimeTokenManager.validateAndBurnToken(context, body)
            if (isValidToken) {
                Log.e(TAG, "AUTHENTICATED ONE-TIME RECOVERY TOKEN VERIFIED: Burning token and triggering wipe.")
                EventLogger.log(context, "AUTHENTICATED: Single-use emergency recovery token executed.")

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val strategy = DefenseCoordinator.resolveStrategy(context)
                        strategy.executeWipe("ONE_TIME_EMERGENCY_TOKEN")
                        PanicActionService.trigger(context, "REMOTE_WIPE", PanicActionService.Severity.CRITICAL)
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else {
                Log.w(TAG, "REJECTED: Received invalid or previously burned One-Time Emergency Token.")
                EventLogger.log(context, "SECURITY: Rejected invalid/replayed One-Time Emergency Token.")
            }
            return
        }

        // =========================================================================
        // ROUTE 2: ED25519 CRYPTOGRAPHIC BINARY WIRE ENVELOPE
        // =========================================================================
        if (body.startsWith("!UT:")) {
            try { abortBroadcast() } catch (_: Exception) {}
            purgeSmsFromDatabase(context, body)

            val base64Payload = body.removePrefix("!UT:")
            val rawBytes = try {
                Base64.decode(base64Payload, Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.e(TAG, "Malformed Base64 payload in !UT envelope", e)
                return
            }

            if (rawBytes.size != SecureWireValidator.WIRE_PACKET_SIZE) {
                Log.w(TAG, "Rejected payload: Invalid wire packet size (${rawBytes.size} bytes).")
                return
            }

            val trustedPubKeyBase64 = CryptoPreferences.getTrustedPublicKey(context)
            if (trustedPubKeyBase64.isNullOrEmpty()) {
                Log.e(TAG, "Cryptographic command rejected: No trusted Ed25519 public key configured on device.")
                return
            }

            val trustedPubKeyBytes = try {
                Base64.decode(trustedPubKeyBase64, Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.e(TAG, "Corrupted local Ed25519 public key in storage", e)
                return
            }

            val lastSeq = CryptoPreferences.getLastRecordedSequence(context)
            val validator = SecureWireValidator(trustedPubKeyBytes)
            val verifiedPacket = validator.verifyAndParse(rawBytes, lastSeq)

            if (verifiedPacket != null) {
                Log.i(TAG, "ED25519 SIGNATURE VERIFIED: OpCode=${verifiedPacket.opCode}, Seq=${verifiedPacket.sequence}")
                EventLogger.log(context, "AUTHENTICATED: Ed25519 command received (OpCode: ${verifiedPacket.opCode}, Seq: ${verifiedPacket.sequence})")

                CryptoPreferences.setLastRecordedSequence(context, verifiedPacket.sequence)

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        dispatchCryptographicOpCode(context, verifiedPacket)
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else {
                Log.w(TAG, "Cryptographic validation failed: Signature rejected, replay detected, or timestamp expired.")
                EventLogger.log(context, "SECURITY: Dropped unauthorized or replayed Ed25519 packet.")
            }
            return
        }

        // =========================================================================
        // ROUTE 3: PERMISSIVE CLEARTEXT SMS FALLBACK
        // =========================================================================
        if (!CryptoPreferences.isCleartextSmsAllowed(context)) {
            Log.d(TAG, "Cleartext SMS processing is disabled in security settings.")
            return
        }

        val masterPassword = SecurityPreferences.getSmsMasterPassword(context)
        val installCode = SecurityPreferences.getRemoteInstallCode(context)

        if (masterPassword.isNullOrEmpty()) {
            return
        }

        val parts = body.split(" ")

        if (parts.isNotEmpty() && parts[0].equals("UNCLETED", ignoreCase = true) && parts.size >= 3) {
            val command = parts[1].uppercase()
            val password = parts[2]

            if (password == masterPassword) {
                try { abortBroadcast() } catch (_: Exception) {}
                purgeSmsFromDatabase(context, body)

                val args = if (parts.size > 3) parts.subList(3, parts.size) else emptyList()
                handleAuthenticatedCommand(context, command, senderNum, args)
            } else {
                Log.w(TAG, "Invalid SMS master password received from $senderNum.")
                EventLogger.log(context, "SMS command attempted from $senderNum with incorrect password.")
            }
            return
        }

        // Silent Install Trigger
        if (SecurityPreferences.isSilentInstallEnabled(context) && !installCode.isNullOrEmpty() && body.contains(installCode)) {
            try { abortBroadcast() } catch (_: Exception) {}
            purgeSmsFromDatabase(context, body)

            Log.i(TAG, "Silent install trigger received from $senderNum.")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    RootActions.performSilentInstall(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun dispatchCryptographicOpCode(context: Context, packet: SecureWireValidator.CommandPacket) {
        val strategy = DefenseCoordinator.resolveStrategy(context)

        when (packet.opCode.toInt()) {
            0x01 -> {
                Log.e(TAG, "Executing OP_EMERGENCY_WIPE")
                strategy.executeWipe("OP_ED25519_WIPE")
                PanicActionService.trigger(context, "REMOTE_WIPE", PanicActionService.Severity.CRITICAL)
            }
            0x02 -> {
                Log.w(TAG, "Executing OP_SEVER_USB_AND_LOCK")
                strategy.setUsbDataPortEnabled(false)
                strategy.evictMemoryKeysAndLock()
            }
            0x03 -> {
                Log.w(TAG, "Executing OP_EVICT_KEYS_TO_BFU")
                strategy.disableBiometrics(true)
                strategy.evictMemoryKeysAndLock()
            }
            0x04 -> {
                Log.i(TAG, "Executing OP_CAPTURE_EVIDENCE")
                PanicActionService.trigger(context, "REMOTE_EVIDENCE", PanicActionService.Severity.HIGH)
            }
            else -> {
                Log.w(TAG, "Unknown OpCode: ${packet.opCode}")
            }
        }
    }

    private fun purgeSmsFromDatabase(context: Context, bodySnippet: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uri = Uri.parse("content://sms")
                val escapedSnippet = bodySnippet.replace("'", "''")
                context.contentResolver.delete(uri, "body LIKE ?", arrayOf("%$escapedSnippet%"))
            } catch (_: Exception) {}

            if (RootChecker.isDeviceRooted()) {
                val safeCommand = "content delete --uri content://sms --where \"body LIKE '%!UT%' OR body LIKE '%UNCLETED%'\""
                RootExecutor.run(safeCommand, logErrors = false)
            }
        }
    }

    private fun handleAuthenticatedCommand(context: Context, command: String, sender: String?, args: List<String>) {
        Log.i(TAG, "Authenticated SMS command '$command' received from $sender.")
        EventLogger.log(context, "Authenticated cleartext SMS command '$command' received.")

        when (command) {
            "WIPE" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val strategy = DefenseCoordinator.resolveStrategy(context)
                    strategy.executeWipe("REMOTE_CLEARTEXT_WIPE")
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
            "SPEAK" -> {
                val message = args.joinToString(" ")
                if (message.isNotEmpty()) {
                    PanicActionService.pendingTtsMessage = message
                    PanicActionService.trigger(context, "REMOTE_SPEAK", PanicActionService.Severity.MEDIUM)
                }
            }
            "REBOOT" -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        RootActions.rebootDevice(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            "SCREENSHOT" -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val screenshot = RootActions.takeStealthScreenshot(context)
                        screenshot?.let {
                            AdvancedEmailSender.sendAdvancedAlert(
                                context,
                                AdvancedEmailSender.EmailTemplate("Remote Screenshot", "Screenshot captured remotely via SMS command."),
                                screenshotFile = it
                            )
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            "GETLOGS" -> {
                val emergencyContact = SecurityPreferences.getEmergencyContact(context)
                if (emergencyContact.isNullOrEmpty() || !emergencyContact.contains("@")) return

                val logs = SecurityPreferences.getKeylogData(context)
                if (!logs.isNullOrEmpty()) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            EmailSender.sendEmail(
                                context,
                                emergencyContact,
                                "Remote Keylog Data",
                                "Keylogger buffer retrieved via SMS command:\n\n$logs"
                            )
                            SecurityPreferences.clearKeylogData(context)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            "EXFIL" -> {
                if (args.size == 2) {
                    val packageName = args[0]
                    val path = args[1]
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val exfilFile = RootActions.exfiltrateAppData(context, packageName, path)
                            val emergencyContact = SecurityPreferences.getEmergencyContact(context)
                            if (exfilFile != null && emergencyContact != null && emergencyContact.contains("@")) {
                                EmailSender.sendEmail(
                                    context,
                                    emergencyContact,
                                    "Data Exfiltration",
                                    "Exfiltrated file '$path' from package '$packageName':",
                                    attachmentFile = exfilFile
                                )
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            else -> {
                Log.w(TAG, "Unknown authenticated SMS command '$command' from $sender.")
            }
        }
    }
}
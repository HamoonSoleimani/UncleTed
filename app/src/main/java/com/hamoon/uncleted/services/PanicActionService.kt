package com.hamoon.uncleted.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.hamoon.uncleted.CameraPermissionBrokerActivity
import com.hamoon.uncleted.LockScreenActivity
import com.hamoon.uncleted.R
import com.hamoon.uncleted.canary.CovertCanarySender
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.util.*
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class PanicActionService : LifecycleService(), TextToSpeech.OnInitListener {

    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var sirenMediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null

    private lateinit var cameraLifecycleOwner: FakeLifecycleOwner

    private var tts: TextToSpeech? = null
    private var ttsMessageQueue: String? = null
    private var pendingWipeType: String? = null

    companion object {
        private const val TAG = "UncleTed-LockHook"
        private const val SERVICE_TAG = "PanicActionService"
        private val lastTriggerTimestamps = ConcurrentHashMap<String, Long>()
        private const val TRIGGER_COOLDOWN_MS = 1500L
        private const val NOTIFICATION_ID = 1001
        private const val BROKER_NOTIFICATION_ID = 9002
        private const val BROKER_CHANNEL_ID = "UncleTedEmergencyBrokerChannel"

        var pendingTtsMessage: String? = null
        var pendingAudioDuration: Int = 60

        fun trigger(context: Context, reason: String, severity: Severity) {
            val now = System.currentTimeMillis()
            val lastTrigger = lastTriggerTimestamps[reason] ?: 0L

            if (now - lastTrigger < TRIGGER_COOLDOWN_MS) {
                Log.w(SERVICE_TAG, "Panic trigger for '$reason' throttled.")
                return
            }
            lastTriggerTimestamps[reason] = now
            EventLogger.log(context, "Action Triggered: $reason (Severity: ${severity.name})")

            val isImmediateWipe = reason == "REMOTE_WIPE" ||
                    reason == "WIPE_PIN" ||
                    reason == "WIPE_PIN_DETECTED" ||
                    reason == "TRIPWIRE_WIPE" ||
                    reason == "MANUAL_WIPE" ||
                    reason == "HARDWARE_BUTTON_WIPE" ||
                    reason == "GEOFENCE_SUICIDE_EVIN" ||
                    reason == "USB_TRIPWIRE_FAIL" ||
                    reason == "BATTERY_SPLICING_DC_JIG_DETECTED" ||
                    reason == "THERMAL_ENCLOSURE_DISASSEMBLY"

            val isSirenOnly = reason == "REMOTE_SIREN" || reason == "MANUAL_SIREN"
            val requiresMedia = (severity == Severity.MEDIUM || severity == Severity.HIGH || severity == Severity.CRITICAL)
                    && !isImmediateWipe && !isSirenOnly

            CoroutineScope(Dispatchers.IO).launch {
                val isRooted = RootChecker.isDeviceRooted()

                if (isRooted) {
                    if (isImmediateWipe) {
                        Log.i(SERVICE_TAG, "ROOT: Executing wipe protocol.")
                        val wipeLevel = when {
                            reason == "GEOFENCE_SUICIDE_EVIN" -> RootActions.WipeLevel.NUCLEAR_WINTER
                            SecurityPreferences.isSecureWipeEnabled(context) -> RootActions.WipeLevel.FAST_USERDATA
                            else -> RootActions.WipeLevel.STANDARD_WIPE
                        }
                        RootActions.executeWipeProtocol(context, wipeLevel)
                        return@launch
                    }

                    if (requiresMedia && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Log.d(SERVICE_TAG, "ROOT: Bypassing Background Activity Restriction via Shell.")
                        val brokerIntent = Intent(context, CameraPermissionBrokerActivity::class.java).apply {
                            putExtra("REASON", reason)
                            putExtra("SEVERITY", severity.name)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        GodMode.startActivityInBackground(context, brokerIntent)
                    } else {
                        startServiceInternal(context, reason, severity)
                    }
                } else {
                    if (isImmediateWipe) {
                        Log.i(SERVICE_TAG, "NON-ROOT: Initiating Immediate Wipe.")
                        DeviceAdminHelper.wipeDeviceImmediately(context)
                        return@launch
                    }

                    if (requiresMedia && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Log.d(SERVICE_TAG, "NON-ROOT: Bypassing BAL restrictions via Full-Screen Intent.")
                        launchBrokerViaFullScreenIntent(context, reason, severity)
                    } else {
                        startServiceInternal(context, reason, severity)
                    }
                }
            }
        }

        private fun launchBrokerViaFullScreenIntent(context: Context, reason: String, severity: Severity) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    BROKER_CHANNEL_ID,
                    "Uncle Ted Emergency Dispatch",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Used to dispatch emergency broker activities under OS restrictions"
                    setBypassDnd(true)
                    enableVibration(true)
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val brokerIntent = Intent(context, CameraPermissionBrokerActivity::class.java).apply {
                putExtra("REASON", reason)
                putExtra("SEVERITY", severity.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                BROKER_NOTIFICATION_ID,
                brokerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, BROKER_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Emergency Security Dispatch")
                .setContentText("Authenticating security session...")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)

            notificationManager.notify(BROKER_NOTIFICATION_ID, builder.build())
        }

        private fun startServiceInternal(context: Context, reason: String, severity: Severity) {
            val intent = Intent(context, PanicActionService::class.java).apply {
                putExtra("REASON", reason)
                putExtra("SEVERITY", severity.name)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(SERVICE_TAG, "Failed startServiceInternal", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        cameraLifecycleOwner = FakeLifecycleOwner()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "uncleted::PanicWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(SERVICE_TAG, "TTS Language not supported")
            } else {
                ttsMessageQueue?.let {
                    speakMessage(it)
                    ttsMessageQueue = null
                }
            }
        } else {
            Log.e(SERVICE_TAG, "TTS Initialization failed")
        }
    }

    private fun speakMessage(message: String) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)

            val params = android.os.Bundle()
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)

            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "UncleTedTTS")
            Log.i(SERVICE_TAG, "Speaking: $message")
        } catch (e: Exception) {
            Log.e(SERVICE_TAG, "Error during TTS speak", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val reason = intent?.getStringExtra("REASON") ?: "UNKNOWN"
        val severityStr = intent?.getStringExtra("SEVERITY") ?: "MEDIUM"
        val severity = try { Severity.valueOf(severityStr) } catch (e: Exception) { Severity.MEDIUM }

        pendingWipeType = intent?.getStringExtra("WIPE_TYPE")

        if (reason == "REMOTE_SPEAK") {
            val message = pendingTtsMessage ?: "System Alert"
            pendingTtsMessage = null
            if (tts != null) speakMessage(message) else ttsMessageQueue = message
        }

        val isImmediateWipe = reason == "REMOTE_WIPE" ||
                reason == "WIPE_PIN" ||
                reason == "WIPE_PIN_DETECTED" ||
                reason == "TRIPWIRE_WIPE" ||
                reason == "MANUAL_WIPE" ||
                reason == "HARDWARE_BUTTON_WIPE" ||
                reason == "GEOFENCE_SUICIDE_EVIN" ||
                reason == "USB_TRIPWIRE_FAIL" ||
                reason == "BATTERY_SPLICING_DC_JIG_DETECTED" ||
                reason == "THERMAL_ENCLOSURE_DISASSEMBLY"

        val isSirenOnly = reason == "REMOTE_SIREN" || reason == "MANUAL_SIREN"
        val isBrokered = intent?.getBooleanExtra("IS_BROKERED", false) ?: false

        try {
            val notification = NotificationHelper.createPanicNotification(this)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                var fgsTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

                val needsLocation = severity != Severity.LOW
                val needsCamera = !isImmediateWipe && !isSirenOnly && (severity != Severity.LOW)
                val needsMic = !isImmediateWipe && !isSirenOnly && (severity == Severity.HIGH || severity == Severity.CRITICAL)
                val needsMediaPlayback = isSirenOnly || reason == "REMOTE_SPEAK"

                if (needsLocation && PermissionUtils.hasLocationPermissions(this)) {
                    fgsTypes = fgsTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                }
                if (needsCamera && PermissionUtils.hasCameraPermission(this)) {
                    fgsTypes = fgsTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }
                if (needsMic && PermissionUtils.hasRecordAudioPermission(this)) {
                    fgsTypes = fgsTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                if (needsMediaPlayback) {
                    fgsTypes = fgsTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                }

                startForeground(NOTIFICATION_ID, notification, fgsTypes)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (isImmediateWipe || isSirenOnly) {
                    startForeground(NOTIFICATION_ID, notification)
                } else {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(SERVICE_TAG, "Failed to start foreground service: ${e.message}", e)
            if (isImmediateWipe) {
                try { DeviceAdminHelper.wipeDeviceImmediately(this) } catch (_: Exception) {}
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val needsMedia = (severity == Severity.MEDIUM || severity == Severity.HIGH || severity == Severity.CRITICAL) && !isImmediateWipe && !isSirenOnly

        if (needsMedia && !isBrokered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !PermissionUtils.hasCameraPermission(this)) {
            Log.w(SERVICE_TAG, "Service missing camera permission. Invoking broker.")
            launchBrokerViaFullScreenIntent(this, reason, severity)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        Log.w(TAG, "Service executing protocol for: $reason (Severity: $severity)")

        serviceScope.coroutineContext.cancelChildren()

        lifecycleScope.launch(serviceScope.coroutineContext) {
            try {
                if (!isImmediateWipe && !isSirenOnly) {
                    withContext(Dispatchers.Main) {
                        cameraLifecycleOwner.start()
                    }
                }

                // Execute Covert OHTTP Canary Dispatch (RFC 9458)
                val currentLoc = getCurrentLocation()
                CovertCanarySender.dispatchCovertDuress(this@PanicActionService, reason, currentLoc)

                when (severity) {
                    Severity.LOW -> handleLowSeverityIncident(reason)
                    Severity.MEDIUM -> handleMediumSeverityIncident(reason)
                    Severity.HIGH -> handleHighSeverityIncident(reason)
                    Severity.CRITICAL -> handleCriticalIncident(reason)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(SERVICE_TAG, "Error executing protocol: ${e.message}", e)
                }
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun handleLowSeverityIncident(reason: String) {
        if (reason == "MANUAL_LOCATION") {
            val location = getCurrentLocation()
            if (location != null) {
                val locInfo = LocationTracker.LocationInfo(location, null, location.accuracy, location.time)
                sendAlert(AdvancedEmailSender.EmailTemplate("Location Requested", LocationTracker.formatLocationForEmail(locInfo)))
            } else {
                sendAlert(AdvancedEmailSender.EmailTemplate("Location Failed", "Could not retrieve GPS."))
            }
            return
        }
        sendAlert(AdvancedEmailSender.EmailTemplate("Alert: $reason", "Low priority event detected: $reason"))
    }

    private suspend fun handleMediumSeverityIncident(reason: String) {
        if (reason == "REMOTE_SPEAK") return

        Log.i(TAG, "Capturing photo evidence for: $reason")
        val capture = if (PermissionUtils.hasCameraPermission(this)) {
            AdvancedCameraHandler.performFullCapture(this, cameraLifecycleOwner, videoDurationSeconds = 0)
        } else {
            Log.e(TAG, "Camera permission not granted! Cannot take photo.")
            null
        }

        if (reason == "INTRUDER_SELFIE") {
            if (capture?.frontPhoto != null) {
                val photoFile = capture.frontPhoto
                Log.i(TAG, "Intruder selfie captured successfully: ${photoFile.absolutePath}")

                val uri = FileUtils.saveImageToPictures(this, photoFile)
                if (uri != null) {
                    Log.i(TAG, "Intruder selfie successfully saved to Gallery: $uri")
                    NotificationHelper.showSelfieSavedNotification(this, uri)
                }
            } else {
                Log.w(TAG, "Front camera photo capture returned null.")
            }
        }

        val template = when (reason) {
            "INTRUDER_SELFIE" -> AdvancedEmailSender.getEmailTemplates()["INTRUDER"]!!
            "SIM_CHANGED", "SIM_REMOVED" -> AdvancedEmailSender.getEmailTemplates()["SIM_CHANGE"]!!
            "MANUAL_HONEYPOT", "HONEYPOT_ACTIVATED", "HONEYPOT_PIN_LOCKSCREEN" ->
                AdvancedEmailSender.EmailTemplate("Honeypot Deployed", "Honeypot mode activated. Covert monitoring engaged.")
            else -> AdvancedEmailSender.getEmailTemplates()["GENERIC_MEDIUM"]!!
        }

        sendAlert(template, capture)
    }

    private suspend fun handleHighSeverityIncident(reason: String) {
        if (reason == "REMOTE_SIREN" || reason == "MANUAL_SIREN") {
            Log.i(SERVICE_TAG, "Executing Siren Only Protocol.")
            sendAlert(AdvancedEmailSender.EmailTemplate("Siren Activated", "Loud siren triggered on device."))
            startSiren(30)
            return
        }

        if (reason == "REMOTE_AUDIO_RECORD") {
            val audioFile = if (PermissionUtils.hasRecordAudioPermission(this)) {
                AudioRecorder.recordAudio(this, pendingAudioDuration)
            } else null

            if (audioFile != null) {
                sendAlert(AdvancedEmailSender.EmailTemplate("Remote Audio", "Audio surveillance attached."), null, audioFile)
            }
            pendingAudioDuration = 60
            return
        }

        if (reason == "REMOTE_EVIDENCE") {
            val capture = if (PermissionUtils.hasCameraPermission(this))
                AdvancedCameraHandler.performFullCapture(this, cameraLifecycleOwner, 15) else null
            val audio = if (PermissionUtils.hasRecordAudioPermission(this))
                AudioRecorder.recordAudio(this, 30) else null

            val template = AdvancedEmailSender.EmailTemplate("Evidence Collection", "Full evidence suite attached.")
            sendAlert(template, capture, audio)
            return
        }

        val sirenJob = if (reason == "SHAKE_TRIGGERED") {
            serviceScope.async { startSiren(30) }
        } else null

        val capture = if (PermissionUtils.hasCameraPermission(this))
            AdvancedCameraHandler.performFullCapture(this, cameraLifecycleOwner, 15) else null

        val audioFile = if (SecurityPreferences.isAmbientAudioEnabled(this) && PermissionUtils.hasRecordAudioPermission(this))
            AudioRecorder.recordAudio(this, 30) else null

        if ((reason == "DURESS_PIN" || reason == "DURESS_PIN_LOCKSCREEN") && RootChecker.isDeviceRooted()) {
            RootActions.setMockLocationConfig(this, true)
        }

        val template = when (reason) {
            "DURESS_PIN", "DURESS_PIN_LOCKSCREEN", "SHAKE_TRIGGERED" -> AdvancedEmailSender.getEmailTemplates()["DURESS"]!!
            "UNINSTALL_ATTEMPT" -> AdvancedEmailSender.getEmailTemplates()["SYSTEM_BREACH"]!!
            "HONEYPOT_PIN_LOCKSCREEN" -> AdvancedEmailSender.EmailTemplate("Honeypot Triggered", "Honeypot PIN entered on Keyguard. Decoy deployed.")
            else -> AdvancedEmailSender.getEmailTemplates()["GENERIC_HIGH"]!!
        }

        sendAlert(template, capture, audioFile)
        sirenJob?.await()
    }

    private suspend fun handleCriticalIncident(reason: String) {
        val isWipeRequest = (reason == "REMOTE_WIPE" ||
                reason == "WIPE_PIN" ||
                reason == "WIPE_PIN_DETECTED" ||
                reason == "MANUAL_WIPE" ||
                reason == "TRIPWIRE_WIPE" ||
                reason == "HARDWARE_BUTTON_WIPE" ||
                reason == "GEOFENCE_SUICIDE_EVIN" ||
                reason == "BATTERY_SPLICING_DC_JIG_DETECTED" ||
                reason == "THERMAL_ENCLOSURE_DISASSEMBLY")

        if (isWipeRequest) {
            Log.e(SERVICE_TAG, "!!! CRITICAL: IMMEDIATE WIPE REQUESTED via $reason !!!")

            try {
                withTimeout(1500) {
                    sendAlert(AdvancedEmailSender.EmailTemplate("DEVICE WIPING NOW", "Reason: $reason. Goodbye.", true))
                }
            } catch (e: Exception) {
                Log.w(SERVICE_TAG, "Could not send goodbye packet: timeout")
            }

            val isManualOverride = reason == "MANUAL_WIPE" || reason == "REMOTE_WIPE" || reason == "HARDWARE_BUTTON_WIPE" || reason == "GEOFENCE_SUICIDE_EVIN"

            if (isManualOverride || SecurityPreferences.isWipeDeviceEnabled(this)) {
                if (pendingWipeType == "STANDARD_WIPE") {
                    Log.i(SERVICE_TAG, "Manual Action: Executing Level 1 (Safe Standard Wipe).")
                    DeviceAdminHelper.wipeDeviceImmediately(this)
                    return
                }

                if (RootChecker.isDeviceRooted()) {
                    Log.e(SERVICE_TAG, "Executing ROOT Wipe Strategy.")

                    if (pendingWipeType != null) {
                        try {
                            val level = RootActions.WipeLevel.valueOf(pendingWipeType!!)
                            Log.e(SERVICE_TAG, "Manual Wipe Selection Detected: $level")
                            RootActions.executeWipeProtocol(this, level)
                            return
                        } catch (e: Exception) {
                            Log.e(SERVICE_TAG, "Invalid wipe type passed: $pendingWipeType. Falling back to settings.", e)
                        }
                    }

                    if (reason == "GEOFENCE_SUICIDE_EVIN") {
                        RootActions.executeWipeProtocol(this, RootActions.WipeLevel.NUCLEAR_WINTER)
                    } else if (SecurityPreferences.isSecureWipeEnabled(this)) {
                        RootActions.executeWipeProtocol(this, RootActions.WipeLevel.FAST_USERDATA)
                    } else {
                        RootActions.executeWipeProtocol(this, RootActions.WipeLevel.STANDARD_WIPE)
                    }
                } else {
                    Log.e(SERVICE_TAG, "Executing DEVICE ADMIN Wipe Strategy.")
                    DeviceAdminHelper.wipeDeviceImmediately(this)
                }
            } else {
                Log.w(SERVICE_TAG, "Wipe requested but 'Wipe Device Enabled' is OFF. Reason: $reason")
            }
            return
        }

        sendAlert(AdvancedEmailSender.getEmailTemplates()["URGENT_PREAMBLE"]!!.copy(body = "CRITICAL: $reason"))
        val capture = if (PermissionUtils.hasCameraPermission(this)) AdvancedCameraHandler.performFullCapture(this, cameraLifecycleOwner, 10) else null
        val audio = if (PermissionUtils.hasRecordAudioPermission(this)) AudioRecorder.recordAudio(this, 10) else null

        sendAlert(AdvancedEmailSender.getEmailTemplates()["SYSTEM_BREACH"]!!.copy(body = "CRITICAL DETAILS: $reason"), capture, audio, true)
    }

    private suspend fun sendAlert(
        template: AdvancedEmailSender.EmailTemplate,
        capture: AdvancedCameraHandler.CameraCapture? = null,
        audioFile: File? = null,
        includeDeviceInfo: Boolean = false,
        screenshotFile: File? = null
    ) {
        val contact = SecurityPreferences.getEmergencyContact(this)
        if (contact.isNullOrEmpty()) {
            Log.d(TAG, "No emergency contact configured. Skipping remote alert dispatch.")
            return
        }

        val deviceInfo = if (includeDeviceInfo) DeviceInfoCollector.collectDeviceInfo(this) else null

        var sc = screenshotFile
        if (sc == null && SecurityPreferences.isStealthScreenshotEnabled(this) && RootChecker.isDeviceRooted()) {
            sc = RootActions.takeStealthScreenshot(this)
        }

        if (contact.contains("@")) {
            AdvancedEmailSender.sendAdvancedAlert(this, template, capture, audioFile, deviceInfo, sc)
        } else {
            val loc = capture?.location ?: getCurrentLocation()
            val locStr = if (loc != null) "https://maps.google.com?q=${loc.latitude},${loc.longitude}" else "No GPS"
            sendSms(contact, "Uncle Ted Alert: ${template.subject}. Loc: $locStr")
        }
    }

    private suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { cont ->
        if (!PermissionUtils.hasLocationPermissions(this)) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }
        try {
            val client = LocationServices.getFusedLocationProviderClient(this)
            val source = CancellationTokenSource()
            cont.invokeOnCancellation { source.cancel() }

            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, source.token)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun sendSms(phoneNumber: String, message: String) {
        if (!PermissionUtils.hasSmsPermissions(this)) return
        try {
            getSystemService(SmsManager::class.java).sendTextMessage(phoneNumber, null, message, null, null)
        } catch (e: Exception) {
            Log.e(SERVICE_TAG, "SMS Failed", e)
        }
    }

    private suspend fun startSiren(durationSeconds: Int) {
        Log.i(SERVICE_TAG, "Siren STARTING for ${durationSeconds}s")
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var originalAlarmVolume: Int? = null

        try {
            originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)

            var soundUri = Settings.System.DEFAULT_ALARM_ALERT_URI
            if (soundUri == null) soundUri = Settings.System.DEFAULT_NOTIFICATION_URI
            if (soundUri == null) soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            if (soundUri != null) {
                sirenMediaPlayer = MediaPlayer().apply {
                    setDataSource(this@PanicActionService, soundUri)
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                    isLooping = true
                    prepare()
                    start()
                }
                Log.i(SERVICE_TAG, "Siren playing...")
            }

            vibrator?.let {
                val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 1000)
                val effect = VibrationEffect.createWaveform(pattern, 0)
                it.vibrate(effect)
            }

            delay(durationSeconds * 1000L)
        } catch (e: Exception) {
            Log.e(SERVICE_TAG, "Failed to play siren", e)
        } finally {
            stopSiren()
            originalAlarmVolume?.let { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) }
            Log.i(SERVICE_TAG, "Siren finished.")
        }
    }

    private fun stopSiren() {
        try {
            sirenMediaPlayer?.apply { if (isPlaying) stop(); release() }
        } catch (e: Exception) {
            Log.w(SERVICE_TAG, "Error releasing media player", e)
        }
        sirenMediaPlayer = null
        vibrator?.cancel()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        stopSiren()
        cameraLifecycleOwner.destroy()
        serviceJob.cancel()
        super.onDestroy()
        Log.d(TAG, "PanicActionService destroyed.")
    }
}
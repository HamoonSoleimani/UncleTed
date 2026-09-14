package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import javax.activation.CommandMap
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.activation.MailcapCommandMap
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

object AdvancedEmailSender {

    private const val TAG = "AdvancedEmailSender"

    data class EmailTemplate(
        val subject: String,
        val body: String,
        val isUrgent: Boolean = false
    )

    suspend fun sendAdvancedAlert(
        context: Context,
        template: EmailTemplate,
        capture: AdvancedCameraHandler.CameraCapture? = null,
        audioFile: File? = null,
        deviceInfo: DeviceInfoCollector.DeviceInfo? = null,
        screenshotFile: File? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val config = EmailSender.getEmailConfig(context)
        val recipient = SecurityPreferences.getEmergencyContact(context)

        if (config == null || recipient.isNullOrEmpty()) {
            Log.e(TAG, "Email configuration or recipient missing. Cannot send advanced alert.")
            return@withContext false
        }
        if (!recipient.contains("@")) {
            Log.e(TAG, "Emergency contact is not a valid email address.")
            return@withContext false
        }
        if (!PermissionUtils.hasInternetPermission(context)) {
            Log.e(TAG, "INTERNET permission not granted. Cannot send email.")
            return@withContext false
        }

        val originalClassLoader = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = context.classLoader

            val mc = CommandMap.getDefaultCommandMap() as MailcapCommandMap
            mc.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html")
            mc.addMailcap("text/xml;; x-java-content-handler=com.sun.mail.handlers.text_xml")
            mc.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain")
            mc.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed")
            mc.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822")
            CommandMap.setDefaultCommandMap(mc)

            val props = createEmailProperties(config)
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(config.username, config.password)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(config.username))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient))
                subject = if (template.isUrgent) "[URGENT] ${template.subject}" else template.subject
                if (template.isUrgent) {
                    setHeader("X-Priority", "1")
                    setHeader("X-MSMail-Priority", "High")
                }
            }

            val multipart = MimeMultipart()

            val bodyPart = MimeBodyPart().apply {
                setText(createDetailedBody(context, template.body, capture, deviceInfo))
            }
            multipart.addBodyPart(bodyPart)

            capture?.frontPhoto?.let { addAttachment(multipart, it, "front_camera.jpg") }
            capture?.backPhoto?.let { addAttachment(multipart, it, "back_camera.jpg") }
            capture?.frontVideo?.let { addAttachment(multipart, it, "front_video.mp4") }
            capture?.backVideo?.let { addAttachment(multipart, it, "back_video.mp4") }
            audioFile?.let {
                val fileName = if (it.name.endsWith(".m4a")) "ambient_audio.m4a" else "ambient_audio.mp4"
                addAttachment(multipart, it, fileName)
            }
            screenshotFile?.let { addAttachment(multipart, it, "stealth_screenshot.png") }

            message.setContent(multipart)
            Transport.send(message)

            Log.i(TAG, "Advanced email sent successfully to $recipient")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send advanced email", e)
            EventLogger.log(context, "ERROR: Failed to send email alert. Check credentials and connection.")
            false
        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }
    }

    private fun createEmailProperties(config: EmailSender.EmailConfig): Properties {
        return Properties().apply {
            put("mail.transport.protocol", "smtp")
            put("mail.smtp.class", "com.sun.mail.smtp.SMTPTransport")
            put("mail.smtp.host", config.host)
            put("mail.smtp.port", config.port.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "15000")
            put("mail.smtp.writetimeout", "15000")
            if (config.enableSslTls) {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.ssl.protocols", "TLSv1.2")
                put("mail.smtp.ssl.trust", config.host)
                put("mail.smtp.socketFactory.port", config.port.toString())
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                put("mail.smtp.socketFactory.fallback", "false")
            }
        }
    }

    private fun createDetailedBody(
        context: Context,
        originalBody: String,
        capture: AdvancedCameraHandler.CameraCapture?,
        deviceInfo: DeviceInfoCollector.DeviceInfo?
    ): String {
        val builder = StringBuilder(originalBody)

        builder.append("\n\n--- EVIDENCE DETAILS ---\n")
        builder.append("Timestamp: ${Date(capture?.timestamp ?: System.currentTimeMillis())}\n")

        capture?.location?.let { location ->
            builder.append("GPS Coordinates: ${location.latitude}, ${location.longitude}\n")
            builder.append("Accuracy: ${location.accuracy}m\n")
            builder.append("Google Maps: https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}\n")
        }

        deviceInfo?.let {
            builder.append("\n--- DEVICE DIAGNOSTICS ---\n")
            builder.append(DeviceInfoCollector.formatDeviceInfoForEmail(it))
        }

        builder.append("\nThis message was sent automatically by Uncle Ted security system.")
        EventLogger.log(context, "Email alert sent: ${originalBody.take(50)}...")
        return builder.toString()
    }

    private fun addAttachment(multipart: MimeMultipart, file: File, filename: String) {
        if (file.exists()) {
            val attachmentPart = MimeBodyPart().apply {
                val dataSource = FileDataSource(file)
                dataHandler = DataHandler(dataSource)
                this.fileName = filename
            }
            multipart.addBodyPart(attachmentPart)
        }
    }

    fun getEmailTemplates(): Map<String, EmailTemplate> {
        return mapOf(
            "INTRUDER" to EmailTemplate(
                subject = "Security Alert: Intruder Detected",
                body = "Multiple failed authentication attempts detected on your secured device. Evidence is attached.",
                isUrgent = true
            ),
            "DURESS" to EmailTemplate(
                subject = "Emergency Alert: Duress Code Activated",
                body = "The duress code has been entered on your device. Covert surveillance evidence is attached.",
                isUrgent = true
            ),
            "SIM_CHANGE" to EmailTemplate(
                subject = "Security Alert: SIM Card Removed or Changed",
                body = "The SIM card in your secured device has been removed or replaced. This may indicate theft or unauthorized access.",
                isUrgent = true
            ),
            "DEVICE_MOVED" to EmailTemplate(
                subject = "Security Alert: Device Left Safe Zone",
                body = "Your secured device has been moved from its designated safe zone."
            ),
            "SYSTEM_BREACH" to EmailTemplate(
                subject = "CRITICAL Alert: Security System Compromised",
                body = "An attempt to disable or tamper with the Uncle Ted security system has been detected.",
                isUrgent = true
            ),
            "SYSTEM_TAMPER" to EmailTemplate(
                subject = "Security Alert: System Tampering Detected",
                body = "A potential attempt to tamper with the device has been detected.",
                isUrgent = true
            ),
            "REMOTE_ACTION" to EmailTemplate(
                subject = "Security Alert: Remote Action Triggered",
                body = "A remote action was successfully triggered on the device.",
                isUrgent = false
            ),
            "GENERIC_MEDIUM" to EmailTemplate(
                subject = "Security Alert: Medium Priority Event",
                body = "A medium priority security event was detected.",
                isUrgent = false
            ),
            "GENERIC_HIGH" to EmailTemplate(
                subject = "URGENT Security Alert: High Priority Event",
                body = "A high priority security event was detected.",
                isUrgent = true
            ),
            "URGENT_PREAMBLE" to EmailTemplate(
                subject = "CRITICAL ALERT",
                body = "This is an urgent preliminary alert.",
                isUrgent = true
            )
        )
    }
}
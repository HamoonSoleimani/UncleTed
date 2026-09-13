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

object EmailSender {

    private const val TAG = "EmailSender"

    data class EmailConfig(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val enableSslTls: Boolean
    )

    fun setEmailConfig(context: Context, config: EmailConfig) = with(SecurityPreferences) {
        setEmailHost(context, config.host)
        setEmailPort(context, config.port)
        setEmailUsername(context, config.username)
        setEmailPassword(context, config.password)
        setEnableSslTls(context, config.enableSslTls)
    }

    fun getEmailConfig(context: Context): EmailConfig? = with(SecurityPreferences) {
        val host = getEmailHost(context)
        val port = getEmailPort(context)
        val username = getEmailUsername(context)
        val password = getEmailPassword(context)
        val enableSslTls = isEnableSslTls(context)

        return if (!host.isNullOrEmpty() && port != 0 && !username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            EmailConfig(host, port, username, password, enableSslTls)
        } else {
            null
        }
    }

    suspend fun sendEmail(
        context: Context,
        recipientEmail: String,
        subject: String,
        body: String,
        attachmentFile: File? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val config = getEmailConfig(context)
        if (config == null) {
            Log.e(TAG, "Email configuration is missing or incomplete. Cannot send email.")
            return@withContext false
        }
        if (!PermissionUtils.hasInternetPermission(context)) {
            Log.e(TAG, "INTERNET permission not granted. Cannot send email.")
            return@withContext false
        }

        val originalClassLoader = Thread.currentThread().contextClassLoader
        try {
            // Fix 1: Ensure Thread Context ClassLoader has access to APK classes (JavaMail provider lookup)
            Thread.currentThread().contextClassLoader = context.classLoader

            // Fix 2: Register mailcap command map handlers to prevent "No content handler" errors
            val mc = CommandMap.getDefaultCommandMap() as MailcapCommandMap
            mc.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html")
            mc.addMailcap("text/xml;; x-java-content-handler=com.sun.mail.handlers.text_xml")
            mc.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain")
            mc.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed")
            mc.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822")
            CommandMap.setDefaultCommandMap(mc)

            val props = Properties()
            // Fix 3: Explicitly bind the transport protocol and class to avoid META-INF lookup failures
            props["mail.transport.protocol"] = "smtp"
            props["mail.smtp.class"] = "com.sun.mail.smtp.SMTPTransport"

            props["mail.smtp.host"] = config.host
            props["mail.smtp.port"] = config.port.toString()
            props["mail.smtp.auth"] = "true"
            props["mail.smtp.connectiontimeout"] = "10000"
            props["mail.smtp.timeout"] = "10000"

            if (config.enableSslTls) {
                props["mail.smtp.starttls.enable"] = "true"
                props["mail.smtp.ssl.protocols"] = "TLSv1.2"
                props["mail.smtp.ssl.trust"] = config.host
                props["mail.smtp.socketFactory.port"] = config.port.toString()
                props["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
                props["mail.smtp.socketFactory.fallback"] = "false"
            } else {
                props["mail.smtp.starttls.enable"] = "false"
                props["mail.smtp.ssl.enable"] = "false"
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(config.username, config.password)
                }
            })

            val message = MimeMessage(session)
            message.setFrom(InternetAddress(config.username))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail))
            message.subject = subject

            val multipart = MimeMultipart()

            val messageBodyPart = MimeBodyPart()
            messageBodyPart.setText(body)
            multipart.addBodyPart(messageBodyPart)

            if (attachmentFile != null && attachmentFile.exists()) {
                val attachmentBodyPart = MimeBodyPart()
                val dataSource = FileDataSource(attachmentFile)
                attachmentBodyPart.dataHandler = DataHandler(dataSource)
                attachmentBodyPart.fileName = attachmentFile.name
                multipart.addBodyPart(attachmentBodyPart)
                Log.d(TAG, "Attaching file: ${attachmentFile.name}")
            }

            message.setContent(multipart)
            Transport.send(message)
            Log.d(TAG, "Email sent successfully to $recipientEmail")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending email to $recipientEmail: ${e.message}", e)
            false
        } finally {
            // Restore original ClassLoader
            Thread.currentThread().contextClassLoader = originalClassLoader
        }
    }
}
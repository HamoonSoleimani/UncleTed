package com.hamoon.uncleted.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLogCollector {

    private const val TAG = "DiagnosticLogCollector"

    data class DeviceEnvironment(
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val apiLevel: Int,
        val buildFingerprint: String,
        val isRooted: Boolean,
        val rootProvider: String,
        val isPrivAppMounted: Boolean,
        val selinuxMode: String
    )

    suspend fun getEnvironmentDiagnostics(context: Context): DeviceEnvironment = withContext(Dispatchers.IO) {
        val isRooted = RootChecker.isDeviceRooted()
        val provider = if (isRooted) RootChecker.getRootProvider().name else "NONE"

        var privAppMounted = false
        var selinux = "Unknown"

        if (isRooted) {
            val mountCheck = RootExecutor.run("mount | grep -i uncleted", logErrors = false)
            val directFile = RootExecutor.run("ls -la /system/priv-app/UncleTed/UncleTed.apk 2>/dev/null", logErrors = false)
            privAppMounted = mountCheck.isSuccess || (directFile.isSuccess && directFile.output.isNotEmpty())

            val seResult = RootExecutor.run("getenforce", logErrors = false)
            if (seResult.isSuccess && seResult.output.isNotEmpty()) {
                selinux = seResult.output.first().trim()
            }
        } else {
            privAppMounted = (context.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        }

        DeviceEnvironment(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            buildFingerprint = Build.FINGERPRINT,
            isRooted = isRooted,
            rootProvider = provider,
            isPrivAppMounted = privAppMounted,
            selinuxMode = selinux
        )
    }

    suspend fun captureDiagnosticDump(context: Context, logScope: String = "ALL"): File? = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputDir = File(context.filesDir, "diagnostics")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val logFile = File(outputDir, "uncleted_bugreport_$timestamp.txt")

        try {
            val env = getEnvironmentDiagnostics(context)

            FileOutputStream(logFile).bufferedWriter().use { writer ->
                writer.write("================================================================\n")
                writer.write("UNCLE TED EXPERIMENTAL BUG DIAGNOSTIC REPORT\n")
                writer.write("Generated: ${Date()}\n")
                writer.write("================================================================\n\n")

                writer.write("--- DEVICE PLATFORM METRICS ---\n")
                writer.write("Brand/Model: ${env.manufacturer} ${env.model}\n")
                writer.write("Android OS: ${env.androidVersion} (API ${env.apiLevel})\n")
                writer.write("Build Fingerprint: ${env.buildFingerprint}\n")
                writer.write("Root Authority: ${if (env.isRooted) "ROOTED (${env.rootProvider})" else "UNROOTED"}\n")
                writer.write("SELinux Enforce: ${env.selinuxMode}\n")
                writer.write("Priv-App System Mount Status: ${if (env.isPrivAppMounted) "MOUNTED (/system/priv-app)" else "FAILED (Running as /data/app user app)"}\n")
                writer.write("Host Process PID: ${Process.myPid()}\n\n")

                if (env.isRooted) {
                    writer.write("--- MOUNT & PACKAGE ENVIRONMENT (ROOT) ---\n")
                    val ksuCheck = RootExecutor.run("ksud -V 2>/dev/null || which ksud magisk apd", logErrors = false)
                    writer.write("Root Daemon Version: ${ksuCheck.output.joinToString(" ")}\n")

                    val mountDump = RootExecutor.run("mount | grep -E 'priv-app|overlay|uncleted'", logErrors = false)
                    writer.write("Active System Mounts:\n${mountDump.output.joinToString("\n")}\n")

                    val pkgDump = RootExecutor.run("dumpsys package ${context.packageName} | grep -E 'userId=|pkgFlags=|versionCode=|dataDir='", logErrors = false)
                    writer.write("Package State:\n${pkgDump.output.joinToString("\n")}\n\n")

                    val dmesgSnippet = RootExecutor.run("dmesg | grep -iE 'touch|sec_ts|goodix|synaptics|usb|dwc3|mte' | tail -n 80", logErrors = false)
                    if (dmesgSnippet.isSuccess && dmesgSnippet.output.isNotEmpty()) {
                        writer.write("--- KERNEL DRIVER RING BUFFER (DMESG TAIL) ---\n")
                        writer.write(dmesgSnippet.output.joinToString("\n"))
                        writer.write("\n\n")
                    }
                }

                writer.write("--- IN-APP EVENT AUDIT LOGS ---\n")
                val auditLogs = EventLogger.getLogs(context)
                if (auditLogs.isEmpty()) {
                    writer.write("(No in-memory audit logs recorded)\n")
                } else {
                    for (entry in auditLogs) {
                        writer.write("$entry\n")
                    }
                }
                writer.write("\n")

                writer.write("--- LOGCAT BUFFER DUMP (SCOPE: $logScope) ---\n")
                val logcatCmd = when {
                    env.isRooted && logScope == "APP_ONLY" -> arrayOf("su", "-c", "logcat -d -v time --pid=${Process.myPid()} *:V")
                    env.isRooted -> arrayOf("su", "-c", "logcat -d -v time *:V")
                    else -> arrayOf("logcat", "-d", "-v", "time", "--pid=${Process.myPid()}", "*:V")
                }

                try {
                    val process = ProcessBuilder(*logcatCmd).start()
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val sanitized = sanitizeLine(line)
                            writer.write("$sanitized\n")
                        }
                    }
                    process.waitFor()
                } catch (pe: Exception) {
                    writer.write("Failed capturing logcat process: ${pe.message}\n")
                }

                writer.write("\n=== END OF REPORT ===\n")
            }

            Log.i(TAG, "Diagnostic bug report captured at: ${logFile.absolutePath}")
            return@withContext logFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed capturing diagnostic dump: ${e.message}", e)
            return@withContext null
        }
    }

    fun createShareIntent(context: Context, logFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Uncle Ted Bug Report (${Build.MODEL})")
            putExtra(Intent.EXTRA_TEXT, "Attached is the experimental diagnostic logcat report for Uncle Ted.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun sanitizeLine(line: String): String {
        return line.replace(Regex("(?i)(pin|password|secret|salt)\\s*=\\s*['\"]?[^'\"\\s]+['\"]?"), "$1=[REDACTED]")
    }
}
package com.hamoon.uncleted.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object RootExecutor {

    private const val TAG = "RootExecutor"
    private const val COMMAND_TIMEOUT_SECONDS = 15L

    data class CommandResult(
        val output: List<String>,
        val errorOutput: List<String>,
        val exitCode: Int
    ) {
        val isSuccess: Boolean
            get() = exitCode == 0

        val isRootAvailable: Boolean
            get() = exitCode != -1
    }

    suspend fun run(command: String, logErrors: Boolean = true): CommandResult = withContext(Dispatchers.IO) {
        var process: Process? = null
        return@withContext try {
            Log.d(TAG, "Executing root command: '$command'")

            process = ProcessBuilder("su").start()

            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("$command\n")
                os.flush()
                os.writeBytes("exit\n")
                os.flush()
            }

            val outputDeferred = async(Dispatchers.IO) {
                try {
                    process.inputStream.bufferedReader().readLines()
                } catch (_: Exception) { emptyList<String>() }
            }

            val errorDeferred = async(Dispatchers.IO) {
                try {
                    process.errorStream.bufferedReader().readLines()
                } catch (_: Exception) { emptyList<String>() }
            }

            val processCompleted = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            if (!processCompleted) {
                if (logErrors) Log.w(TAG, "Command timed out: '$command'")
                process.destroyForcibly()
                CommandResult(emptyList(), listOf("Command timed out"), -1)
            } else {
                val exitCode = process.exitValue()
                val output = outputDeferred.await()
                val errorOutput = errorDeferred.await()

                if (!CommandResult(output, errorOutput, exitCode).isSuccess && logErrors) {
                    Log.e(TAG, "Command failed ($exitCode): $errorOutput")
                }

                CommandResult(output, errorOutput, exitCode)
            }

        } catch (e: IOException) {
            if (logErrors) Log.d(TAG, "Root command execution failed: ${e.message}")
            CommandResult(emptyList(), listOf("Root not available: ${e.message}"), -1)
        } catch (e: Exception) {
            if (logErrors) Log.w(TAG, "Unexpected exception: ${e.message}", e)
            CommandResult(emptyList(), listOf("Unexpected error: ${e.message}"), -1)
        } finally {
            process?.destroy()
        }
    }

    suspend fun runMultiple(commands: List<String>, logErrors: Boolean = true): List<CommandResult> {
        if (commands.isEmpty()) return emptyList()
        val combined = commands.joinToString(" ; ")
        val result = run(combined, logErrors)
        return listOf(result)
    }

    suspend fun isRootAvailable(): Boolean {
        return try {
            val result = run("echo test", logErrors = false)
            result.isRootAvailable && result.isSuccess
        } catch (_: Exception) {
            false
        }
    }
}
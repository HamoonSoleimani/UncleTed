package com.hamoon.uncleted.hooks

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.File
import java.nio.charset.StandardCharsets

class LockscreenHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "UncleTed-LockHook"
        private const val TARGET_PACKAGE = "android"
        private const val LOCK_SETTINGS_CLASS = "com.android.server.locksettings.LockSettingsService"
        private const val CREDENTIALS_FILE = "/data/system/uncleted/credentials.cfg"

        private const val COOLDOWN_MS = 1500L

        @Volatile
        private var lastInterceptTime = 0L

        @Volatile
        private var lastFailureBroadcastTime = 0L

        @Volatile
        private var lastSuccessBroadcastTime = 0L

        @Volatile
        private var lastAttemptWasSpecialPin = false

        @Volatile
        private var systemContext: Context? = null
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        try {
            val lockSettingsClass = XposedHelpers.findClass(LOCK_SETTINGS_CLASS, lpparam.classLoader)
            hookCredentialVerification(lockSettingsClass)
            Log.i(TAG, "LockSettingsService hooked successfully in system_server.")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed hooking LockSettingsService: ${t.message}", t)
        }
    }

    private fun hookCredentialVerification(lockSettingsClass: Class<*>) {
        val hookCallback = object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val now = System.currentTimeMillis()
                val enteredPin = extractPinFromArguments(param.args)

                if (enteredPin.isNullOrEmpty()) return

                if (now - lastInterceptTime < COOLDOWN_MS) {
                    return
                }
                lastInterceptTime = now
                lastAttemptWasSpecialPin = false

                Log.d(TAG, "Credential verification intercepted: [length=${enteredPin.length}]")

                val context = getContextFromParam(param)
                if (context != null) {
                    systemContext = context
                }

                val config = loadTargetCredentials()
                val wipePin = config["wipe_pin"]
                val duressPin = config["duress_pin"]
                val honeypotPin = config["honeypot_pin"]
                val decoyUserId = config["decoy_user_id"]?.toIntOrNull() ?: -1

                // 1. WIPE PIN INTERCEPTION
                if (!wipePin.isNullOrEmpty() && enteredPin == wipePin) {
                    lastAttemptWasSpecialPin = true
                    Log.e(TAG, "WIPE PIN matched at OS level. Aborting auth and initiating emergency destruction.")
                    abortAuthenticationFlow(param)
                    executeSystemServerWipe(context ?: systemContext)
                    return
                }

                // 2. DURESS PIN INTERCEPTION (Silent Trap: Show incorrect PIN on lockscreen & alert)
                if (!duressPin.isNullOrEmpty() && enteredPin == duressPin) {
                    lastAttemptWasSpecialPin = true
                    Log.w(TAG, "DURESS PIN matched at OS level. Rejecting unlock & dispatching silent duress broadcast.")
                    dispatchDuressBroadcast(context ?: systemContext)
                    abortAuthenticationFlow(param)
                    return
                }

                // 3. MASTERCLASS HONEYPOT: Native Android Multi-User Switch
                if (!honeypotPin.isNullOrEmpty() && enteredPin == honeypotPin) {
                    lastAttemptWasSpecialPin = true
                    Log.w(TAG, "HONEYPOT PIN matched at OS level! Executing native multi-user switch to Decoy (UID $decoyUserId)...")

                    dispatchHoneypotBroadcast(context ?: systemContext)

                    val currentCtx = context ?: systemContext
                    if (currentCtx != null && decoyUserId > 0) {
                        try {
                            val am = currentCtx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                            XposedHelpers.callMethod(am, "switchUser", decoyUserId)
                            Log.i(TAG, "Native switchUser($decoyUserId) called successfully from system_server.")
                        } catch (t: Throwable) {
                            Log.e(TAG, "Failed calling switchUser directly. Executing shell fallback.", t)
                            Thread {
                                try {
                                    Runtime.getRuntime().exec(arrayOf("am", "switch-user", decoyUserId.toString()))
                                } catch (_: Throwable) {}
                            }.start()
                        }
                    }

                    abortAuthenticationFlow(param)
                    return
                }
            }

            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                if (lastAttemptWasSpecialPin) return

                val result = param.result ?: return
                var isFailed = false
                var isSuccess = false

                val className = result.javaClass.name
                if (className.contains("VerifyCredentialResponse")) {
                    try {
                        var code: Int? = null
                        try {
                            code = XposedHelpers.callMethod(result, "getResponseCode") as? Int
                        } catch (_: Throwable) {}

                        if (code == null) {
                            try {
                                code = XposedHelpers.getIntField(result, "mResponseCode")
                            } catch (_: Throwable) {
                                try {
                                    code = XposedHelpers.getIntField(result, "responseCode")
                                } catch (_: Throwable) {}
                            }
                        }

                        if (code != null) {
                            if (code == 0) {
                                isSuccess = true
                            } else {
                                isFailed = true
                            }
                        } else {
                            val timeout = try {
                                XposedHelpers.callMethod(result, "getTimeout") as? Int ?: 0
                            } catch (_: Throwable) { 0 }

                            val payload = try {
                                XposedHelpers.callMethod(result, "getPayload") as? ByteArray
                            } catch (_: Throwable) { null }

                            if (payload != null && timeout == 0) {
                                isSuccess = true
                            } else {
                                isFailed = true
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error parsing VerifyCredentialResponse: ${t.message}")
                    }
                } else if (result is Boolean) {
                    if (result) {
                        isSuccess = true
                    } else {
                        isFailed = true
                    }
                }

                val context = getContextFromParam(param) ?: systemContext
                val now = System.currentTimeMillis()

                if (isFailed) {
                    if (now - lastFailureBroadcastTime >= COOLDOWN_MS) {
                        lastFailureBroadcastTime = now
                        Log.w(TAG, "Authentication failed. Broadcasting lockscreen failure event.")
                        dispatchFailedAttemptBroadcast(context)
                    }
                } else if (isSuccess) {
                    if (now - lastSuccessBroadcastTime >= COOLDOWN_MS) {
                        lastSuccessBroadcastTime = now
                        Log.i(TAG, "Authentication succeeded. Broadcasting lockscreen success event.")
                        dispatchSuccessAttemptBroadcast(context)
                    }
                }
            }
        }

        val methodsToHook = listOf(
            "checkCredential",
            "verifyCredential",
            "spBasedDoVerifyCredential",
            "doVerifyCredential"
        )

        for (method in methodsToHook) {
            try {
                XposedBridge.hookAllMethods(lockSettingsClass, method, hookCallback)
                Log.d(TAG, "Hooked LockSettingsService.$method")
            } catch (_: Throwable) {}
        }
    }

    private fun extractPinFromArguments(args: Array<Any?>?): String? {
        if (args == null || args.isEmpty()) return null

        for (arg in args) {
            if (arg == null) continue

            // Android 9 (Pie) legacy string format
            if (arg is String && arg.isNotEmpty()) {
                return arg
            }

            // Android 9 (Pie) raw byte format
            if (arg is ByteArray && arg.isNotEmpty()) {
                return String(arg, StandardCharsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
            }

            if (arg is CharSequence && arg.isNotEmpty()) {
                return arg.toString()
            }

            // Android 10 - 14 LockscreenCredential class
            if (arg.javaClass.name.contains("LockscreenCredential")) {
                try {
                    val credentialObj = XposedHelpers.callMethod(arg, "getCredential")
                    if (credentialObj is ByteArray) {
                        return String(credentialObj, StandardCharsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                    }
                } catch (_: Throwable) {}

                try {
                    val mCredential = XposedHelpers.getObjectField(arg, "mCredential")
                    if (mCredential is ByteArray) {
                        return String(mCredential, StandardCharsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                    }
                } catch (_: Throwable) {}
            }
        }
        return null
    }

    private fun loadTargetCredentials(): Map<String, String> {
        val creds = mutableMapOf<String, String>()
        try {
            val file = File(CREDENTIALS_FILE)
            if (file.exists() && file.canRead()) {
                file.readLines().forEach { line ->
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        creds[parts[0].trim()] = parts[1].trim()
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed reading credentials bridge: ${t.message}")
        }
        return creds
    }

    private fun getContextFromParam(param: XC_MethodHook.MethodHookParam): Context? {
        return try {
            XposedHelpers.getObjectField(param.thisObject, "mContext") as? Context
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Cross-Version Return Type Safety:
     * Handles methods returning boolean (Android 9/10/custom ROMs) vs VerifyCredentialResponse (Android 11–14).
     * Prevents fatal ClassCastExceptions and ART type mismatch crashes.
     */
    private fun abortAuthenticationFlow(param: XC_MethodHook.MethodHookParam) {
        try {
            val returnType = (param.method as? java.lang.reflect.Method)?.returnType
            if (returnType != null && returnType != Void.TYPE) {

                // Legacy Android 9/10 boolean method signature check
                if (returnType == Boolean::class.javaPrimitiveType || returnType == java.lang.Boolean::class.java) {
                    param.result = false
                    return
                }

                // Integer response code check
                if (returnType == Int::class.javaPrimitiveType || returnType == java.lang.Integer::class.java) {
                    param.result = 1 // Non-zero indicates auth failure
                    return
                }

                // Modern Android 10-14 VerifyCredentialResponse check
                try {
                    val responseClass = XposedHelpers.findClass(
                        "com.android.internal.widget.VerifyCredentialResponse",
                        param.thisObject.javaClass.classLoader
                    )
                    param.result = XposedHelpers.getStaticObjectField(responseClass, "ERROR")
                } catch (_: Throwable) {
                    param.result = null
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed aborting authentication: ${t.message}")
        }
    }

    private fun executeSystemServerWipe(context: Context?) {
        Thread {
            try {
                Log.e(TAG, "Invoking platform wipe from system_server UID=${Process.myUid()}")
                val recoverySystemClass = Class.forName("android.os.RecoverySystem")
                val methods = recoverySystemClass.declaredMethods.filter { it.name == "rebootWipeUserData" }

                for (m in methods) {
                    m.isAccessible = true
                    val paramTypes = m.parameterTypes
                    try {
                        when (paramTypes.size) {
                            5 -> {
                                m.invoke(null, context, false, "UncleTed_Duress_Wipe", true, false)
                                return@Thread
                            }
                            4 -> {
                                m.invoke(null, context, false, "UncleTed_Duress_Wipe", true)
                                return@Thread
                            }
                            3 -> {
                                if (paramTypes[1] == Boolean::class.javaPrimitiveType) {
                                    m.invoke(null, context, false, "UncleTed_Duress_Wipe")
                                    return@Thread
                                }
                            }
                            2 -> {
                                if (paramTypes[1] == String::class.java) {
                                    m.invoke(null, context, "UncleTed_Duress_Wipe")
                                    return@Thread
                                }
                            }
                        }
                    } catch (invEx: Throwable) {
                        Log.w(TAG, "Platform wipe reflection failed on overload (${paramTypes.size} args): ${invEx.message}")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "RecoverySystem execution error: ${t.message}", t)
            }

            try {
                Runtime.getRuntime().exec(arrayOf("/system/bin/reboot", "recovery"))
            } catch (_: Throwable) {}
        }.start()
    }

    private fun dispatchDuressBroadcast(context: Context?) {
        if (context == null) return
        try {
            val intent = Intent("com.hamoon.uncleted.ACTION_DURESS_TRIGGERED").apply {
                setPackage("com.hamoon.uncleted")
                putExtra("REASON", "DURESS_PIN_LOCKSCREEN")
                putExtra("SEVERITY", "HIGH")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed dispatching duress broadcast: ${t.message}", t)
        }
    }

    private fun dispatchHoneypotBroadcast(context: Context?) {
        if (context == null) return
        try {
            val intent = Intent("com.hamoon.uncleted.ACTION_HONEYPOT_TRIGGERED").apply {
                setPackage("com.hamoon.uncleted")
                putExtra("REASON", "HONEYPOT_PIN_LOCKSCREEN")
                putExtra("SEVERITY", "HIGH")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed dispatching honeypot broadcast: ${t.message}", t)
        }
    }

    private fun dispatchFailedAttemptBroadcast(context: Context?) {
        if (context == null) return
        try {
            val intent = Intent("com.hamoon.uncleted.ACTION_LOCKSCREEN_FAILED_ATTEMPT").apply {
                setPackage("com.hamoon.uncleted")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed dispatching failure broadcast: ${t.message}", t)
        }
    }

    private fun dispatchSuccessAttemptBroadcast(context: Context?) {
        if (context == null) return
        try {
            val intent = Intent("com.hamoon.uncleted.ACTION_LOCKSCREEN_SUCCESS").apply {
                setPackage("com.hamoon.uncleted")
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed dispatching success broadcast: ${t.message}", t)
        }
    }
}
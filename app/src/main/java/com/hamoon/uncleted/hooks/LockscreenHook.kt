package com.hamoon.uncleted.hooks

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class LockscreenHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "UncleTed-LockHook"
        private const val TARGET_PACKAGE = "android"
        private const val LOCK_SETTINGS_CLASS = "com.android.server.locksettings.LockSettingsService"
        private const val USER_CONTROLLER_CLASS = "com.android.server.am.UserController"
        private const val USER_SWITCH_DIALOG_CLASS = "com.android.server.am.UserSwitchingDialog"
        private const val CREDENTIALS_FILE = "/data/system/uncleted/credentials.cfg"
        private const val RECEIVER_CLASS = "com.hamoon.uncleted.receivers.DuressHookReceiver"
        private const val TARGET_APP_PKG = "com.hamoon.uncleted"

        private const val DEBOUNCE_MS = 1500L

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

        // 1. Hook credential verification inside LockSettingsService
        try {
            val lockSettingsClass = XposedHelpers.findClass(LOCK_SETTINGS_CLASS, lpparam.classLoader)
            hookCredentialVerification(lockSettingsClass, lpparam)
            Log.i(TAG, "LockSettingsService hooked in system_server (PID: ${Process.myPid()}).")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed hooking LockSettingsService: ${t.message}", t)
        }

        // 2. Suppress the AOSP "Switching to..." UserSwitchingDialog for 100% stealth transition
        hookUserSwitchingDialogSuppression(lpparam)
    }

    private fun hookUserSwitchingDialogSuppression(lpparam: LoadPackageParam) {
        try {
            val userControllerClass = XposedHelpers.findClass(USER_CONTROLLER_CLASS, lpparam.classLoader)
            val suppressDialogHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // Mute and prevent inflation of the full-screen "Switching User" dialog
                    param.result = null
                }
            }

            XposedBridge.hookAllMethods(userControllerClass, "showUserSwitchDialog", suppressDialogHook)
            XposedBridge.hookAllMethods(userControllerClass, "showUserSwitchingDialog", suppressDialogHook)
            Log.i(TAG, "UserController switch dialog hooks armed (Stealth mode active).")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not hook UserController dialog: ${t.message}")
        }

        try {
            val dialogClass = XposedHelpers.findClass(USER_SWITCH_DIALOG_CLASS, lpparam.classLoader)
            XposedBridge.hookAllMethods(dialogClass, "show", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = null
                }
            })
        } catch (_: Throwable) {}
    }

    private fun hookCredentialVerification(lockSettingsClass: Class<*>, lpparam: LoadPackageParam) {
        val hookCallback = object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val enteredPin = extractPinFromArguments(param.args)
                if (enteredPin.isNullOrEmpty()) return

                val now = System.currentTimeMillis()
                if (now - lastInterceptTime < DEBOUNCE_MS) return

                val context = getContextFromParam(param)
                if (context != null) {
                    systemContext = context
                }

                val config = loadTargetCredentials()

                val wipePinPlain = config["wipe_pin"]
                val wipeHash = config["wipe_pin_hash"]
                val wipeSalt = config["wipe_pin_salt"]

                val duressPinPlain = config["duress_pin"]
                val duressHash = config["duress_pin_hash"]
                val duressSalt = config["duress_pin_salt"]

                val honeypotPinPlain = config["honeypot_pin"]
                val honeypotHash = config["honeypot_pin_hash"]
                val honeypotSalt = config["honeypot_pin_salt"]
                var decoyUserId = config["decoy_user_id"]?.toIntOrNull() ?: -1

                // 1. WIPE PIN (Lethal platform erasure)
                if (matchesCredential(enteredPin, wipePinPlain, wipeHash, wipeSalt)) {
                    lastInterceptTime = now + 4000L
                    lastAttemptWasSpecialPin = true
                    Log.e(TAG, "WIPE PIN matched at OS level! Executing emergency wipe.")
                    abortAuthenticationFlow(param)
                    executeSystemServerWipe(context ?: systemContext)
                    return
                }

                // 2. DURESS PIN (Covert canary + false 'Wrong PIN')
                if (matchesCredential(enteredPin, duressPinPlain, duressHash, duressSalt)) {
                    lastInterceptTime = now + 4000L
                    lastAttemptWasSpecialPin = true
                    Log.w(TAG, "DURESS PIN matched at OS level! Rejecting unlock & dispatching canary.")
                    dispatchDuressBroadcast(context ?: systemContext)
                    abortAuthenticationFlow(param)
                    return
                }

                // 3. HONEYPOT PIN (Instant In-Process Decoy Migration)
                if (matchesCredential(enteredPin, honeypotPinPlain, honeypotHash, honeypotSalt)) {
                    lastInterceptTime = now + 5000L
                    lastAttemptWasSpecialPin = true

                    if (decoyUserId <= 0) {
                        decoyUserId = resolveDecoyUserId(lpparam)
                    }

                    Log.w(TAG, "HONEYPOT PIN matched! Executing atomic in-process switch to Decoy User $decoyUserId...")

                    val ctx = context ?: systemContext
                    val switched = switchUserInSystemServer(decoyUserId, ctx, lpparam)

                    // Complete authentication as RESPONSE_OK so the bouncer dissolves instantly without error animations
                    completeAuthenticationSuccess(param)

                    // Dispatch telemetry with ALREADY_SWITCHED flag
                    dispatchHoneypotBroadcast(ctx, decoyUserId, switched)
                    return
                }

                lastInterceptTime = now
                lastAttemptWasSpecialPin = false
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
                            if (code == 0) isSuccess = true else isFailed = true
                        } else {
                            val timeout = try {
                                XposedHelpers.callMethod(result, "getTimeout") as? Int ?: 0
                            } catch (_: Throwable) { 0 }

                            val payload = try {
                                XposedHelpers.callMethod(result, "getPayload") as? ByteArray
                            } catch (_: Throwable) { null }

                            if (payload != null && timeout == 0) isSuccess = true else isFailed = true
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error parsing response: ${t.message}")
                    }
                } else if (result is Boolean) {
                    if (result) isSuccess = true else isFailed = true
                }

                val context = getContextFromParam(param) ?: systemContext
                val now = System.currentTimeMillis()

                if (isFailed) {
                    if (now - lastFailureBroadcastTime >= DEBOUNCE_MS) {
                        lastFailureBroadcastTime = now
                        dispatchFailedAttemptBroadcast(context)
                    }
                } else if (isSuccess) {
                    if (now - lastSuccessBroadcastTime >= DEBOUNCE_MS) {
                        lastSuccessBroadcastTime = now
                        dispatchSuccessAttemptBroadcast(context)
                    }
                }
            }
        }

        val methodsToHook = listOf(
            "verifyCredential",
            "checkCredential"
        )

        for (method in methodsToHook) {
            try {
                XposedBridge.hookAllMethods(lockSettingsClass, method, hookCallback)
                Log.d(TAG, "Hooked LockSettingsService.$method")
            } catch (_: Throwable) {}
        }
    }

    private fun switchUserInSystemServer(targetUserId: Int, context: Context?, lpparam: LoadPackageParam): Boolean {
        if (targetUserId <= 0) return false
        val token = Binder.clearCallingIdentity()
        try {
            context?.let { ctx ->
                try {
                    Settings.Global.putInt(ctx.contentResolver, "allow_user_switching_when_system_user_locked", 1)
                    Settings.Global.putInt(ctx.contentResolver, "add_users_when_locked", 1)
                } catch (_: Throwable) {}
            }

            // Method 1: Direct ServiceManager retrieval of IActivityManager
            try {
                val smClass = XposedHelpers.findClass("android.os.ServiceManager", lpparam.classLoader)
                val amBinder = XposedHelpers.callStaticMethod(smClass, "getService", "activity") as? android.os.IBinder
                if (amBinder != null) {
                    val amStubClass = XposedHelpers.findClass("android.app.IActivityManager\$Stub", lpparam.classLoader)
                    val iAm = XposedHelpers.callStaticMethod(amStubClass, "asInterface", amBinder)
                    if (iAm != null) {
                        val res = XposedHelpers.callMethod(iAm, "switchUser", targetUserId) as? Boolean
                        if (res == true) {
                            Log.i(TAG, "In-process IActivityManager.switchUser($targetUserId) succeeded.")
                            return true
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "ServiceManager activity.switchUser failed: ${t.message}")
            }

            // Method 2: ActivityManager.getService().switchUser(targetUserId)
            try {
                val amClass = XposedHelpers.findClass("android.app.ActivityManager", lpparam.classLoader)
                val iAm = XposedHelpers.callStaticMethod(amClass, "getService")
                if (iAm != null) {
                    val res = XposedHelpers.callMethod(iAm, "switchUser", targetUserId) as? Boolean
                    if (res == true) {
                        Log.i(TAG, "In-process ActivityManager.getService().switchUser($targetUserId) succeeded.")
                        return true
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "ActivityManager.getService().switchUser failed: ${t.message}")
            }

            // Method 3: Context.getSystemService(ActivityManager).switchUser(targetUserId)
            try {
                context?.let { ctx ->
                    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    if (am != null) {
                        val res = XposedHelpers.callMethod(am, "switchUser", targetUserId) as? Boolean
                        if (res == true) {
                            Log.i(TAG, "In-process ActivityManager.switchUser($targetUserId) succeeded.")
                            return true
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Context ActivityManager.switchUser failed: ${t.message}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error during switchUserInSystemServer: ${t.message}", t)
        } finally {
            Binder.restoreCallingIdentity(token)
        }
        return false
    }

    private fun completeAuthenticationSuccess(param: XC_MethodHook.MethodHookParam) {
        try {
            val returnType = (param.method as? java.lang.reflect.Method)?.returnType
            if (returnType != null && returnType != Void.TYPE) {
                if (returnType == Boolean::class.javaPrimitiveType || returnType == java.lang.Boolean::class.java) {
                    param.result = true
                    return
                }

                if (returnType == Int::class.javaPrimitiveType || returnType == java.lang.Integer::class.java) {
                    param.result = 0 // RESPONSE_OK
                    return
                }

                try {
                    val responseClass = XposedHelpers.findClass(
                        "com.android.internal.widget.VerifyCredentialResponse",
                        param.thisObject.javaClass.classLoader
                    )
                    var okObj: Any? = null

                    try {
                        okObj = XposedHelpers.getStaticObjectField(responseClass, "OK")
                    } catch (_: Throwable) {}

                    if (okObj == null) {
                        try {
                            val builderClass = XposedHelpers.findClass(
                                "com.android.internal.widget.VerifyCredentialResponse\$Builder",
                                param.thisObject.javaClass.classLoader
                            )
                            val builder = XposedHelpers.newInstance(builderClass)
                            okObj = XposedHelpers.callMethod(builder, "build")
                        } catch (_: Throwable) {}
                    }

                    if (okObj == null) {
                        try {
                            okObj = XposedHelpers.callStaticMethod(responseClass, "fromCode", 0)
                        } catch (_: Throwable) {}
                    }

                    if (okObj == null) {
                        try {
                            okObj = XposedHelpers.newInstance(responseClass)
                        } catch (_: Throwable) {}
                    }

                    if (okObj == null) {
                        try {
                            okObj = XposedHelpers.newInstance(responseClass, 0, 0, null)
                        } catch (_: Throwable) {}
                    }

                    if (okObj != null) {
                        param.result = okObj
                        return
                    }
                } catch (inner: Throwable) {
                    Log.e(TAG, "Failed creating VerifyCredentialResponse OK: ${inner.message}")
                }
                param.result = null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed completing authentication success: ${t.message}")
        }
    }

    private fun resolveDecoyUserId(lpparam: LoadPackageParam): Int {
        try {
            val smClass = XposedHelpers.findClass("android.os.ServiceManager", lpparam.classLoader)
            val userBinder = XposedHelpers.callStaticMethod(smClass, "getService", "user") as? android.os.IBinder
            if (userBinder != null) {
                val stubClass = XposedHelpers.findClass("android.os.IUserManager\$Stub", lpparam.classLoader)
                val um = XposedHelpers.callStaticMethod(stubClass, "asInterface", userBinder)
                val users = XposedHelpers.callMethod(um, "getUsers", true) as? List<*>
                if (users != null) {
                    for (u in users) {
                        if (u == null) continue
                        val id = XposedHelpers.getIntField(u, "id")
                        val name = XposedHelpers.getObjectField(u, "name") as? String
                        if (id != 0 && (name.equals("Personal", ignoreCase = true) || name.equals("Decoy", ignoreCase = true))) {
                            return id
                        }
                    }
                    for (u in users) {
                        if (u == null) continue
                        val id = XposedHelpers.getIntField(u, "id")
                        if (id != 0) return id
                    }
                }
            }
        } catch (_: Throwable) {}
        return -1
    }

    private fun matchesCredential(
        enteredPin: String,
        plainExpected: String?,
        hashExpected: String?,
        saltExpected: String?
    ): Boolean {
        if (!plainExpected.isNullOrEmpty() && enteredPin == plainExpected.trim()) {
            return true
        }

        if (!hashExpected.isNullOrEmpty() && !saltExpected.isNullOrEmpty()) {
            return verifyPinHash(enteredPin, hashExpected.trim(), saltExpected.trim())
        }

        return false
    }

    private fun verifyPinHash(enteredPin: String, expectedHashBase64: String, saltBase64: String): Boolean {
        return try {
            val saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP)
            val expectedHashBytes = Base64.decode(expectedHashBase64, Base64.NO_WRAP)

            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(saltBytes)
            digest.update(enteredPin.toByteArray(Charsets.UTF_8))
            digest.update(saltBytes)
            val computedHashBytes = digest.digest()

            MessageDigest.isEqual(computedHashBytes, expectedHashBytes)
        } catch (_: Exception) {
            false
        }
    }

    private fun extractPinFromArguments(args: Array<Any?>?): String? {
        if (args == null || args.isEmpty()) return null

        for (arg in args) {
            if (arg == null) continue

            if (arg is String && arg.isNotEmpty()) return arg

            if (arg is ByteArray && arg.isNotEmpty()) {
                return String(arg, StandardCharsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
            }

            if (arg is CharSequence && arg.isNotEmpty()) return arg.toString()

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
        val file = File(CREDENTIALS_FILE)

        try {
            if (!file.exists()) return creds

            FileInputStream(file).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val parts = trimmed.split("=", limit = 2)
                        if (parts.size == 2) {
                            creds[parts[0].trim()] = parts[1].trim()
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
        return creds
    }

    private fun getContextFromParam(param: XC_MethodHook.MethodHookParam): Context? {
        return try {
            XposedHelpers.getObjectField(param.thisObject, "mContext") as? Context
        } catch (_: Throwable) {
            try {
                val atClass = XposedHelpers.findClass("android.app.ActivityThread", param.thisObject.javaClass.classLoader)
                XposedHelpers.callStaticMethod(atClass, "currentApplication") as? Context
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun abortAuthenticationFlow(param: XC_MethodHook.MethodHookParam) {
        try {
            val returnType = (param.method as? java.lang.reflect.Method)?.returnType
            if (returnType != null && returnType != Void.TYPE) {
                if (returnType == Boolean::class.javaPrimitiveType || returnType == java.lang.Boolean::class.java) {
                    param.result = false
                    return
                }

                if (returnType == Int::class.javaPrimitiveType || returnType == java.lang.Integer::class.java) {
                    param.result = -1 // ERROR code
                    return
                }

                try {
                    val responseClass = XposedHelpers.findClass(
                        "com.android.internal.widget.VerifyCredentialResponse",
                        param.thisObject.javaClass.classLoader
                    )
                    var errObj: Any? = null
                    try {
                        errObj = XposedHelpers.getStaticObjectField(responseClass, "ERROR")
                    } catch (_: Throwable) {}

                    if (errObj != null) {
                        param.result = errObj
                        return
                    }

                    try {
                        param.result = XposedHelpers.callStaticMethod(responseClass, "fromCode", -1)
                        return
                    } catch (_: Throwable) {}

                    try {
                        param.result = XposedHelpers.newInstance(responseClass, -1, 0, null)
                        return
                    } catch (_: Throwable) {}

                } catch (inner: Throwable) {
                    Log.e(TAG, "Failed creating VerifyCredentialResponse ERROR: ${inner.message}")
                }
                param.result = null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed aborting authentication: ${t.message}")
        }
    }

    private fun executeSystemServerWipe(context: Context?) {
        Thread {
            try {
                Log.e(TAG, "Executing platform wipe from system_server UID=${Process.myUid()}")
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
                        Log.w(TAG, "Platform wipe overload failed: ${invEx.message}")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "RecoverySystem execution error: ${t.message}", t)
            }
        }.start()
    }

    private fun sendExplicitBroadcast(context: Context?, intent: Intent) {
        if (context == null) return
        try {
            val userSystem = XposedHelpers.getStaticObjectField(UserHandle::class.java, "SYSTEM") as? UserHandle
            if (userSystem != null) {
                XposedHelpers.callMethod(context, "sendBroadcastAsUser", intent, userSystem)
                return
            }
        } catch (_: Throwable) {}
        try {
            context.sendBroadcast(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed sending broadcast: ${t.message}", t)
        }
    }

    private fun dispatchDuressBroadcast(context: Context?) {
        val intent = Intent("com.hamoon.uncleted.ACTION_DURESS_TRIGGERED").apply {
            setPackage(TARGET_APP_PKG)
            setClassName(TARGET_APP_PKG, RECEIVER_CLASS)
            putExtra("REASON", "DURESS_PIN_LOCKSCREEN")
            putExtra("SEVERITY", "HIGH")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        sendExplicitBroadcast(context, intent)
    }

    private fun dispatchHoneypotBroadcast(context: Context?, targetUserId: Int, alreadySwitched: Boolean) {
        val intent = Intent("com.hamoon.uncleted.ACTION_HONEYPOT_TRIGGERED").apply {
            setPackage(TARGET_APP_PKG)
            setClassName(TARGET_APP_PKG, RECEIVER_CLASS)
            putExtra("REASON", "HONEYPOT_PIN_LOCKSCREEN")
            putExtra("DECOY_USER_ID", targetUserId)
            putExtra("ALREADY_SWITCHED", alreadySwitched)
            putExtra("SEVERITY", "HIGH")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        sendExplicitBroadcast(context, intent)
    }

    private fun dispatchFailedAttemptBroadcast(context: Context?) {
        val intent = Intent("com.hamoon.uncleted.ACTION_LOCKSCREEN_FAILED_ATTEMPT").apply {
            setPackage(TARGET_APP_PKG)
            setClassName(TARGET_APP_PKG, RECEIVER_CLASS)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        sendExplicitBroadcast(context, intent)
    }

    private fun dispatchSuccessAttemptBroadcast(context: Context?) {
        val intent = Intent("com.hamoon.uncleted.ACTION_LOCKSCREEN_SUCCESS").apply {
            setPackage(TARGET_APP_PKG)
            setClassName(TARGET_APP_PKG, RECEIVER_CLASS)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        sendExplicitBroadcast(context, intent)
    }
}
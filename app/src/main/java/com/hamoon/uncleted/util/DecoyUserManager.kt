package com.hamoon.uncleted.util

import android.content.Context
import android.util.Log
import com.hamoon.uncleted.crypto.EphemeralKeyDecayEngine
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DecoyUserManager {

    private const val TAG = "DecoyUserManager"
    private const val DECOY_USER_NAME = "Personal"

    @Volatile
    private var isMultiUserPropsConfigured = false

    data class DecoyStatus(
        val isSupported: Boolean,
        val exists: Boolean,
        val userId: Int
    )

    suspend fun getDecoyStatus(context: Context): DecoyStatus = withContext(Dispatchers.IO) {
        if (!RootChecker.isDeviceRooted()) {
            return@withContext DecoyStatus(isSupported = false, exists = false, userId = -1)
        }

        val cachedId = SecurityPreferences.getDecoyUserId(context)
        val existingDecoyId = if (cachedId > 0) cachedId else findExistingDecoyUserId(context)

        return@withContext DecoyStatus(
            isSupported = true,
            exists = existingDecoyId > 0,
            userId = existingDecoyId
        )
    }

    suspend fun getValidDecoyUserId(context: Context): Int = withContext(Dispatchers.IO) {
        val cachedId = SecurityPreferences.getDecoyUserId(context)
        if (cachedId > 0) {
            return@withContext cachedId
        }
        val existingId = findExistingDecoyUserId(context)
        if (existingId > 0) {
            SecurityPreferences.setDecoyUserId(context, existingId)
            return@withContext existingId
        }
        return@withContext provisionDecoyUser(context)
    }

    suspend fun provisionDecoyUser(context: Context): Int = withContext(Dispatchers.IO) {
        if (!RootChecker.isDeviceRooted()) return@withContext -1

        ensureMultiUserPropertyEnabled()

        var decoyId = findExistingDecoyUserId(context)
        if (decoyId > 0) {
            Log.i(TAG, "Decoy user profile verified with UID $decoyId. Running configuration...")
            repairAndWarmDecoyUser(decoyId)
            SecurityPreferences.setDecoyUserId(context, decoyId)
            CredentialBridge.syncCredentials(context)
            return@withContext decoyId
        }

        Log.i(TAG, "Creating secondary Android user profile: '$DECOY_USER_NAME'...")
        val createResult = RootExecutor.run("pm create-user \"$DECOY_USER_NAME\"")
        val finalResult = if (!createResult.isSuccess) {
            RootExecutor.run("pm create-user --user-type android.os.usertype.full.SECONDARY \"$DECOY_USER_NAME\"")
        } else createResult

        if (finalResult.isSuccess) {
            val rawOutput = finalResult.output.joinToString(" ")
            val parsedId = Regex("""\b(\d+)\b""").findAll(rawOutput).lastOrNull()?.value?.toIntOrNull()

            if (parsedId != null && parsedId > 0) {
                decoyId = parsedId
                Log.i(TAG, "Successfully provisioned Decoy User space (UserHandle $decoyId).")

                repairAndWarmDecoyUser(decoyId)

                SecurityPreferences.setDecoyUserId(context, decoyId)
                CredentialBridge.syncCredentials(context)
                return@withContext decoyId
            }
        }

        Log.e(TAG, "Failed creating secondary user profile: ${finalResult.errorOutput}")
        return@withContext -1
    }

    suspend fun repairAndWarmDecoyUser(userId: Int) {
        if (userId <= 0) return
        Log.i(TAG, "Configuring decoy profile policies for User $userId...")

        val warmupCmds = listOf(
            "am start-user $userId 2>/dev/null || true"
        )
        RootExecutor.runMultiple(warmupCmds, logErrors = false)

        isolateDecoyFromPrimaryApp(userId)
        configureDecoyUserPolicies(userId)
        ensureLauncherEnabledForUser(userId)
    }

    private suspend fun isolateDecoyFromPrimaryApp(userId: Int) {
        if (userId <= 0) return
        val commands = listOf(
            "pm disable --user $userId com.hamoon.uncleted 2>/dev/null || true",
            "pm hide $userId com.hamoon.uncleted 2>/dev/null || true"
        )
        RootExecutor.runMultiple(commands, logErrors = false)
    }

    private suspend fun configureDecoyUserPolicies(userId: Int) {
        val commands = listOf(
            "settings put global device_provisioned 1",
            "settings put global setup_wizard_has_run 1",
            "settings put global allow_user_switching_when_system_user_locked 1",
            "settings put global add_users_when_locked 1",
            "settings put --user $userId secure user_setup_complete 1",
            "settings put --user $userId secure tv_user_setup_complete 1",
            "settings put --user $userId secure user_setup_personalization_state 2",
            "settings put --user $userId secure skip_first_use_hints 1",
            "settings delete --user $userId secure lockscreen.disabled 2>/dev/null || true",
            "settings put --user $userId secure lockscreen.disabled 0 2>/dev/null || true",
            "pm disable --user $userId com.google.android.setupwizard 2>/dev/null || true",
            "pm disable --user $userId com.android.setupwizard 2>/dev/null || true",
            "pm disable --user $userId org.lineageos.setupwizard 2>/dev/null || true",
            "pm disable --user $userId com.google.android.setupwizard/.SetupWizardActivity 2>/dev/null || true",
            "pm disable --user $userId com.android.setupwizard/.SetupWizardActivity 2>/dev/null || true",
            "pm disable --user $userId org.lineageos.setupwizard/.SetupWizardActivity 2>/dev/null || true"
        )
        RootExecutor.runMultiple(commands, logErrors = false)
    }

    private suspend fun ensureLauncherEnabledForUser(userId: Int) {
        if (userId <= 0) return

        val resolveResult = RootExecutor.run(
            "cmd package resolve-activity --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null || pm resolve-activity --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME",
            logErrors = false
        )
        val output = resolveResult.output.joinToString("\n")
        val resolvedPkg = Regex("""(?:packageName|package)=([a-zA-Z0-9._]+)""").find(output)?.groupValues?.get(1)
            ?: Regex("""\b([a-zA-Z0-9._]+)/(?:[a-zA-Z0-9._]+)""").find(output)?.groupValues?.get(1)

        val targetLauncher = if (!resolvedPkg.isNullOrBlank() && !resolvedPkg.contains("setupwizard", ignoreCase = true)) {
            resolvedPkg.trim()
        } else {
            "com.google.android.apps.nexuslauncher"
        }

        val batchCmd = "cmd package install-existing --user $userId $targetLauncher 2>/dev/null || pm install-existing --user $userId $targetLauncher 2>/dev/null || true ; pm enable --user $userId $targetLauncher 2>/dev/null || true"
        RootExecutor.run(batchCmd, logErrors = false)
    }

    suspend fun evictPrimaryUserCeKeys(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = withContext(Dispatchers.IO) {
        Log.w(TAG, "Sanitizing primary user volatile caches and syncing filesystem buffers...")
        val commands = listOf(
            "sync",
            "echo 3 > /proc/sys/vm/drop_caches"
        )
        val result = RootExecutor.runMultiple(commands, logErrors = false)
        try {
            EphemeralKeyDecayEngine.purgeEphemeralKey(null)
        } catch (_: Exception) {}
        return@withContext result.any { it.isSuccess }
    }

    suspend fun switchToDecoyWithCeEviction(context: Context, decoyUserId: Int): Boolean = withContext(Dispatchers.IO) {
        if (decoyUserId <= 0) return@withContext false

        Log.w(TAG, "Executing fast session switch to Decoy User $decoyUserId...")

        val switchCmd = "am start-user $decoyUserId 2>/dev/null ; cmd activity switch-user $decoyUserId 2>/dev/null || am switch-user $decoyUserId"
        val result = RootExecutor.run(switchCmd, logErrors = false)

        CoroutineScope(Dispatchers.IO).launch {
            delay(5000L)
            evictPrimaryUserCeKeys(context)
        }

        return@withContext result.isSuccess
    }

    suspend fun switchToDecoyWithLauncher(context: Context, decoyUserId: Int): Boolean = withContext(Dispatchers.IO) {
        return@withContext switchToDecoyWithCeEviction(context, decoyUserId)
    }

    suspend fun switchToOwner(): Boolean = withContext(Dispatchers.IO) {
        val result = RootExecutor.run("cmd activity switch-user 0 2>/dev/null || am switch-user 0")
        return@withContext result.isSuccess
    }

    suspend fun removeDecoyUser(context: Context): Boolean = withContext(Dispatchers.IO) {
        val decoyId = findExistingDecoyUserId(context)
        if (decoyId <= 0) return@withContext true

        RootExecutor.run("am stop-user -w -f $decoyId 2>/dev/null || am stop-user $decoyId 2>/dev/null || true", logErrors = false)
        val result = RootExecutor.run("pm remove-user $decoyId")
        if (result.isSuccess) {
            SecurityPreferences.setDecoyUserId(context, -1)
            CredentialBridge.syncCredentials(context)
            return@withContext true
        }
        return@withContext false
    }

    private suspend fun findExistingDecoyUserId(context: Context): Int {
        val listResult = RootExecutor.run("pm list users", logErrors = false)
        if (listResult.isSuccess) {
            val userMap = mutableMapOf<Int, String>()
            for (line in listResult.output) {
                val match = Regex("""UserInfo\{(\d+):([^:]+):""").find(line)
                if (match != null) {
                    val id = match.groupValues[1].toIntOrNull() ?: continue
                    val name = match.groupValues[2]
                    if (id != 0) {
                        userMap[id] = name
                    }
                }
            }

            for ((id, name) in userMap) {
                if (name.equals(DECOY_USER_NAME, ignoreCase = true)) {
                    SecurityPreferences.setDecoyUserId(context, id)
                    return id
                }
            }

            if (userMap.isNotEmpty()) {
                val foundId = userMap.keys.first()
                SecurityPreferences.setDecoyUserId(context, foundId)
                return foundId
            }
        }

        SecurityPreferences.setDecoyUserId(context, -1)
        return -1
    }

    private suspend fun ensureMultiUserPropertyEnabled() {
        if (isMultiUserPropsConfigured) return
        val commands = listOf(
            "resetprop fw.max_users 5 2>/dev/null || setprop fw.max_users 5",
            "resetprop fw.show_multiuserui 1 2>/dev/null || setprop fw.show_multiuserui 1",
            "resetprop persist.sys.max_users 5 2>/dev/null || setprop persist.sys.max_users 5",
            "settings put global allow_user_switching_when_system_user_locked 1",
            "settings put global add_users_when_locked 1"
        )
        RootExecutor.runMultiple(commands, logErrors = false)
        isMultiUserPropsConfigured = true
    }
}
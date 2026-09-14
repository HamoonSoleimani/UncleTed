package com.hamoon.uncleted.util

import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.hamoon.uncleted.data.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DecoyUserManager {

    private const val TAG = "DecoyUserManager"
    private const val DECOY_USER_NAME = "Personal"

    data class DecoyStatus(
        val isSupported: Boolean,
        val exists: Boolean,
        val userId: Int
    )

    /**
     * Checks if Android multi-user is supported and whether a decoy space already exists.
     */
    suspend fun getDecoyStatus(context: Context): DecoyStatus = withContext(Dispatchers.IO) {
        if (!RootChecker.isDeviceRooted()) {
            return@withContext DecoyStatus(isSupported = false, exists = false, userId = -1)
        }

        // 1. Ensure OEM multi-user restriction is unlocked (Samsung/Carrier bypass)
        ensureMultiUserPropertyEnabled()

        // 2. Discover existing secondary users
        val existingDecoyId = findExistingDecoyUserId(context)

        return@withContext DecoyStatus(
            isSupported = true,
            exists = existingDecoyId > 0,
            userId = existingDecoyId
        )
    }

    /**
     * Creates and provisions the authentic secondary Android user space if not already created.
     */
    suspend fun provisionDecoyUser(context: Context): Int = withContext(Dispatchers.IO) {
        if (!RootChecker.isDeviceRooted()) return@withContext -1

        ensureMultiUserPropertyEnabled()

        var decoyId = findExistingDecoyUserId(context)
        if (decoyId > 0) {
            Log.i(TAG, "Decoy user already exists with UID $decoyId.")
            SecurityPreferences.setDecoyUserId(context, decoyId)
            return@withContext decoyId
        }

        Log.i(TAG, "Creating authentic secondary Android user profile: '$DECOY_USER_NAME'...")
        val createResult = RootExecutor.run("pm create-user \"$DECOY_USER_NAME\"")

        if (createResult.isSuccess) {
            // Parses output: 'Success: created user id 10'
            val rawOutput = createResult.output.joinToString(" ")
            val parsedId = Regex("""\b(\d+)\b""").findAll(rawOutput).lastOrNull()?.value?.toIntOrNull()

            if (parsedId != null && parsedId > 0) {
                decoyId = parsedId
                Log.i(TAG, "Successfully provisioned Decoy User space (UserHandle $decoyId).")

                // Remove Keyguard PIN requirement on Decoy User so switching lands directly onto the home screen
                RootExecutor.run("locksettings clear --user $decoyId")

                // Clone essential baseline system apps to make the decoy space look authentic
                cloneEssentialAppsToDecoy(decoyId)

                SecurityPreferences.setDecoyUserId(context, decoyId)
                return@withContext decoyId
            }
        }

        Log.e(TAG, "Failed creating secondary user. Output: ${createResult.errorOutput}")
        return@withContext -1
    }

    /**
     * Clones common pre-installed system packages into the decoy user space so it has a browser, calculator, etc.
     */
    private suspend fun cloneEssentialAppsToDecoy(decoyUserId: Int) {
        val packagesToClone = listOf(
            "com.android.chrome",
            "com.google.android.apps.messaging",
            "com.google.android.dialer",
            "com.google.android.calculator",
            "com.android.calculator2",
            "com.google.android.deskclock"
        )

        for (pkg in packagesToClone) {
            RootExecutor.run("pm install-existing --user $decoyUserId $pkg 2>/dev/null || true")
        }
    }

    /**
     * Switches the active OS session to the decoy space for initial setup by the owner.
     */
    suspend fun switchToDecoyUser(decoyUserId: Int): Boolean = withContext(Dispatchers.IO) {
        if (decoyUserId <= 0) return@withContext false
        val result = RootExecutor.run("am switch-user $decoyUserId")
        return@withContext result.isSuccess
    }

    /**
     * Switches back to the Primary Owner (User 0).
     */
    suspend fun switchToOwner(): Boolean = withContext(Dispatchers.IO) {
        val result = RootExecutor.run("am switch-user 0")
        return@withContext result.isSuccess
    }

    /**
     * Completely removes the decoy user and purges all /data/user/<id> storage.
     */
    suspend fun removeDecoyUser(context: Context): Boolean = withContext(Dispatchers.IO) {
        val decoyId = findExistingDecoyUserId(context)
        if (decoyId <= 0) return@withContext true

        val result = RootExecutor.run("pm remove-user $decoyId")
        if (result.isSuccess) {
            SecurityPreferences.setDecoyUserId(context, -1)
            return@withContext true
        }
        return@withContext false
    }

    private suspend fun findExistingDecoyUserId(context: Context): Int {
        // Method 1: System UserManager API
        try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
            if (userManager != null) {
                val users = userManager.userProfiles
                for (handle in users) {
                    val uid = handle.hashCode()
                    if (uid > 0) return uid
                }
            }
        } catch (_: Exception) {}

        // Method 2: Root Shell 'pm list users'
        val listResult = RootExecutor.run("pm list users")
        if (listResult.isSuccess) {
            for (line in listResult.output) {
                if (line.contains(DECOY_USER_NAME, ignoreCase = true)) {
                    // Line format: 'UserInfo{10:Personal:0}'
                    val match = Regex("""UserInfo\{(\d+):""").find(line)
                    val id = match?.groupValues?.get(1)?.toIntOrNull()
                    if (id != null && id > 0) return id
                }
            }
        }

        return SecurityPreferences.getDecoyUserId(context)
    }

    private suspend fun ensureMultiUserPropertyEnabled() {
        val currentMax = RootExecutor.run("getprop fw.max_users").output.firstOrNull()?.trim()?.toIntOrNull() ?: 1
        if (currentMax < 2) {
            RootExecutor.run("setprop fw.max_users 4")
            RootExecutor.run("setprop fw.show_multiuserui 1")
        }
    }
}
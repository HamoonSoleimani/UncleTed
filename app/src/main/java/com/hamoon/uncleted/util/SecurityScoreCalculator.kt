package com.hamoon.uncleted.util

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.hamoon.uncleted.R
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.services.PowerButtonService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SecurityScoreCalculator {

    data class ChecklistItem(
        @DrawableRes val iconRes: Int,
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
        val weight: Int,
        val isMet: () -> Boolean
    )

    data class SecurityLevel(
        val level: Int,
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
        @ColorRes val colorRes: Int
    )

    suspend fun getChecklistItems(context: Context): List<ChecklistItem> = withContext(Dispatchers.IO) {
        val isRooted = RootChecker.isDeviceRooted()
        val isAdmin = PermissionUtils.isDeviceAdminActive(context)
        val isAccessibility = isRooted || PermissionUtils.isAccessibilityServiceEnabled(context, PowerButtonService::class.java)
        val hasPerms = PermissionUtils.hasCameraPermission(context) &&
                PermissionUtils.hasLocationPermissions(context) &&
                PermissionUtils.hasSmsPermissions(context) &&
                PermissionUtils.hasPostNotificationsPermission(context)
        val normalPin = SecurityPreferences.getNormalPin(context)
        val duressPin = SecurityPreferences.getDuressPin(context)
        val hasPins = !normalPin.isNullOrEmpty() && !duressPin.isNullOrEmpty()
        val hasContact = !SecurityPreferences.getEmergencyContact(context).isNullOrEmpty()
        val isIntruder = SecurityPreferences.isIntruderSelfieEnabled(context)
        val isSim = SecurityPreferences.isSimChangeAlertEnabled(context)

        listOf(
            ChecklistItem(
                iconRes = R.drawable.ic_alert_triangle_24,
                titleRes = R.string.check_root_title,
                descriptionRes = R.string.check_root_desc,
                weight = 0,
                isMet = { isRooted }
            ),
            ChecklistItem(
                iconRes = R.drawable.ic_key_24,
                titleRes = R.string.check_admin_title,
                descriptionRes = R.string.check_admin_desc,
                weight = 25,
                isMet = { isAdmin }
            ),
            ChecklistItem(
                iconRes = R.drawable.ic_accessibility_24,
                titleRes = R.string.check_accessibility_title,
                descriptionRes = R.string.check_accessibility_desc,
                weight = 20,
                isMet = { isAccessibility }
            ),
            ChecklistItem(
                iconRes = R.drawable.ic_smartphone_24,
                titleRes = R.string.check_permissions_title,
                descriptionRes = R.string.check_permissions_desc,
                weight = 20,
                isMet = { hasPerms }
            ),
            ChecklistItem(
                iconRes = R.drawable.ic_pin_24,
                titleRes = R.string.check_pins_title,
                descriptionRes = R.string.check_pins_desc,
                weight = 15,
                isMet = { hasPins }
            ),
            ChecklistItem(
                iconRes = R.drawable.ic_contact_24,
                titleRes = R.string.check_contact_title,
                descriptionRes = R.string.check_contact_desc,
                weight = 10,
                isMet = { hasContact }
            ),
            ChecklistItem(
                iconRes = R.drawable.ic_selfie_24,
                titleRes = R.string.check_intruder_title,
                descriptionRes = R.string.check_intruder_desc,
                weight = 5,
                isMet = { isIntruder }
            ),
            ChecklistItem(
                iconRes = R.drawable.ic_sim_card_24,
                titleRes = R.string.check_sim_title,
                descriptionRes = R.string.check_sim_desc,
                weight = 5,
                isMet = { isSim }
            )
        )
    }

    suspend fun calculateSecurityLevel(context: Context): SecurityLevel = withContext(Dispatchers.IO) {
        val items = getChecklistItems(context)
        val isRooted = items.firstOrNull { it.titleRes == R.string.check_root_title }?.isMet?.invoke() ?: false

        if (isRooted) {
            return@withContext SecurityLevel(6, R.string.level_root_title, R.string.level_root_desc, R.color.level_root_god_mode)
        }

        val nonRootItems = items.filter { it.weight > 0 }
        val maxScore = nonRootItems.sumOf { it.weight }
        val currentScore = nonRootItems.filter { it.isMet() }.sumOf { it.weight }

        val percentage = if (maxScore > 0) (currentScore.toFloat() / maxScore.toFloat()) * 100 else 0f

        when {
            !PermissionUtils.isDeviceAdminActive(context) -> SecurityLevel(0, R.string.level_0_title, R.string.level_0_desc, R.color.level_1_poor)
            percentage < 25 -> SecurityLevel(1, R.string.level_1_title, R.string.level_1_desc, R.color.level_1_poor)
            percentage < 50 -> SecurityLevel(2, R.string.level_2_title, R.string.level_2_desc, R.color.level_2_fair)
            percentage < 75 -> SecurityLevel(3, R.string.level_3_title, R.string.level_3_desc, R.color.level_3_good)
            percentage < 100 -> SecurityLevel(4, R.string.level_4_title, R.string.level_4_desc, R.color.level_4_excellent)
            else -> SecurityLevel(5, R.string.level_5_title, R.string.level_5_desc, R.color.level_5_maximum)
        }
    }
}
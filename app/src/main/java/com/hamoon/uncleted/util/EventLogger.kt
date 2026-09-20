package com.hamoon.uncleted.util

import android.content.Context
import com.hamoon.uncleted.data.SecurityPreferences

object EventLogger {

    fun log(context: Context, message: String) {
        SecurityPreferences.logEvent(context, message)
    }

    fun getLogs(context: Context): List<String> {
        return SecurityPreferences.getLogs(context)
    }

    fun clearLogs(context: Context) {
        SecurityPreferences.clearLogs(context)
    }
}
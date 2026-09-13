package com.hamoon.uncleted.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hamoon.uncleted.R
import com.hamoon.uncleted.receivers.WidgetActionReceiver

class PanicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_panic_layout)

        // 1. Setup LOCK Button (Added this)
        views.setOnClickPendingIntent(R.id.btn_widget_lock,
            getPendingIntent(context, "ACTION_LOCK"))

        // 2. Setup Siren Button
        views.setOnClickPendingIntent(R.id.btn_widget_siren,
            getPendingIntent(context, "ACTION_SIREN"))

        // 3. Setup Location Button
        views.setOnClickPendingIntent(R.id.btn_widget_location,
            getPendingIntent(context, "ACTION_LOCATION"))

        // 4. Setup Wipe Button
        views.setOnClickPendingIntent(R.id.btn_widget_wipe,
            getPendingIntent(context, "ACTION_WIPE"))

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            this.action = action
        }
        // Unique ID (action.hashCode()) ensures different buttons don't overwrite each other
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
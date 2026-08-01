package io.github.thedayapp.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.thedayapp.WidgetPinActivity

class WidgetPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return
        }

        intent.getStringExtra(WidgetPinActivity.EXTRA_EVENT_ID)
            ?.takeIf(String::isNotBlank)
            ?.let { eventId ->
                DayWidgetPreferences.setEventId(
                    context = context,
                    appWidgetId = appWidgetId,
                    eventId = eventId,
                )
            }

        DayWidgetProvider.requestUpdate(
            context = context,
            appWidgetId = appWidgetId,
        )
    }
}

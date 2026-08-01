package io.github.thedayapp.widget

import android.content.Context

internal object DayWidgetPreferences {
    private const val PREFERENCES_NAME = "day_widget_preferences"
    private const val EVENT_ID_PREFIX = "event_id_"

    fun eventId(
        context: Context,
        appWidgetId: Int,
    ): String? {
        return preferences(context)
            .getString(EVENT_ID_PREFIX + appWidgetId, null)
            ?.takeIf(String::isNotBlank)
    }

    fun setEventId(
        context: Context,
        appWidgetId: Int,
        eventId: String,
    ) {
        preferences(context)
            .edit()
            .putString(EVENT_ID_PREFIX + appWidgetId, eventId)
            .apply()
    }

    fun remove(
        context: Context,
        appWidgetId: Int,
    ) {
        preferences(context)
            .edit()
            .remove(EVENT_ID_PREFIX + appWidgetId)
            .apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
}

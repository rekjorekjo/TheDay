package io.github.thedayapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import io.github.thedayapp.MainActivity
import io.github.thedayapp.R
import io.github.thedayapp.data.DayRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

class MonthCalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            MonthCalendarWidgetPreferences.remove(context, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                refreshAllWidgets(context)
            }

            ACTION_PREVIOUS -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    navigateMonth(context, appWidgetId, -1)
                }
            }

            ACTION_NEXT -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    navigateMonth(context, appWidgetId, +1)
                }
            }
        }
    }

    private fun navigateMonth(
        context: Context,
        appWidgetId: Int,
        delta: Int,
    ) {
        val currentMonth = MonthCalendarWidgetPreferences.loadMonth(
            context,
            appWidgetId,
            YearMonth.now(),
        )

        val newMonth = if (delta < 0) {
            currentMonth.minusMonths(delta.absoluteValue.toLong())
        } else {
            currentMonth.plusMonths(delta.toLong())
        }

        MonthCalendarWidgetPreferences.saveMonth(context, appWidgetId, newMonth)

        val manager = AppWidgetManager.getInstance(context)
        updateWidget(context, manager, appWidgetId)
    }

    private fun refreshAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = android.content.ComponentName(context, MonthCalendarWidgetProvider::class.java)
        val widgetIds = manager.getAppWidgetIds(component)
        widgetIds.forEach { appWidgetId ->
            updateWidget(context, manager, appWidgetId)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "io.github.thedayapp.action.REFRESH_MONTH_WIDGET"
        private const val ACTION_PREVIOUS = "io.github.thedayapp.action.MONTH_WIDGET_PREVIOUS"
        private const val ACTION_NEXT = "io.github.thedayapp.action.MONTH_WIDGET_NEXT"

        private val monthTitleFormatter = DateTimeFormatter.ofPattern("yyyy年M月")

        fun requestUpdate(context: Context) {
            context.sendBroadcast(
                Intent(context, MonthCalendarWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                },
            )
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val repository = DayRepository(context)
            val settings = repository.loadSettings()
            val today = LocalDate.now()

            val month = MonthCalendarWidgetPreferences.loadMonth(
                context,
                appWidgetId,
                YearMonth.from(today),
            )

            val events = repository.loadEvents()

            val options = manager.getAppWidgetOptions(appWidgetId)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceAtLeast(1)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220).coerceAtLeast(1)

            val density = context.resources.displayMetrics.density
            val widthPx = (widthDp * density).roundToInt()
            val heightPx = (heightDp * density).roundToInt()

            val views = RemoteViews(context.packageName, R.layout.month_calendar_widget)

            val eventDates = io.github.thedayapp.domain.CalendarEventDates.datesInMonth(events, month)

            val bitmap = MonthCalendarWidgetRenderer.render(
                context = context,
                widthPx = widthPx,
                heightPx = heightPx,
                month = month,
                today = today,
                eventDates = eventDates,
                settings = settings,
            )

            if (bitmap != null) {
                views.setImageViewBitmap(R.id.month_widget_canvas, bitmap)
                views.setViewVisibility(R.id.month_widget_canvas, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.month_widget_canvas, View.GONE)
            }

            views.setTextViewText(R.id.month_widget_title, month.format(monthTitleFormatter))

            val titleColor = io.github.thedayapp.ui.theme.colorSchemeFor(
                settings.paletteStyle,
                isDarkMode(context, settings.themeMode),
            ).onSurface.toArgb()

            views.setTextColor(R.id.month_widget_previous, titleColor)
            views.setTextColor(R.id.month_widget_title, titleColor)
            views.setTextColor(R.id.month_widget_next, titleColor)

            val previousIntent = Intent(context, MonthCalendarWidgetProvider::class.java).apply {
                action = ACTION_PREVIOUS
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val previousPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 1,
                previousIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.month_widget_previous, previousPendingIntent)

            val nextIntent = Intent(context, MonthCalendarWidgetProvider::class.java).apply {
                action = ACTION_NEXT
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 2,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.month_widget_next, nextPendingIntent)

            val openIntent = Intent(context, MainActivity::class.java)
            val openPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 3,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.month_widget_canvas, openPendingIntent)

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun isDarkMode(
            context: Context,
            themeMode: io.github.thedayapp.data.ThemeMode,
        ): Boolean {
            return when (themeMode) {
                io.github.thedayapp.data.ThemeMode.LIGHT -> false
                io.github.thedayapp.data.ThemeMode.DARK -> true
                io.github.thedayapp.data.ThemeMode.SYSTEM -> {
                    val uiMode = context.resources.configuration.uiMode
                    (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }
        }
    }
}
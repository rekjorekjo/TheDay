package io.github.thedayapp.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import io.github.thedayapp.MainActivity
import io.github.thedayapp.R
import io.github.thedayapp.data.DayRepository
import io.github.thedayapp.data.PaletteStyle
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.domain.EventOrdering
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class DayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
        if (appWidgetIds.isNotEmpty()) {
            scheduleNextMidnight(context)
        } else {
            cancelMidnightRefresh(context)
        }
    }

    override fun onEnabled(context: Context) {
        scheduleNextMidnight(context)
    }

    override fun onDisabled(context: Context) {
        cancelMidnightRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH,
            ACTION_MIDNIGHT,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                if (updateAll(context)) {
                    scheduleNextMidnight(context)
                } else {
                    cancelMidnightRefresh(context)
                }
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH = "io.github.thedayapp.action.REFRESH_WIDGET"
        private const val ACTION_MIDNIGHT = "io.github.thedayapp.action.WIDGET_MIDNIGHT"
        private val widgetDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

        fun requestUpdate(context: Context) {
            context.sendBroadcast(
                Intent(context, DayWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                },
            )
        }

        private fun updateAll(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, DayWidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(component)
            widgetIds.forEach { id -> updateWidget(context, manager, id) }
            return widgetIds.isNotEmpty()
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val repository = DayRepository(context)
            val settings = repository.loadSettings()
            val today = LocalDate.now()
            val visibleEvents = repository.loadEvents().filter {
                settings.showPastEvents || DayMath.signedDays(it, today) >= 0
            }
            val event = EventOrdering.heroEvent(visibleEvents, today)

            val views = RemoteViews(context.packageName, R.layout.the_day_widget)
            views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                backgroundFor(settings.paletteStyle),
            )

            if (event == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                views.setViewVisibility(R.id.widget_relation, View.GONE)
                views.setTextViewText(R.id.widget_count, "—")
                views.setViewVisibility(R.id.widget_count, View.VISIBLE)
                views.setTextViewText(R.id.widget_unit, context.getString(R.string.widget_empty))
                views.setViewVisibility(R.id.widget_unit, View.VISIBLE)
                views.setTextViewText(R.id.widget_date, context.getString(R.string.widget_tap_to_add))
            } else {
                val delta = DayMath.signedDays(event, today)
                views.setTextViewText(R.id.widget_title, event.title)
                if (delta == 0L) {
                    views.setViewVisibility(R.id.widget_relation, View.GONE)
                    views.setTextViewText(R.id.widget_count, context.getString(R.string.widget_today))
                    views.setViewVisibility(R.id.widget_count, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_unit, View.GONE)
                } else {
                    views.setTextViewText(
                        R.id.widget_relation,
                        if (delta > 0) {
                            context.getString(R.string.widget_relation_countdown)
                        } else {
                            context.getString(R.string.widget_relation_countup)
                        },
                    )
                    views.setViewVisibility(R.id.widget_relation, View.VISIBLE)
                    views.setTextViewText(R.id.widget_count, abs(delta).toString())
                    views.setViewVisibility(R.id.widget_count, View.VISIBLE)
                    views.setTextViewText(R.id.widget_unit, context.getString(R.string.widget_unit_days))
                    views.setViewVisibility(R.id.widget_unit, View.VISIBLE)
                }
                views.setTextViewText(
                    R.id.widget_date,
                    DayMath.effectiveDate(event, today).format(widgetDateFormatter),
                )
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                event?.let { putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, it.id) }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun backgroundFor(palette: PaletteStyle): Int = when (palette) {
            PaletteStyle.MIDNIGHT -> R.drawable.widget_bg_midnight
            PaletteStyle.CINNABAR -> R.drawable.widget_bg_cinnabar
            PaletteStyle.PINE -> R.drawable.widget_bg_pine
            PaletteStyle.ANTIQUE_GOLD -> R.drawable.widget_bg_antique_gold
            PaletteStyle.CATPPUCCIN -> R.drawable.widget_bg_catppuccin
            PaletteStyle.ROSE_PINE -> R.drawable.widget_bg_rose_pine
            PaletteStyle.NORD -> R.drawable.widget_bg_nord
            PaletteStyle.SOLARIZED -> R.drawable.widget_bg_solarized
            PaletteStyle.GRUVBOX -> R.drawable.widget_bg_gruvbox
            PaletteStyle.DRACULA -> R.drawable.widget_bg_dracula
        }

        private fun scheduleNextMidnight(context: Context) {
            val next = LocalDate.now()
                .plusDays(1)
                .atTime(0, 1)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                next,
                midnightPendingIntent(context),
            )
        }

        private fun cancelMidnightRefresh(context: Context) {
            context.getSystemService(AlarmManager::class.java)
                .cancel(midnightPendingIntent(context))
        }

        private fun midnightPendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                9191,
                Intent(context, DayWidgetProvider::class.java).apply {
                    action = ACTION_MIDNIGHT
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
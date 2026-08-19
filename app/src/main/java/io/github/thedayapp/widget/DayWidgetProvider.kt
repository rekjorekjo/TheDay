package io.github.thedayapp.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.graphics.Bitmap
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
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


    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            DayWidgetPreferences.remove(
                context = context,
                appWidgetId = appWidgetId,
            )
        }
        super.onDeleted(context, appWidgetIds)
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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(
            context,
            appWidgetManager,
            appWidgetId,
            newOptions,
        )

        updateWidget(
            context,
            appWidgetManager,
            appWidgetId,
        )
    }

    companion object {
        private const val ACTION_REFRESH = "io.github.thedayapp.action.REFRESH_WIDGET"
        private const val ACTION_MIDNIGHT = "io.github.thedayapp.action.WIDGET_MIDNIGHT"
        private val widgetDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

        private data class WidgetSize(
            val widthDp: Int,
            val heightDp: Int,
        )

        private fun widgetSize(
            manager: AppWidgetManager,
            appWidgetId: Int,
        ): WidgetSize {
            val options =
                manager.getAppWidgetOptions(
                    appWidgetId,
                )

            return WidgetSize(
                widthDp =
                    options.getInt(
                        AppWidgetManager
                            .OPTION_APPWIDGET_MIN_WIDTH,
                        180,
                    ).coerceAtLeast(1),
                heightDp =
                    options.getInt(
                        AppWidgetManager
                            .OPTION_APPWIDGET_MIN_HEIGHT,
                        90,
                    ).coerceAtLeast(1),
            )
        }

        fun requestUpdate(context: Context) {
            context.sendBroadcast(
                Intent(context, DayWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                },
            )
        }


        fun requestUpdate(
            context: Context,
            appWidgetId: Int,
        ) {
            updateWidget(
                context = context,
                manager = AppWidgetManager.getInstance(context),
                appWidgetId = appWidgetId,
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
            val size = widgetSize(manager, appWidgetId)
            val portrait = size.heightDp >= size.widthDp * 1.15f
            val compact = size.heightDp < 88 || (!portrait && size.widthDp < 170)
            val large = !portrait && size.widthDp >= 300 && size.heightDp >= 150

            val repository = DayRepository(context)
            val settings = repository.loadSettings()
            val today = LocalDate.now()
            val allEvents = repository.loadEvents()
            val assignedEventId = DayWidgetPreferences.eventId(
                context = context,
                appWidgetId = appWidgetId,
            )
            val assignedEvent = assignedEventId?.let { eventId ->
                allEvents.firstOrNull { event ->
                    event.id == eventId
                }
            }
            if (assignedEventId != null && assignedEvent == null) {
                DayWidgetPreferences.remove(
                    context = context,
                    appWidgetId = appWidgetId,
                )
            }
            val visibleEvents = allEvents
            val event = assignedEvent
                ?: EventOrdering.heroEvent(visibleEvents, today)

            val views = RemoteViews(
                context.packageName,
                if (portrait) {
                    R.layout.the_day_widget_portrait
                } else {
                    R.layout.the_day_widget
                },
            )
            var countGlowBitmap: Bitmap? = null
            views.setViewVisibility(R.id.widget_count_glow, View.GONE)
            views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                backgroundFor(settings.paletteStyle),
            )

            val widgetBitmap = event?.backgroundImage?.let { image ->
                WidgetImageRenderer.render(
                    context = context,
                    image = image,
                    widthDp = size.widthDp,
                    heightDp = size.heightDp,
                )
            }

            if (widgetBitmap != null) {
                views.setImageViewBitmap(R.id.widget_background_image, widgetBitmap)
                views.setViewVisibility(R.id.widget_background_image, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_background_image, View.GONE)
            }

            if (event == null) {
                views.setViewVisibility(R.id.widget_count_glow, View.GONE)
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                views.setViewVisibility(R.id.widget_relation, View.GONE)
                if (compact) {
                    views.setTextViewText(R.id.widget_count, context.getString(R.string.widget_empty))
                    views.setViewVisibility(R.id.widget_count, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_unit, View.GONE)
                    views.setViewVisibility(R.id.widget_date, View.GONE)
                } else {
                    views.setTextViewText(R.id.widget_count, "—")
                    views.setViewVisibility(R.id.widget_count, View.VISIBLE)
                    views.setTextViewText(R.id.widget_unit, context.getString(R.string.widget_empty))
                    views.setViewVisibility(R.id.widget_unit, View.VISIBLE)
                    views.setTextViewText(R.id.widget_date, context.getString(R.string.widget_tap_to_add))
                    views.setViewVisibility(R.id.widget_date, View.VISIBLE)
                }
            } else {
                val delta = DayMath.signedDays(event, today)
                if (delta == 0L) {
                    views.setViewVisibility(R.id.widget_count_glow, View.GONE)
                    views.setTextViewText(R.id.widget_title, event.title)
                    views.setViewVisibility(R.id.widget_relation, View.GONE)
                    views.setTextViewText(R.id.widget_count, context.getString(R.string.widget_today))
                    views.setViewVisibility(R.id.widget_count, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_unit, View.GONE)
                } else {
                    val relationText = if (delta > 0) {
                        context.getString(R.string.widget_relation_countdown)
                    } else {
                        context.getString(R.string.widget_relation_countup)
                    }
                    views.setTextViewText(R.id.widget_title, "${event.title} $relationText")
                    views.setViewVisibility(R.id.widget_relation, View.GONE)

                    val countText = if (compact) {
                        "${abs(delta)} ${context.getString(R.string.widget_unit_days)}"
                    } else {
                        abs(delta).toString()
                    }
                    val countTextSizeSp = when {
                        portrait && size.widthDp < 170 -> 40f
                        portrait -> 48f
                        compact -> 28f
                        large -> 56f
                        else -> 46f
                    }
                    val horizontalPaddingDp = when {
                        portrait -> 18
                        compact -> 12
                        large -> 24
                        else -> 20
                    }
                    val countMaxWidthDp = (
                        size.widthDp - horizontalPaddingDp * 2 -
                            (if (compact) 0 else 42)
                    ).coerceAtLeast(56)

                    countGlowBitmap = WidgetCountGlowRenderer.render(
                        context = context,
                        text = countText,
                        textSizeSp = countTextSizeSp,
                        maxWidthDp = countMaxWidthDp,
                    )

                    if (countGlowBitmap != null) {
                        views.setImageViewBitmap(
                            R.id.widget_count_glow,
                            countGlowBitmap,
                        )
                        views.setContentDescription(
                            R.id.widget_count_glow,
                            countText,
                        )
                        views.setViewVisibility(R.id.widget_count_glow, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_count, View.GONE)
                    } else {
                        views.setTextViewText(R.id.widget_count, countText)
                        views.setViewVisibility(R.id.widget_count, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_count_glow, View.GONE)
                    }

                    if (compact) {
                        views.setViewVisibility(R.id.widget_unit, View.GONE)
                    } else {
                        views.setTextViewText(
                            R.id.widget_unit,
                            context.getString(R.string.widget_unit_days),
                        )
                        views.setViewVisibility(R.id.widget_unit, View.VISIBLE)
                    }
                }
                if (compact) {
                    views.setViewVisibility(R.id.widget_date, View.GONE)
                } else {
                    views.setTextViewText(
                        R.id.widget_date,
                        DayMath.effectiveDate(event, today).format(widgetDateFormatter),
                    )
                    views.setViewVisibility(R.id.widget_date, View.VISIBLE)
                }
            }

            applyResponsiveLayout(
                context = context,
                views = views,
                size = size,
                compact = compact,
                large = large,
                portrait = portrait,
            )

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
            countGlowBitmap?.recycle()
        }

        private fun applyResponsiveLayout(
            context: Context,
            views: RemoteViews,
            size: WidgetSize,
            compact: Boolean,
            large: Boolean,
            portrait: Boolean,
        ) {
            val density = context.resources.displayMetrics.density

            if (portrait) {
                val countSize = if (size.widthDp < 170) 40f else 48f
                views.setViewPadding(
                    R.id.widget_content,
                    dpToPx(18, density),
                    dpToPx(18, density),
                    dpToPx(18, density),
                    dpToPx(18, density),
                )
                views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, 16f)
                views.setTextViewTextSize(R.id.widget_count, TypedValue.COMPLEX_UNIT_SP, countSize)
                views.setTextViewTextSize(R.id.widget_relation, TypedValue.COMPLEX_UNIT_SP, 13f)
                views.setTextViewTextSize(R.id.widget_unit, TypedValue.COMPLEX_UNIT_SP, 14f)
                views.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_SP, 13f)
                views.setViewVisibility(R.id.widget_date, View.VISIBLE)
                views.setViewVisibility(
                    R.id.widget_accent,
                    if (size.widthDp < 145) View.GONE else View.VISIBLE,
                )
                views.setInt(R.id.widget_title, "setMaxLines", 2)
            } else if (compact) {
                views.setViewPadding(R.id.widget_content, dpToPx(12, density), dpToPx(9, density), dpToPx(12, density), dpToPx(9, density))
                views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, 14f)
                views.setTextViewTextSize(R.id.widget_count, TypedValue.COMPLEX_UNIT_SP, 28f)
                views.setViewVisibility(R.id.widget_date, View.GONE)
                views.setInt(R.id.widget_title, "setMaxLines", 1)
                if (size.widthDp < 145) {
                    views.setViewVisibility(R.id.widget_accent, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_accent, View.VISIBLE)
                }
            } else if (large) {
                views.setViewPadding(R.id.widget_content, dpToPx(24, density), dpToPx(20, density), dpToPx(24, density), dpToPx(20, density))
                views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, 19f)
                views.setTextViewTextSize(R.id.widget_count, TypedValue.COMPLEX_UNIT_SP, 56f)
                views.setTextViewTextSize(R.id.widget_relation, TypedValue.COMPLEX_UNIT_SP, 14f)
                views.setTextViewTextSize(R.id.widget_unit, TypedValue.COMPLEX_UNIT_SP, 15f)
                views.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_SP, 14f)
                views.setViewVisibility(R.id.widget_date, View.VISIBLE)
                views.setViewVisibility(R.id.widget_accent, View.VISIBLE)
                views.setInt(R.id.widget_title, "setMaxLines", 2)
            } else {
                views.setViewPadding(R.id.widget_content, dpToPx(20, density), dpToPx(17, density), dpToPx(20, density), dpToPx(17, density))
                views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, 17f)
                views.setTextViewTextSize(R.id.widget_count, TypedValue.COMPLEX_UNIT_SP, 46f)
                views.setTextViewTextSize(R.id.widget_relation, TypedValue.COMPLEX_UNIT_SP, 13f)
                views.setTextViewTextSize(R.id.widget_unit, TypedValue.COMPLEX_UNIT_SP, 14f)
                views.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_SP, 13f)
                views.setViewVisibility(R.id.widget_date, View.VISIBLE)
                views.setViewVisibility(R.id.widget_accent, View.VISIBLE)
                views.setInt(R.id.widget_title, "setMaxLines", 1)
            }
        }

        private fun dpToPx(dp: Int, density: Float): Int = (dp * density).toInt()

        private fun backgroundFor(palette: PaletteStyle): Int = when (palette) {
            PaletteStyle.MIDNIGHT -> R.drawable.widget_bg_midnight
            PaletteStyle.CINNABAR -> R.drawable.widget_bg_cinnabar
            PaletteStyle.PINE -> R.drawable.widget_bg_pine
            PaletteStyle.ANTIQUE_GOLD -> R.drawable.widget_bg_antique_gold
            PaletteStyle.BLOOM_PETAL -> R.drawable.widget_bg_bloom_petal
            PaletteStyle.BLOOM_MIST -> R.drawable.widget_bg_bloom_mist
            PaletteStyle.BLOOM_VERDANT -> R.drawable.widget_bg_bloom_verdant
            PaletteStyle.BLOOM_STONE -> R.drawable.widget_bg_bloom_stone
            PaletteStyle.BLOOM_WHEAT -> R.drawable.widget_bg_bloom_wheat
            PaletteStyle.BLOOM_INK -> R.drawable.widget_bg_bloom_ink
            PaletteStyle.BLOOM_AMBER -> R.drawable.widget_bg_bloom_amber
            PaletteStyle.BLOOM_LAPIS -> R.drawable.widget_bg_bloom_lapis
            PaletteStyle.BLOOM_RIPPLE -> R.drawable.widget_bg_bloom_ripple
            PaletteStyle.BLOOM_CINNABAR -> R.drawable.widget_bg_bloom_cinnabar
            PaletteStyle.BLOOM_SAGE -> R.drawable.widget_bg_bloom_sage
            PaletteStyle.BLOOM_SPRING -> R.drawable.widget_bg_bloom_spring
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

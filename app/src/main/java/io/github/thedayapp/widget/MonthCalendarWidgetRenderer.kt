package io.github.thedayapp.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import io.github.thedayapp.data.AppSettings
import io.github.thedayapp.data.ThemeMode
import io.github.thedayapp.ui.theme.colorSchemeFor
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object MonthCalendarWidgetRenderer {

    private const val TAG = "MonthCalendarRenderer"

    private const val MAX_LONG_EDGE = 720
    private const val MAX_PIXELS = 180_000

    fun render(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        month: YearMonth,
        today: LocalDate,
        eventDates: Set<LocalDate>,
        settings: AppSettings,
    ): Bitmap? {
        if (widthPx <= 0 || heightPx <= 0) return null

        val deviceDensity = context.resources.displayMetrics.density

        var targetWidth = widthPx
        var targetHeight = heightPx

        val longEdge = maxOf(targetWidth, targetHeight)
        if (longEdge > MAX_LONG_EDGE) {
            val scale = MAX_LONG_EDGE.toFloat() / longEdge.toFloat()
            targetWidth = (targetWidth * scale).roundToInt().coerceAtLeast(1)
            targetHeight = (targetHeight * scale).roundToInt().coerceAtLeast(1)
        }

        val pixelCount = targetWidth.toLong() * targetHeight.toLong()
        if (pixelCount > MAX_PIXELS) {
            val scale = sqrt(MAX_PIXELS.toFloat() / pixelCount.toFloat())
            targetWidth = (targetWidth * scale).roundToInt().coerceAtLeast(1)
            targetHeight = (targetHeight * scale).roundToInt().coerceAtLeast(1)
        }

        val renderScale = minOf(
            targetWidth.toFloat() / widthPx.toFloat(),
            targetHeight.toFloat() / heightPx.toFloat(),
        )

        val effectiveDensity = deviceDensity * renderScale

        return createBitmap(
            context = context,
            width = targetWidth,
            height = targetHeight,
            density = effectiveDensity,
            month = month,
            today = today,
            eventDates = eventDates,
            settings = settings,
        )
    }

    private fun createBitmap(
        context: Context,
        width: Int,
        height: Int,
        density: Float,
        month: YearMonth,
        today: LocalDate,
        eventDates: Set<LocalDate>,
        settings: AppSettings,
    ): Bitmap? {
        val dark = isDarkMode(context, settings.themeMode)
        val colorScheme = colorSchemeFor(settings.paletteStyle, dark)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        try {
            drawMonthCalendar(
                canvas = canvas,
                width = width,
                height = height,
                density = density,
                month = month,
                today = today,
                eventDates = eventDates,
                surface = colorScheme.surface.toArgb(),
                onSurface = colorScheme.onSurface.toArgb(),
                onSurfaceVariant = colorScheme.onSurfaceVariant.toArgb(),
                primary = colorScheme.primary.toArgb(),
                onPrimary = colorScheme.onPrimary.toArgb(),
                primaryContainer = colorScheme.primaryContainer.toArgb(),
                onPrimaryContainer = colorScheme.onPrimaryContainer.toArgb(),
                borderColor = colorScheme.outlineVariant
                    .copy(alpha = 0.55f)
                    .toArgb(),
            )
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to render month calendar", exception)
            bitmap.recycle()
            return null
        }

        return bitmap
    }

    private fun isDarkMode(context: Context, themeMode: ThemeMode): Boolean {
        return when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> {
                val uiMode = context.resources.configuration.uiMode
                (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun drawMonthCalendar(
        canvas: Canvas,
        width: Int,
        height: Int,
        density: Float,
        month: YearMonth,
        today: LocalDate,
        eventDates: Set<LocalDate>,
        surface: Int,
        onSurface: Int,
        onSurfaceVariant: Int,
        primary: Int,
        onPrimary: Int,
        primaryContainer: Int,
        onPrimaryContainer: Int,
        borderColor: Int,
    ) {
        val padding = (10 * density).roundToInt()
        val cornerRadius = 22f * density

        val contentRect = RectF(
            padding.toFloat(),
            padding.toFloat(),
            (width - padding).toFloat(),
            (height - padding).toFloat(),
        )

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = surface
        }
        canvas.drawRoundRect(contentRect, cornerRadius, cornerRadius, backgroundPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = 0.75f * density
        }
        canvas.drawRoundRect(contentRect, cornerRadius, cornerRadius, borderPaint)

        val headerHeight = (50 * density).roundToInt()

        val gridLeft = contentRect.left + padding
        val gridRight = contentRect.right - padding
        val gridTop = contentRect.top + headerHeight
        val gridBottom = contentRect.bottom - padding

        val gridWidth = gridRight - gridLeft
        val gridHeight = gridBottom - gridTop

        val cellHeight = gridHeight / 7f
        val cellWidth = gridWidth / 7f

        val weekdayTextSize = (12 * density).roundToInt().toFloat()
        val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurfaceVariant
            textSize = weekdayTextSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
        for ((index, weekday) in weekdays.withIndex()) {
            val x = gridLeft + cellWidth * (index + 0.5f)
            val y = gridTop + cellHeight * 0.5f - (weekdayPaint.descent() + weekdayPaint.ascent()) / 2f
            canvas.drawText(weekday, x, y, weekdayPaint)
        }

        val cellMinDimension = min(cellWidth, cellHeight)
        val dayTextSize = (cellMinDimension * 0.4f).coerceIn(
            (11 * density).roundToInt().toFloat(),
            (16 * density).roundToInt().toFloat(),
        )
        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dayTextSize
            textAlign = Paint.Align.CENTER
        }

        val firstDayOfWeek = month.atDay(1).dayOfWeek.value

        for (week in 0..5) {
            for (day in 1..7) {
                val position = week * 7 + day
                val dayOfMonth = position - firstDayOfWeek + 1

                if (dayOfMonth < 1 || dayOfMonth > month.lengthOfMonth()) continue

                val date = month.atDay(dayOfMonth)
                val isToday = date == today
                val hasEvent = date in eventDates

                val cellLeft = gridLeft + cellWidth * (day - 1)
                val cellTop = gridTop + cellHeight * (week + 1)
                val cellRight = cellLeft + cellWidth
                val cellBottom = cellTop + cellHeight

                val centerX = (cellLeft + cellRight) / 2
                val centerY = (cellTop + cellBottom) / 2

                val circleRadius = minOf(cellWidth, cellHeight) * 0.4f

                when {
                    isToday && hasEvent -> {
                        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = primary
                        }
                        canvas.drawCircle(centerX, centerY, circleRadius, bgPaint)

                        dayPaint.color = onPrimary
                        dayPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }

                    isToday -> {
                        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = primary
                            style = Paint.Style.STROKE
                            strokeWidth = 1.5f * density
                        }
                        canvas.drawCircle(centerX, centerY, circleRadius, borderPaint)

                        dayPaint.color = primary
                        dayPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }

                    hasEvent -> {
                        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = primaryContainer
                        }
                        canvas.drawCircle(centerX, centerY, circleRadius, bgPaint)

                        dayPaint.color = onPrimaryContainer
                        dayPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    }

                    else -> {
                        dayPaint.color = onSurface
                        dayPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    }
                }

                val textY = centerY - (dayPaint.descent() + dayPaint.ascent()) / 2
                canvas.drawText(date.dayOfMonth.toString(), centerX, textY, dayPaint)
            }
        }
    }
}

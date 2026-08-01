package io.github.thedayapp.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import kotlin.math.ceil
import kotlin.math.max

/**
 * Renders the widget count as a bitmap with a visible but static halo.
 * RemoteViews cannot run Compose animations reliably, so the glow is baked
 * into the bitmap while the glyph fill keeps the normal widget text color.
 */
internal object WidgetCountGlowRenderer {
    fun render(
        context: Context,
        text: String,
        textSizeSp: Float,
        maxWidthDp: Int,
    ): Bitmap? {
        if (text.isBlank()) return null

        val metrics = context.resources.displayMetrics
        val maxWidthPx = max(
            1f,
            maxWidthDp * metrics.density,
        )
        val minimumTextSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            20f,
            metrics,
        )

        val paint = Paint(
            Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG,
        ).apply {
            typeface = Typeface.create(
                "sans-serif-light",
                Typeface.NORMAL,
            )
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                metrics,
            )
        }

        fun renderedWidth(): Float {
            val glowRadius = max(10f, paint.textSize * 0.30f)
            return paint.measureText(text) +
                (glowRadius + paint.textSize * 0.04f) * 2f
        }

        while (
            paint.textSize > minimumTextSizePx &&
            renderedWidth() > maxWidthPx
        ) {
            paint.textSize = max(
                minimumTextSizePx,
                paint.textSize - metrics.scaledDensity,
            )
        }

        val outerGlowRadius = max(10f, paint.textSize * 0.30f)
        val innerGlowRadius = max(4f, paint.textSize * 0.12f)
        val horizontalPadding = outerGlowRadius + paint.textSize * 0.04f
        val verticalPadding = outerGlowRadius + paint.textSize * 0.05f
        val fontMetrics = paint.fontMetrics
        val width = ceil(
            paint.measureText(text) + horizontalPadding * 2f,
        ).toInt().coerceAtLeast(1)
        val height = ceil(
            fontMetrics.descent - fontMetrics.ascent +
                verticalPadding * 2f,
        ).toInt().coerceAtLeast(1)

        return runCatching {
            Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888,
            ).also { bitmap ->
                val canvas = Canvas(bitmap)
                val baseline = verticalPadding - fontMetrics.ascent

                val outerGlowColor = Color.rgb(255, 185, 45)
                val innerGlowColor = Color.rgb(255, 199, 87)

                paint.color = Color.argb(24, 255, 193, 70)
                paint.setShadowLayer(
                    outerGlowRadius,
                    0f,
                    0f,
                    Color.argb(
                        205,
                        Color.red(outerGlowColor),
                        Color.green(outerGlowColor),
                        Color.blue(outerGlowColor),
                    ),
                )
                canvas.drawText(
                    text,
                    horizontalPadding,
                    baseline,
                    paint,
                )

                paint.color = Color.rgb(255, 248, 234)
                paint.setShadowLayer(
                    innerGlowRadius,
                    0f,
                    0f,
                    Color.argb(
                        180,
                        Color.red(innerGlowColor),
                        Color.green(innerGlowColor),
                        Color.blue(innerGlowColor),
                    ),
                )
                canvas.drawText(
                    text,
                    horizontalPadding,
                    baseline,
                    paint,
                )
                paint.clearShadowLayer()
            }
        }.getOrNull()
    }
}

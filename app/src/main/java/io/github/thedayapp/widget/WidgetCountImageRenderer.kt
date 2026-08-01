package io.github.thedayapp.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import kotlin.math.ceil
import kotlin.math.max

/**
 * Produces a small static glossy count bitmap for RemoteViews. Android home
 * screen widgets cannot run a reliable continuous Compose-style animation, so
 * the widget uses a static metallic-gold highlight and glow while the in-app
 * hero card uses an animated sweep.
 */
internal object WidgetCountImageRenderer {
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

        while (
            paint.textSize > minimumTextSizePx &&
            paint.measureText(text) +
                max(16f, paint.textSize * 0.40f) > maxWidthPx
        ) {
            paint.textSize = max(
                minimumTextSizePx,
                paint.textSize - metrics.scaledDensity,
            )
        }

        val horizontalPadding = max(
            8f,
            paint.textSize * 0.20f,
        )
        val verticalPadding = max(
            8f,
            paint.textSize * 0.20f,
        )
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
                val baseline =
                    verticalPadding - fontMetrics.ascent

                val glowPaint = Paint(paint).apply {
                    shader = null
                    color = Color.rgb(255, 185, 48)
                    alpha = 190
                    setShadowLayer(
                        paint.textSize * 0.15f,
                        0f,
                        0f,
                        Color.argb(220, 255, 174, 38),
                    )
                }
                canvas.drawText(
                    text,
                    horizontalPadding,
                    baseline,
                    glowPaint,
                )

                paint.clearShadowLayer()
                paint.shader = LinearGradient(
                    0f,
                    height.toFloat(),
                    width.toFloat(),
                    0f,
                    intArrayOf(
                        Color.rgb(126, 76, 4),
                        Color.rgb(214, 145, 20),
                        Color.rgb(255, 211, 83),
                        Color.rgb(255, 250, 216),
                        Color.WHITE,
                        Color.rgb(255, 224, 116),
                        Color.rgb(203, 126, 11),
                        Color.rgb(126, 76, 4),
                    ),
                    floatArrayOf(
                        0f,
                        0.18f,
                        0.36f,
                        0.47f,
                        0.52f,
                        0.61f,
                        0.82f,
                        1f,
                    ),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawText(
                    text,
                    horizontalPadding,
                    baseline,
                    paint,
                )

                val gleamPaint = Paint(paint).apply {
                    shader = null
                    color = Color.WHITE
                    alpha = 135
                    strokeWidth = max(1f, paint.textSize * 0.018f)
                    style = Paint.Style.STROKE
                }
                canvas.drawText(
                    text,
                    horizontalPadding,
                    baseline - paint.textSize * 0.012f,
                    gleamPaint,
                )
            }
        }.getOrNull()

    }
}

package io.github.thedayapp.sharing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Draws the same Glass ambience used by Flutter into static exported bitmaps. */
object GlassExportBackdrop {
    fun draw(canvas: Canvas, rect: RectF, style: GlassExportStyle) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        drawBase(canvas, rect, style)
        drawOrbitingLights(
            canvas = canvas,
            rect = rect,
            style = style,
            interior = style.backgroundMode == "AURORA",
        )
        drawTexture(canvas, rect, style)
    }

    private fun drawBase(canvas: Canvas, rect: RectF, style: GlassExportStyle) {
        val tide = style.backgroundTexture == "WAVE"
        val top = if (tide) {
            if (style.isDark) Color.rgb(8, 19, 33) else Color.rgb(215, 235, 245)
        } else {
            if (style.isDark) Color.rgb(18, 26, 45) else Color.rgb(234, 242, 248)
        }
        val bottom = if (tide) {
            if (style.isDark) Color.rgb(4, 10, 18) else Color.rgb(201, 215, 217)
        } else {
            if (style.isDark) Color.rgb(11, 17, 30) else Color.rgb(220, 231, 238)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                top,
                bottom,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(rect, paint)
    }

    private fun drawOrbitingLights(
        canvas: Canvas,
        rect: RectF,
        style: GlassExportStyle,
        interior: Boolean = false,
    ) {
        val minSide = min(rect.width(), rect.height())
        val baseOrbSize = minSide * 1.28f
        val baseRadiusX = if (interior) {
            max(56f, rect.width() * 0.30f)
        } else {
            (rect.width() / 2f) + (baseOrbSize * 0.22f)
        }
        val baseRadiusY = if (interior) {
            max(120f, rect.height() * 0.30f)
        } else {
            (rect.height() / 2f) + (baseOrbSize * 0.12f)
        }
        val phase = ((style.backgroundPhase % 1f) + 1f) % 1f
        val baseAngle = phase * (PI * 2.0)

        fun drawAt(
            orbScale: Float,
            radiusXScale: Float,
            radiusYScale: Float,
            angle: Double,
            color: Int,
            alpha: Int,
            shapeX: Float,
            shapeY: Float,
            shapePhase: Double,
            rotationBase: Float,
        ) {
            val orbSize = baseOrbSize * orbScale
            val cx = rect.centerX() + (cos(angle) * baseRadiusX * radiusXScale).toFloat()
            val cy = rect.centerY() + (sin(angle) * baseRadiusY * radiusYScale).toFloat()
            val pulseX = if (interior) {
                1f + (sin((angle * 2.0) + shapePhase) * 0.075).toFloat()
            } else {
                1f
            }
            val pulseY = if (interior) {
                1f + (cos((angle * 3.0) + shapePhase) * 0.065).toFloat()
            } else {
                1f
            }
            val rotation = if (interior) {
                rotationBase + (sin(angle + shapePhase) * 0.18).toFloat()
            } else {
                0f
            }
            drawOrb(
                canvas = canvas,
                cx = cx,
                cy = cy,
                radius = orbSize / 2f,
                color = color,
                alpha = alpha,
                scaleX = if (interior) shapeX * pulseX else 1f,
                scaleY = if (interior) shapeY * pulseY else 1f,
                rotation = rotation,
            )
        }

        drawAt(
            orbScale = 1f,
            radiusXScale = 1f,
            radiusYScale = 1f,
            angle = baseAngle + 0.10,
            color = style.primary,
            alpha = if (style.isDark) 128 else 84,
            shapeX = 1.30f,
            shapeY = 0.82f,
            shapePhase = 0.35,
            rotationBase = -0.30f,
        )
        drawAt(
            orbScale = 0.98f,
            radiusXScale = 1.02f,
            radiusYScale = 0.99f,
            angle = baseAngle + ((PI * 2.0) / 3.0) + 0.18,
            color = style.secondary,
            alpha = if (style.isDark) 110 else 74,
            shapeX = 0.84f,
            shapeY = 1.26f,
            shapePhase = 2.10,
            rotationBase = 0.42f,
        )
        drawAt(
            orbScale = 1.07f,
            radiusXScale = 0.98f,
            radiusYScale = 1.02f,
            angle = baseAngle + ((PI * 4.0) / 3.0) - 0.10,
            color = style.tertiary,
            alpha = if (style.isDark) 118 else 69,
            shapeX = 1.38f,
            shapeY = 0.74f,
            shapePhase = 4.30,
            rotationBase = -0.62f,
        )
    }

    private fun drawOrb(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        alpha: Int,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        rotation: Float = 0f,
    ) {
        val safeRadius = max(1f, radius)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx,
                cy,
                safeRadius,
                intArrayOf(
                    withAlpha(color, alpha),
                    withAlpha(color, (alpha * 0.42f).toInt()),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.46f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        val saveCount = canvas.save()
        canvas.rotate(Math.toDegrees(rotation.toDouble()).toFloat(), cx, cy)
        canvas.scale(scaleX, scaleY, cx, cy)
        canvas.drawCircle(cx, cy, safeRadius, paint)
        canvas.restoreToCount(saveCount)
    }


    private fun drawTexture(canvas: Canvas, rect: RectF, style: GlassExportStyle) {
        when (style.backgroundTexture) {
            "NONE" -> Unit
            "WAVE" -> {
                drawSnowBase(canvas, rect, style)
                drawSnowMotion(canvas, rect, style)
            }
            "STARS" -> {
                drawMeteorField(canvas, rect, style)
                drawTransientMeteors(canvas, rect, style)
            }
            "CONSTELLATION" -> drawStars(canvas, rect, style, constellation = true)
            "HEART" -> drawHearts(canvas, rect, style)
            else -> drawDiagonal(canvas, rect, style)
        }
    }

    private fun textureColor(style: GlassExportStyle, alpha: Int): Int =
        if (style.isDark) Color.argb(alpha, 255, 255, 255) else Color.argb(alpha, 0, 0, 0)

    private fun drawDiagonal(canvas: Canvas, rect: RectF, style: GlassExportStyle) {
        val random = Random(0x4A21)
        val phase = ((style.backgroundPhase % 1f) + 1f) % 1f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        repeat(52) {
            val baseX = rect.left + random.nextFloat() * (rect.width() + 40f) - 20f
            val baseY = rect.top + random.nextFloat() * (rect.height() + 36f) - 18f
            val speed = 0.65f + random.nextFloat() * 0.95f
            val y = rect.top + (((baseY - rect.top) + (rect.height() * phase * speed)) % (rect.height() + 44f)) - 22f
            val length = min(rect.width(), rect.height()) * (0.045f + random.nextFloat() * 0.028f)
            val dx = length * (0.16f + random.nextFloat() * 0.07f)
            val dy = length * (0.84f + random.nextFloat() * 0.08f)
            paint.strokeWidth = 0.9f + random.nextFloat() * 0.55f
            paint.color = textureColor(style, if (style.isDark) 34 else 22)
            canvas.drawLine(baseX, y, baseX - dx, y + dy, paint)
        }

        val mistCenterX = rect.left + rect.width() * 0.22f
        val mistCenterY = rect.top + rect.height() * 0.18f
        canvas.drawOval(
            RectF(
                mistCenterX - rect.width() * 0.18f,
                mistCenterY - rect.height() * 0.08f,
                mistCenterX + rect.width() * 0.18f,
                mistCenterY + rect.height() * 0.08f,
            ),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    mistCenterX,
                    mistCenterY,
                    rect.width() * 0.20f,
                    intArrayOf(
                        withAlpha(Color.rgb(191, 217, 244), if (style.isDark) 11 else 8),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
    }

    private fun drawSnowBase(canvas: Canvas, rect: RectF, style: GlassExportStyle) {
        canvas.drawRect(
            RectF(rect.left, rect.top + rect.height() * 0.70f, rect.right, rect.bottom),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    rect.left,
                    rect.top + rect.height() * 0.70f,
                    rect.left,
                    rect.bottom,
                    intArrayOf(
                        Color.TRANSPARENT,
                        withAlpha(Color.rgb(234, 243, 255), if (style.isDark) 15 else 10),
                        withAlpha(Color.rgb(246, 250, 255), if (style.isDark) 31 else 20),
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )

    }

    private fun drawSnowMotion(canvas: Canvas, rect: RectF, style: GlassExportStyle) {
        val phase = ((style.backgroundPhase % 1f) + 1f) % 1f
        val random = Random(0x5F21)
        repeat(70) { index ->
            val baseX = rect.left + random.nextFloat() * rect.width()
            val baseY = rect.top + random.nextFloat() * rect.height()
            val radius = 0.9f + random.nextFloat() * 2.4f
            val speed = 0.40f + random.nextFloat() * 0.85f
            val drift = (sin((phase * (PI * 2.0)) + (index * 0.41)) * (2.5 + random.nextFloat() * 4.0)).toFloat()
            val y = rect.top + (((baseY - rect.top) + (rect.height() * 0.46f * phase * speed)) % (rect.height() + 26f)) - 13f
            val x = baseX + drift
            val alpha = (((if (style.isDark) 0.11f else 0.08f) + (radius * 0.018f)) * 255f).toInt().coerceIn(1, 255)
            canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(Color.rgb(247, 251, 255), alpha)
            })
        }

        val nearRandom = Random(0x7319)
        repeat(18) { index ->
            val baseX = rect.left + nearRandom.nextFloat() * rect.width()
            val baseY = rect.top + nearRandom.nextFloat() * rect.height()
            val radius = 2.0f + nearRandom.nextFloat() * 3.6f
            val speed = 0.28f + nearRandom.nextFloat() * 0.42f
            val sway = (cos((phase * (PI * 2.0)) + (index * 0.53)) * (4.0 + nearRandom.nextFloat() * 6.0)).toFloat()
            val y = rect.top + (((baseY - rect.top) + (rect.height() * 0.34f * phase * speed)) % (rect.height() + 32f)) - 16f
            val x = baseX + sway
            canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(Color.WHITE, if (style.isDark) 26 else 15)
            })
        }
    }

    private fun drawMeteorField(canvas: Canvas, rect: RectF, style: GlassExportStyle) {
        val random = Random(0x5A31)
        repeat(24) {
            val x = rect.left + random.nextFloat() * rect.width()
            val y = rect.top + random.nextFloat() * rect.height() * 0.84f
            val radius = 0.6f + random.nextFloat() * 1.1f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textureColor(style, if (style.isDark) 31 else 20)
            }
            canvas.drawCircle(x, y, radius, paint)
        }
        repeat(5) {
            val startX = rect.left + rect.width() * (0.05f + random.nextFloat() * 0.80f)
            val startY = rect.top + rect.height() * (0.08f + random.nextFloat() * 0.42f)
            val length = min(rect.width(), rect.height()) * (0.08f + random.nextFloat() * 0.08f)
            val angle = 0.65 + (random.nextDouble() * 0.12)
            val endX = startX + (cos(angle) * length).toFloat()
            val endY = startY + (sin(angle) * length).toFloat()
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textureColor(style, if (style.isDark) 26 else 17)
                strokeWidth = 0.95f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(startX, startY, endX, endY, line)
            canvas.drawCircle(endX, endY, 1.15f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textureColor(style, if (style.isDark) 41 else 26)
            })
        }
    }

    private fun meteorOpacity(progress: Float): Float {
        val p = ((progress % 1f) + 1f) % 1f
        return when {
            p < 0.15f -> 0f
            p < 0.32f -> (p - 0.15f) / 0.17f
            p < 0.63f -> 1f
            p < 0.82f -> 1f - ((p - 0.63f) / 0.19f)
            else -> 0f
        }
    }

    private fun drawTransientMeteors(canvas: Canvas, rect: RectF, style: GlassExportStyle) {
        val opacity = meteorOpacity(style.backgroundPhase)
        if (opacity <= 0f) return
        val seed = (style.backgroundPhase * 10000f).toInt() xor 0x2F57
        val random = Random(seed.toLong())
        repeat(4) {
            val startX = rect.left + rect.width() * (0.06f + random.nextFloat() * 0.78f)
            val startY = rect.top + rect.height() * (0.05f + random.nextFloat() * 0.42f)
            val length = min(rect.width(), rect.height()) * (0.13f + random.nextFloat() * 0.10f)
            val angle = 0.72 + (random.nextDouble() * 0.16)
            val endX = startX + (cos(angle) * length).toFloat()
            val endY = startY + (sin(angle) * length).toFloat()
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textureColor(style, ((if (style.isDark) 46 else 31) * opacity).toInt().coerceAtLeast(1))
                strokeWidth = 1.2f + random.nextFloat() * 0.4f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(startX, startY, endX, endY, line)
            canvas.drawCircle(endX, endY, 1.5f + random.nextFloat() * 0.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textureColor(style, ((if (style.isDark) 72 else 43) * opacity).toInt().coerceAtLeast(1))
            })
        }
    }

    private fun drawStars(canvas: Canvas, rect: RectF, style: GlassExportStyle, constellation: Boolean) {
        val random = Random(if (constellation) 0x51A7 else 0x57A2)
        val count = if (constellation) 30 else 52
        val points = ArrayList<Pair<Float, Float>>(count)
        val strengths = ArrayList<Float>(count)
        repeat(count) { index ->
            val yRatio = Math.pow(random.nextDouble(), 0.72).toFloat()
            points += Pair(
                rect.left + random.nextFloat() * rect.width(),
                rect.top + yRatio * rect.height(),
            )
            strengths += 0.80f + (index % 5) * 0.05f
        }

        if (constellation) {
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = 0.55f * (rect.width() / 400f).coerceIn(1f, 2.6f)
                color = textureColor(style, if (style.isDark) 19 else 13)
            }
            var i = 0
            while (i + 1 < points.size) {
                val a = points[i]
                val b = points[i + 1]
                val maxDistance = rect.width() * 0.375f
                val abX = a.first - b.first
                val abY = a.second - b.second
                if (abX * abX + abY * abY < maxDistance * maxDistance) {
                    canvas.drawLine(a.first, a.second, b.first, b.second, linePaint)
                }
                if (i + 2 < points.size) {
                    val c = points[i + 2]
                    val bcX = b.first - c.first
                    val bcY = b.second - c.second
                    if (bcX * bcX + bcY * bcY < maxDistance * maxDistance) {
                        canvas.drawLine(b.first, b.second, c.first, c.second, linePaint)
                    }
                }
                i += 3
            }
        }

        val scale = (rect.width() / 400f).coerceIn(1f, 2.6f)
        points.forEachIndexed { index, point ->
            val strength = strengths[index] * strengths[index]
            val radius = if (constellation) {
                if (index % 7 == 0) 1.65f else 0.85f + (index % 3) * 0.18f
            } else {
                if (index % 11 == 0) 1.45f else 0.65f + (index % 4) * 0.13f
            } * scale
            val baseAlpha = if (style.isDark) 45f else 31f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textureColor(style, (baseAlpha * strength).toInt().coerceAtLeast(1))
            }
            canvas.drawCircle(point.first, point.second, radius, paint)
            if (constellation && index % 9 == 0) {
                val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textureColor(style, ((if (style.isDark) 32f else 22f) * strength).toInt().coerceAtLeast(1))
                    strokeWidth = 0.55f * scale
                }
                canvas.drawLine(point.first - 3.5f * scale, point.second, point.first + 3.5f * scale, point.second, cross)
                canvas.drawLine(point.first, point.second - 3.5f * scale, point.first, point.second + 3.5f * scale, cross)
            }
        }
    }


    private fun drawHearts(canvas: Canvas, rect: RectF, style: GlassExportStyle) {
        val random = Random(0x7E41)
        repeat(15) {
            val centerX = rect.left + rect.width() * (0.08f + random.nextFloat() * 0.84f)
            val centerY = rect.top + rect.height() * (0.10f + random.nextFloat() * 0.80f)
            val size = 6f + random.nextFloat() * 7f
            val path = Path().apply {
                moveTo(centerX, centerY + size * 0.34f)
                cubicTo(
                    centerX - size * 0.80f,
                    centerY - size * 0.18f,
                    centerX - size * 0.82f,
                    centerY - size * 0.86f,
                    centerX,
                    centerY - size * 0.28f,
                )
                cubicTo(
                    centerX + size * 0.82f,
                    centerY - size * 0.86f,
                    centerX + size * 0.80f,
                    centerY - size * 0.18f,
                    centerX,
                    centerY + size * 0.34f,
                )
                close()
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = 0.9f
                color = textureColor(style, if (style.isDark) 28 else 18)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun buildShorePath(rect: RectF, level: Float = 0.76f): Path = Path().apply {
        moveTo(rect.left, rect.top + rect.height() * (level + 0.02f))
        cubicTo(
            rect.left + rect.width() * 0.18f,
            rect.top + rect.height() * (level - 0.01f),
            rect.left + rect.width() * 0.38f,
            rect.top + rect.height() * (level + 0.04f),
            rect.left + rect.width() * 0.58f,
            rect.top + rect.height() * level,
        )
        cubicTo(
            rect.left + rect.width() * 0.76f,
            rect.top + rect.height() * (level - 0.03f),
            rect.left + rect.width() * 0.90f,
            rect.top + rect.height() * (level + 0.01f),
            rect.right,
            rect.top + rect.height() * (level - 0.01f),
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )
}

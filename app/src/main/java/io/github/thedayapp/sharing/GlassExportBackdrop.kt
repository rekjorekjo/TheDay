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
            "WAVE" -> drawSnowMotion(canvas, rect, style)
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
        val travelSpan = rect.height() + 64f
        repeat(52) { index ->
            val baseX = rect.left + random.nextFloat() * (rect.width() + 40f) - 20f
            val baseOffset = random.nextFloat() * travelSpan
            val fallCycles = if (index % 8 == 0) 2f else 1f
            val windPhase = random.nextDouble() * PI * 2.0
            val wind = (sin(windPhase + phase * PI * 2.0) * 1.8).toFloat()
            val y = rect.top + ((baseOffset + phase * travelSpan * fallCycles) % travelSpan) - 32f
            val length = min(rect.width(), rect.height()) * (0.045f + random.nextFloat() * 0.028f)
            val dx = length * (0.16f + random.nextFloat() * 0.07f)
            val dy = length * (0.84f + random.nextFloat() * 0.08f)
            paint.strokeWidth = 0.9f + random.nextFloat() * 0.55f
            paint.color = textureColor(style, if (style.isDark) 34 else 22)
            canvas.drawLine(baseX + wind, y, baseX + wind - dx, y + dy, paint)
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

    private fun drawSnowflake(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, alpha: Int) {
        val glowRadius = radius * 1.9f
        canvas.drawCircle(
            centerX,
            centerY,
            glowRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    centerX,
                    centerY,
                    glowRadius,
                    intArrayOf(
                        withAlpha(Color.rgb(239, 247, 255), (alpha * 0.32f).toInt().coerceIn(0, 255)),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(Color.rgb(249, 252, 255), alpha.coerceIn(1, 255))
            strokeWidth = (radius * 0.18f).coerceIn(0.7f, 2.2f)
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val arm = radius * 1.08f
        val branchLength = radius * 0.34f
        val branchOffset = radius * 0.52f
        repeat(6) { index ->
            val angle = (PI / 3.0) * index
            val endX = centerX + (cos(angle) * arm).toFloat()
            val endY = centerY + (sin(angle) * arm).toFloat()
            canvas.drawLine(centerX, centerY, endX, endY, paint)

            val baseX = centerX + (cos(angle) * branchOffset).toFloat()
            val baseY = centerY + (sin(angle) * branchOffset).toFloat()
            val branchA = angle + (PI / 4.0)
            val branchB = angle - (PI / 4.0)
            canvas.drawLine(
                baseX,
                baseY,
                baseX + (cos(branchA) * branchLength).toFloat(),
                baseY + (sin(branchA) * branchLength).toFloat(),
                paint,
            )
            canvas.drawLine(
                baseX,
                baseY,
                baseX + (cos(branchB) * branchLength).toFloat(),
                baseY + (sin(branchB) * branchLength).toFloat(),
                paint,
            )
        }
    }


    private fun drawSnowMotion(canvas: Canvas, rect: RectF, style: GlassExportStyle) {
        val phase = ((style.backgroundPhase % 1f) + 1f) % 1f
        val farRandom = Random(0x5F21)
        val farSpan = rect.height() + 40f
        repeat(48) { index ->
            val baseX = rect.left + farRandom.nextFloat() * rect.width()
            val baseOffset = farRandom.nextFloat() * farSpan
            val radius = 0.75f + farRandom.nextFloat() * 1.2f
            val fallCycles = if (index % 6 == 0) 2f else 1f
            val driftAmplitude = 2.5 + farRandom.nextFloat() * 4.0
            val driftPhase = farRandom.nextDouble() * PI * 2.0
            val driftCycles = if (index % 5 == 0) 2.0 else 1.0
            val y = rect.top + ((baseOffset + phase * farSpan * fallCycles) % farSpan) - 20f
            val x = baseX + (sin(driftPhase + phase * PI * 2.0 * driftCycles) * driftAmplitude).toFloat()
            val alpha = (((if (style.isDark) 0.16f else 0.10f) + (radius * 0.030f)) * 255f).toInt().coerceIn(1, 255)
            canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(Color.rgb(247, 251, 255), alpha)
            })
        }

        val nearRandom = Random(0x7319)
        val nearSpan = rect.height() + 56f
        repeat(20) { index ->
            val baseX = rect.left + nearRandom.nextFloat() * rect.width()
            val baseOffset = nearRandom.nextFloat() * nearSpan
            val radius = 2.2f + nearRandom.nextFloat() * 2.8f
            val fallCycles = if (index % 5 == 0) 2f else 1f
            val swayAmplitude = 4.0 + nearRandom.nextFloat() * 6.0
            val swayPhase = nearRandom.nextDouble() * PI * 2.0
            val y = rect.top + ((baseOffset + phase * nearSpan * fallCycles) % nearSpan) - 28f
            val x = baseX + (cos(swayPhase + phase * PI * 2.0) * swayAmplitude).toFloat()
            val alpha = (((if (style.isDark) 0.26f else 0.16f) + nearRandom.nextFloat() * 0.16f) * 255f).toInt().coerceIn(1, 255)
            drawSnowflake(canvas, x, y, radius, alpha)
        }
    }

    private fun drawMeteorField(canvas: Canvas, rect: RectF, style: GlassExportStyle) {
        val random = Random(0x5A31)
        repeat(24) {
            val x = rect.left + random.nextFloat() * rect.width()
            val y = rect.top + rect.height() * (0.04f + random.nextFloat() * 0.92f)
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
            val yRatio = random.nextFloat()
            points += Pair(
                rect.left + random.nextFloat() * rect.width(),
                rect.top + yRatio * rect.height(),
            )
            strengths += 0.84f + (index % 5) * 0.06f
        }

        if (constellation) {
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = 0.55f * (rect.width() / 400f).coerceIn(1f, 2.6f)
                color = textureColor(style, if (style.isDark) 26 else 18)
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
            val emphasis = strengths[index].coerceIn(0.84f, 1.20f)
            val glowRadius = (if (constellation) {
                if (index % 7 == 0) 5.6f else 3.1f + (index % 3) * 0.55f
            } else {
                if (index % 11 == 0) 4.6f else 2.2f + (index % 4) * 0.4f
            }) * scale
            canvas.drawCircle(
                point.first,
                point.second,
                glowRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        point.first,
                        point.second,
                        glowRadius,
                        intArrayOf(
                            textureColor(style, (((if (style.isDark) 28f else 19f) * emphasis)).toInt().coerceIn(1, 255)),
                            Color.TRANSPARENT,
                        ),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                },
            )

            val radius = (if (constellation) {
                if (index % 7 == 0) 1.95f else 1.05f + (index % 3) * 0.22f
            } else {
                if (index % 11 == 0) 1.65f else 0.82f + (index % 4) * 0.15f
            }) * scale
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textureColor(style, (((if (style.isDark) 84f else 56f) * emphasis)).toInt().coerceAtLeast(1))
            }
            canvas.drawCircle(point.first, point.second, radius, paint)
            if (constellation && (index % 4 == 0 || emphasis > 1.04f)) {
                val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textureColor(style, (((if (style.isDark) 56f else 38f) * emphasis)).toInt().coerceAtLeast(1))
                    strokeWidth = 0.65f * scale
                    strokeCap = Paint.Cap.ROUND
                }
                val reach = (if (index % 7 == 0) 4.6f else 3.2f) * scale
                canvas.drawLine(point.first - reach, point.second, point.first + reach, point.second, cross)
                canvas.drawLine(point.first, point.second - reach, point.first, point.second + reach, cross)
            }
        }
    }


    private fun drawHearts(canvas: Canvas, rect: RectF, style: GlassExportStyle) {
        val phase = ((style.backgroundPhase % 1f) + 1f) % 1f
        val refreshBucket = (phase * 5f).toInt().coerceIn(0, 4)
        val random = Random((0x7E41 xor (refreshBucket * 0x45D9F3B)).toLong())
        val count = 10 + random.nextInt(11)
        repeat(count) {
            val centerX = rect.left + rect.width() * (0.05f + random.nextFloat() * 0.90f)
            val centerY = rect.top + rect.height() * (0.05f + random.nextFloat() * 0.90f)
            val size = 5.5f + random.nextFloat() * 8f
            val depth = random.nextFloat()
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
            val minAlpha = if (style.isDark) 14f else 10f
            val alphaRange = if (style.isDark) 27f else 18f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = 0.72f + depth * 0.42f
                color = textureColor(
                    style,
                    (minAlpha + depth * alphaRange).toInt().coerceIn(1, 255),
                )
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

package io.github.thedayapp.sharing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.LocalImageReference
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.media.LocalImageStore
import io.github.thedayapp.util.DateFormatting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

data class MemoryImagePalette(
    val background: Int,
    val surface: Int,
    val primary: Int,
    val primaryContainer: Int,
    val onPrimary: Int,
    val onPrimaryContainer: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val isDark: Boolean,
)

object EventMemoryImageRenderer {
    const val PORTRAIT_WIDTH = 1080
    const val PORTRAIT_HEIGHT = 1440
    const val LANDSCAPE_WIDTH = 1440
    const val LANDSCAPE_HEIGHT = 1080

    private const val SOURCE_IMAGE_MAX_LONG_EDGE = 2160
    private const val LANDSCAPE_ASPECT_RATIO_THRESHOLD = 1.15f

    private data class DecorationPoint(
        val x: Float,
        val y: Float,
        val scale: Float,
        val rotation: Float,
        val alphaScale: Float,
        val isCenter: Boolean,
    )

    private data class GradientLine(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
    )

    private data class DecorationAlphaProfile(
        val primaryFillMin: Int,
        val primaryFillMax: Int,
        val variantFillMin: Int,
        val variantFillMax: Int,
        val primaryStrokeMin: Int,
        val primaryStrokeMax: Int,
    )

    private fun decorationAlphaProfile(isDark: Boolean): DecorationAlphaProfile {
        return if (isDark) {
            DecorationAlphaProfile(
                primaryFillMin = 22,
                primaryFillMax = 48,
                variantFillMin = 19,
                variantFillMax = 42,
                primaryStrokeMin = 32,
                primaryStrokeMax = 58,
            )
        } else {
            DecorationAlphaProfile(
                primaryFillMin = 26,
                primaryFillMax = 54,
                variantFillMin = 22,
                variantFillMax = 48,
                primaryStrokeMin = 36,
                primaryStrokeMax = 66,
            )
        }
    }

    private fun gradientLineForRect(
        rect: RectF,
        angleRadians: Float,
    ): GradientLine {
        val centerX = rect.centerX()
        val centerY = rect.centerY()
        val halfDiagonal = hypot(rect.width(), rect.height()) / 2f

        val dx = cos(angleRadians.toDouble()).toFloat() * halfDiagonal
        val dy = sin(angleRadians.toDouble()).toFloat() * halfDiagonal

        return GradientLine(
            startX = centerX - dx,
            startY = centerY - dy,
            endX = centerX + dx,
            endY = centerY + dy,
        )
    }

    private fun blendArgb(
        from: Int,
        to: Int,
        fraction: Float,
    ): Int {
        val clampedFraction = fraction.coerceIn(0f, 1f)
        val fromAlpha = Color.alpha(from)
        val fromRed = Color.red(from)
        val fromGreen = Color.green(from)
        val fromBlue = Color.blue(from)

        val toAlpha = Color.alpha(to)
        val toRed = Color.red(to)
        val toGreen = Color.green(to)
        val toBlue = Color.blue(to)

        val resultAlpha = (fromAlpha + (toAlpha - fromAlpha) * clampedFraction).roundToInt()
        val resultRed = (fromRed + (toRed - fromRed) * clampedFraction).roundToInt()
        val resultGreen = (fromGreen + (toGreen - fromGreen) * clampedFraction).roundToInt()
        val resultBlue = (fromBlue + (toBlue - fromBlue) * clampedFraction).roundToInt()

        return Color.argb(resultAlpha, resultRed, resultGreen, resultBlue)
    }

    private fun randomCenterPoint(random: Random): DecorationPoint {
        val x = 0.25f + random.nextFloat() * 0.5f
        val y = 0.22f + random.nextFloat() * 0.56f

        val baseScale = 0.55f + random.nextFloat() * 0.3f
        val baseAlpha = 0.55f + random.nextFloat() * 0.23f

        val (finalScale, finalAlpha) = if (x in 0.36f..0.64f && y in 0.38f..0.64f) {
            baseScale * 0.65f to baseAlpha * 0.6f
        } else {
            baseScale to baseAlpha
        }

        return DecorationPoint(
            x = x,
            y = y,
            scale = finalScale,
            rotation = random.nextFloat() * 360f,
            alphaScale = finalAlpha,
            isCenter = true,
        )
    }

    private fun randomOuterPoint(random: Random): DecorationPoint {
        var x: Float
        var y: Float

        do {
            val candidateX = random.nextFloat()
            val candidateY = random.nextFloat()
            x = candidateX
            y = candidateY
        } while (x in 0.25f..0.75f && y in 0.22f..0.78f)

        return DecorationPoint(
            x = x,
            y = y,
            scale = 0.75f + random.nextFloat() * 0.5f,
            rotation = random.nextFloat() * 360f,
            alphaScale = 0.8f + random.nextFloat() * 0.3f,
            isCenter = false,
        )
    }

    private fun generateDecorationPoints(
        random: Random,
        totalCount: Int,
    ): List<DecorationPoint> {
        val centerCount = (totalCount * 0.2f).roundToInt().coerceAtLeast(1)
        val outerCount = totalCount - centerCount

        val centerPoints = List(centerCount) { randomCenterPoint(random) }
        val outerPoints = List(outerCount) { randomOuterPoint(random) }

        return (centerPoints + outerPoints).shuffled(random)
    }

    suspend fun render(
        context: Context,
        event: DayEvent,
        today: LocalDate,
        palette: MemoryImagePalette,
        template: MemoryImageTemplate,
    ): Result<Bitmap> {
        return try {
            val bitmap = withContext(Dispatchers.Default) {
                renderInternal(
                    context = context.applicationContext,
                    event = event,
                    today = today,
                    palette = palette,
                    template = template,
                )
            }
            Result.success(bitmap)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun renderInternal(
        context: Context,
        event: DayEvent,
        today: LocalDate,
        palette: MemoryImagePalette,
        template: MemoryImageTemplate,
    ): Bitmap {
        val imageReference = event.backgroundImage
        val imageFile = imageReference
            ?.let { reference ->
                LocalImageStore(context).fileFor(reference.fileName)
            }

        val sourceAspectRatio = imageFile?.let { file ->
            getImageAspectRatio(file)
        }

        val isLandscape = sourceAspectRatio != null && sourceAspectRatio >= LANDSCAPE_ASPECT_RATIO_THRESHOLD

        val width = if (isLandscape) LANDSCAPE_WIDTH else PORTRAIT_WIDTH
        val height = if (isLandscape) LANDSCAPE_HEIGHT else PORTRAIT_HEIGHT

        val output = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(output)
        var sourceBitmap: Bitmap? = null

        try {
            val hasBackgroundImage = imageFile != null && imageReference != null
            val random = Random.Default

            if (hasBackgroundImage) {
                sourceBitmap = imageFile?.let(::decodeBackgroundImage)
            }

            val baseSize = min(width, height).toFloat()
            val outerInset = baseSize * 0.035f
            val outerRadius = baseSize * 0.035f
            val outerBorderWidth = baseSize * 0.0018f

            val cardRect = RectF(
                outerInset,
                outerInset,
                width - outerInset,
                height - outerInset,
            )

            val cardPath = Path().apply {
                addRoundRect(
                    cardRect,
                    outerRadius,
                    outerRadius,
                    Path.Direction.CW,
                )
            }

            val brandFraction = if (isLandscape) 0.09f else 0.072f
            val sloganFraction = if (isLandscape) 0.112f else 0.088f

            val brandBottom = cardRect.top + cardRect.height() * brandFraction
            val sloganTop = cardRect.bottom - cardRect.height() * sloganFraction

            val brandRect = RectF(
                cardRect.left,
                cardRect.top,
                cardRect.right,
                brandBottom,
            )

            val mainRect = RectF(
                cardRect.left,
                brandBottom,
                cardRect.right,
                sloganTop,
            )

            val sloganRect = RectF(
                cardRect.left,
                sloganTop,
                cardRect.right,
                cardRect.bottom,
            )

            val cardSaveCount = canvas.save()
            canvas.clipPath(cardPath)

            drawBaseRegions(
                canvas = canvas,
                brandRect = brandRect,
                mainRect = mainRect,
                sloganRect = sloganRect,
                palette = palette,
                hasBackgroundImage = hasBackgroundImage,
                random = random,
            )

            drawTemplateDecoration(
                canvas = canvas,
                cardRect = cardRect,
                palette = palette,
                template = template,
            )

            if (sourceBitmap != null && imageReference != null) {
                val imageSaveCount = canvas.save()
                canvas.clipRect(mainRect)

                drawFocusedCenterCrop(
                    canvas = canvas,
                    bitmap = sourceBitmap,
                    destination = mainRect,
                    image = imageReference,
                )

                drawImageReadabilityOverlay(
                    canvas = canvas,
                    mainRect = mainRect,
                )

                canvas.restoreToCount(imageSaveCount)
            }

            drawBrandText(
                canvas = canvas,
                brandRect = brandRect,
                palette = palette,
                isLandscape = isLandscape,
            )

            drawMainContent(
                canvas = canvas,
                event = event,
                today = today,
                palette = palette,
                hasBackgroundImage = hasBackgroundImage,
                mainRect = mainRect,
            )

            drawSloganText(
                canvas = canvas,
                sloganRect = sloganRect,
                palette = palette,
                isLandscape = isLandscape,
            )

            drawInternalDividers(
                canvas = canvas,
                brandRect = brandRect,
                sloganRect = sloganRect,
                cardRect = cardRect,
                baseSize = baseSize,
                palette = palette,
            )

            canvas.restoreToCount(cardSaveCount)

            val borderColor = if (palette.isDark) {
                withAlpha(palette.primary, 108)
            } else {
                withAlpha(palette.primary, 135)
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor
                style = Paint.Style.STROKE
                strokeWidth = outerBorderWidth
            }
            canvas.drawRoundRect(
                cardRect,
                outerRadius,
                outerRadius,
                borderPaint,
            )

            return output
        } catch (exception: Exception) {
            output.recycle()
            throw exception
        } finally {
            sourceBitmap?.let { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun getImageAspectRatio(file: File): Float? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        return bounds.outWidth.toFloat() / bounds.outHeight.toFloat()
    }

    private fun drawBaseRegions(
        canvas: Canvas,
        brandRect: RectF,
        mainRect: RectF,
        sloganRect: RectF,
        palette: MemoryImagePalette,
        hasBackgroundImage: Boolean,
        random: Random,
    ) {
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.surface
        }
        canvas.drawRect(brandRect, brandPaint)

        val mainPaint = if (hasBackgroundImage) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    mainRect.top,
                    mainRect.right,
                    mainRect.bottom,
                    intArrayOf(
                        palette.primaryContainer,
                        palette.background,
                        palette.surface,
                    ),
                    floatArrayOf(0f, 0.58f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
        } else {
            val gradientAngle = random.nextFloat() * (Math.PI * 2.0).toFloat()
            val gradientLine = gradientLineForRect(mainRect, gradientAngle)

            val middleColor = blendArgb(
                palette.primaryContainer,
                palette.background,
                0.5f,
            )

            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    gradientLine.startX,
                    gradientLine.startY,
                    gradientLine.endX,
                    gradientLine.endY,
                    intArrayOf(
                        palette.primaryContainer,
                        middleColor,
                        palette.background,
                    ),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
        }
        canvas.drawRect(mainRect, mainPaint)

        val sloganPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.surface
        }
        canvas.drawRect(sloganRect, sloganPaint)
    }

    private fun drawTemplateDecoration(
        canvas: Canvas,
        cardRect: RectF,
        palette: MemoryImagePalette,
        template: MemoryImageTemplate,
    ) {
        val random = Random.Default

        when (template) {
            MemoryImageTemplate.CIRCLES -> drawCircleTemplate(
                canvas = canvas,
                cardRect = cardRect,
                palette = palette,
                random = random,
            )

            MemoryImageTemplate.STARS -> drawStarTemplate(
                canvas = canvas,
                cardRect = cardRect,
                palette = palette,
                random = random,
            )

            MemoryImageTemplate.METEORS -> drawMeteorTemplate(
                canvas = canvas,
                cardRect = cardRect,
                palette = palette,
                random = random,
            )

            MemoryImageTemplate.WAVES -> drawWaveTemplate(
                canvas = canvas,
                cardRect = cardRect,
                palette = palette,
                random = random,
            )

            MemoryImageTemplate.MINIMAL -> drawMinimalTemplate(
                canvas = canvas,
                cardRect = cardRect,
                palette = palette,
                random = random,
            )
        }
    }

    private fun drawCircleTemplate(
        canvas: Canvas,
        cardRect: RectF,
        palette: MemoryImagePalette,
        random: Random,
    ) {
        val baseSize = minOf(cardRect.width(), cardRect.height())
        val profile = decorationAlphaProfile(palette.isDark)
        val totalCount = 8 + random.nextInt(3)
        val points = generateDecorationPoints(random, totalCount)

        val primaryFillAlpha = random.nextInt(profile.primaryFillMin, profile.primaryFillMax + 1)
        val variantFillAlpha = random.nextInt(profile.variantFillMin, profile.variantFillMax + 1)
        val strokeAlpha = random.nextInt(profile.primaryStrokeMin, profile.primaryStrokeMax + 1)

        val baseFillPrimaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
        }
        val baseFillVariantPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurfaceVariant
        }
        val baseStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
            style = Paint.Style.STROKE
            strokeWidth = baseSize * 0.003f
        }

        var largeOuterCount = 0
        points.forEach { point ->
            val cx = cardRect.left + cardRect.width() * point.x
            val cy = cardRect.top + cardRect.height() * point.y

            val baseRadius = if (point.isCenter) {
                baseSize * (0.035f + random.nextFloat() * 0.055f)
            } else {
                if (largeOuterCount < 2 && random.nextFloat() < 0.3f) {
                    largeOuterCount++
                    baseSize * (0.14f + random.nextFloat() * 0.04f)
                } else {
                    baseSize * (0.05f + random.nextFloat() * 0.09f)
                }
            }

            val radius = baseRadius * point.scale
            val isStroke = random.nextFloat() < 0.4f

            val elementPaint = if (isStroke) {
                Paint(baseStrokePaint).apply {
                    alpha = (strokeAlpha * point.alphaScale).roundToInt().coerceIn(0, 255)
                }
            } else {
                val baseAlpha = if (random.nextFloat() < 0.5f) primaryFillAlpha else variantFillAlpha
                val basePaint = if (random.nextFloat() < 0.5f) baseFillPrimaryPaint else baseFillVariantPaint
                Paint(basePaint).apply {
                    alpha = (baseAlpha * point.alphaScale).roundToInt().coerceIn(0, 255)
                }
            }

            canvas.drawCircle(cx, cy, radius, elementPaint)
        }
    }

    private fun drawStarTemplate(
        canvas: Canvas,
        cardRect: RectF,
        palette: MemoryImagePalette,
        random: Random,
    ) {
        val baseSize = minOf(cardRect.width(), cardRect.height())
        val profile = decorationAlphaProfile(palette.isDark)
        val totalCount = 12 + random.nextInt(4)
        val points = generateDecorationPoints(random, totalCount)

        val primaryFillAlpha = random.nextInt(profile.primaryFillMin, profile.primaryFillMax + 1)
        val variantFillAlpha = random.nextInt(profile.variantFillMin, profile.variantFillMax + 1)
        val strokeAlpha = random.nextInt(profile.primaryStrokeMin, profile.primaryStrokeMax + 1)

        val baseFillPrimaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
        }
        val baseFillVariantPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurfaceVariant
        }
        val baseStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
            strokeWidth = baseSize * 0.0025f
            style = Paint.Style.STROKE
        }

        var largeOuterCount = 0
        points.forEach { point ->
            val cx = cardRect.left + cardRect.width() * point.x
            val cy = cardRect.top + cardRect.height() * point.y

            val baseRadius = if (point.isCenter) {
                baseSize * (0.018f + random.nextFloat() * 0.025f)
            } else {
                if (largeOuterCount < 2 && random.nextFloat() < 0.25f) {
                    largeOuterCount++
                    baseSize * (0.05f + random.nextFloat() * 0.015f)
                } else {
                    baseSize * (0.022f + random.nextFloat() * 0.038f)
                }
            }

            val radius = baseRadius * point.scale
            val isStroke = point.isCenter || random.nextFloat() < 0.55f

            val elementPaint = if (isStroke) {
                Paint(baseStrokePaint).apply {
                    alpha = (strokeAlpha * point.alphaScale).roundToInt().coerceIn(0, 255)
                }
            } else {
                val baseAlpha = if (random.nextFloat() < 0.5f) primaryFillAlpha else variantFillAlpha
                val basePaint = if (random.nextFloat() < 0.5f) baseFillPrimaryPaint else baseFillVariantPaint
                Paint(basePaint).apply {
                    alpha = (baseAlpha * point.alphaScale).roundToInt().coerceIn(0, 255)
                }
            }

            drawStar(canvas, cx, cy, radius, elementPaint, point.rotation)
        }
    }

    private fun drawStar(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        paint: Paint,
        rotation: Float,
    ) {
        val path = Path()
        val innerRadius = radius * 0.4f

        for (i in 0..4) {
            val outerAngle = Math.toRadians(rotation + i * 72.0 - 90.0).toFloat()
            val innerAngle = Math.toRadians(rotation + i * 72.0 + 36.0 - 90.0).toFloat()

            val outerX = cx + radius * kotlin.math.cos(outerAngle)
            val outerY = cy + radius * kotlin.math.sin(outerAngle)
            val innerX = cx + innerRadius * kotlin.math.cos(innerAngle)
            val innerY = cy + innerRadius * kotlin.math.sin(innerAngle)

            if (i == 0) {
                path.moveTo(outerX, outerY)
            } else {
                path.lineTo(outerX, outerY)
            }
            path.lineTo(innerX, innerY)
        }
        path.close()

        canvas.drawPath(path, paint)
    }

    private fun drawMeteorTemplate(
        canvas: Canvas,
        cardRect: RectF,
        palette: MemoryImagePalette,
        random: Random,
    ) {
        val baseSize = minOf(cardRect.width(), cardRect.height())
        val profile = decorationAlphaProfile(palette.isDark)
        val totalCount = 7 + random.nextInt(3)
        val points = generateDecorationPoints(random, totalCount)

        val strokeAlpha = random.nextInt(profile.primaryStrokeMin, profile.primaryStrokeMax + 1)
        val headAlpha = random.nextInt(profile.variantFillMin, profile.variantFillMax + 1)

        val baseTailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
            strokeWidth = baseSize * 0.003f
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val baseHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurfaceVariant
        }

        points.forEach { point ->
            val headX = cardRect.left + cardRect.width() * point.x
            val headY = cardRect.top + cardRect.height() * point.y
            val size = baseSize * 0.04f * point.scale

            val headPaint = Paint(baseHeadPaint).apply {
                alpha = (headAlpha * point.alphaScale).roundToInt().coerceIn(0, 255)
            }
            canvas.drawCircle(headX, headY, size * 0.3f, headPaint)

            val directionX = if (point.x >= 0.5f) -1f else 1f
            val directionY = -0.65f

            val baseTailLength = baseSize * (0.055f + random.nextFloat() * 0.065f)
            val tailLength = baseTailLength * point.scale * if (point.isCenter) 0.55f else 1f

            val tailCount = if (point.isCenter) 2 else 3

            val tailPaint = Paint(baseTailPaint).apply {
                alpha = (strokeAlpha * point.alphaScale).roundToInt().coerceIn(0, 255)
            }

            for (i in 1..tailCount) {
                val offset = i * size * 0.6f
                val tailStartX = headX + directionX * offset
                val tailStartY = headY + directionY * offset
                val tailEndX = tailStartX + directionX * tailLength
                val tailEndY = tailStartY + directionY * tailLength

                canvas.drawLine(tailStartX, tailStartY, tailEndX, tailEndY, tailPaint)
            }
        }
    }

    private fun drawWaveTemplate(
        canvas: Canvas,
        cardRect: RectF,
        palette: MemoryImagePalette,
        random: Random,
    ) {
        val baseSize = minOf(cardRect.width(), cardRect.height())
        val profile = decorationAlphaProfile(palette.isDark)

        val basePrimaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
            strokeWidth = baseSize * 0.0022f
            style = Paint.Style.STROKE
        }
        val baseVariantPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurfaceVariant
            strokeWidth = baseSize * 0.0018f
            style = Paint.Style.STROKE
        }

        val width = cardRect.width()
        val height = cardRect.height()

        val centerCount = (10 + random.nextInt(3)) / 5
        val outerCount = (10 + random.nextInt(3)) - centerCount

        for (i in 0 until centerCount) {
            val path = Path()
            val y = cardRect.top + height * (0.28f + random.nextFloat() * 0.44f)
            val amplitude = height * 0.015f

            path.moveTo(cardRect.left + width * 0.2f, y)
            path.quadTo(
                cardRect.left + width * 0.4f, y - amplitude,
                cardRect.left + width * 0.6f, y,
            )
            path.quadTo(
                cardRect.left + width * 0.8f, y + amplitude,
                cardRect.right - width * 0.2f, y,
            )

            val usePrimary = random.nextFloat() < 0.5f
            val baseAlpha = if (usePrimary) {
                random.nextInt(profile.primaryStrokeMin, profile.primaryStrokeMax + 1)
            } else {
                random.nextInt(profile.variantFillMin, profile.variantFillMax + 1)
            }
            val alphaScale = 0.6f + random.nextFloat() * 0.15f
            val widthScale = 0.6f + random.nextFloat() * 0.15f

            val pathPaint = Paint(if (usePrimary) basePrimaryPaint else baseVariantPaint).apply {
                strokeWidth *= widthScale
                alpha = (baseAlpha * alphaScale).roundToInt().coerceIn(0, 255)
            }

            canvas.drawPath(path, pathPaint)
        }

        for (i in 0 until outerCount) {
            val path = Path()
            val y = if (random.nextFloat() < 0.5f) {
                cardRect.top + height * (0.05f + random.nextFloat() * 0.15f)
            } else {
                cardRect.bottom - height * (0.05f + random.nextFloat() * 0.15f)
            }

            path.moveTo(cardRect.left - width * 0.05f, y)
            path.quadTo(
                cardRect.left + width * 0.25f, y - height * 0.03f,
                cardRect.left + width * 0.5f, y,
            )
            path.quadTo(
                cardRect.left + width * 0.75f, y + height * 0.03f,
                cardRect.right + width * 0.05f, y,
            )

            val usePrimary = i % 2 == 0
            val baseAlpha = if (usePrimary) {
                random.nextInt(profile.primaryStrokeMin, profile.primaryStrokeMax + 1)
            } else {
                random.nextInt(profile.variantFillMin, profile.variantFillMax + 1)
            }

            val pathPaint = Paint(if (usePrimary) basePrimaryPaint else baseVariantPaint).apply {
                alpha = baseAlpha.coerceIn(0, 255)
            }

            canvas.drawPath(path, pathPaint)
        }

        val leftPath = Path().apply {
            moveTo(cardRect.left, cardRect.top + height * 0.2f)
            quadTo(
                cardRect.left + width * 0.06f,
                cardRect.top + height * 0.5f,
                cardRect.left,
                cardRect.bottom - height * 0.2f,
            )
        }
        val leftPaint = Paint(basePrimaryPaint).apply {
            alpha = random.nextInt(profile.primaryStrokeMin, profile.primaryStrokeMax + 1)
        }
        canvas.drawPath(leftPath, leftPaint)

        val rightPath = Path().apply {
            moveTo(cardRect.right, cardRect.top + height * 0.25f)
            quadTo(
                cardRect.right - width * 0.07f,
                cardRect.top + height * 0.5f,
                cardRect.right,
                cardRect.bottom - height * 0.25f,
            )
        }
        val rightPaint = Paint(baseVariantPaint).apply {
            alpha = random.nextInt(profile.variantFillMin, profile.variantFillMax + 1)
        }
        canvas.drawPath(rightPath, rightPaint)
    }

    private fun drawMinimalTemplate(
        canvas: Canvas,
        cardRect: RectF,
        palette: MemoryImagePalette,
        random: Random,
    ) {
        val baseSize = minOf(cardRect.width(), cardRect.height())
        val totalCount = 7 + random.nextInt(3)
        val points = generateDecorationPoints(random, totalCount)

        points.forEachIndexed { index, point ->
            val cx = cardRect.left + cardRect.width() * point.x
            val cy = cardRect.top + cardRect.height() * point.y

            val baseSizeFactor = if (point.isCenter) 0.02f else 0.025f
            val size = baseSize * baseSizeFactor * point.scale

            val baseAlpha = 12 + random.nextInt(30)
            val alpha = (baseAlpha * point.alphaScale).roundToInt().coerceIn(0, 255)

            when (index % 5) {
                0 -> {
                    val radius = size * 1.2f
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.primary
                        style = Paint.Style.STROKE
                        strokeWidth = baseSize * 0.002f
                        this.alpha = alpha
                    }
                    canvas.drawCircle(cx, cy, radius, paint)
                }
                1 -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.onSurfaceVariant
                        this.alpha = alpha
                    }
                    canvas.drawCircle(cx, cy, size * 0.4f, paint)
                }
                2 -> {
                    val rect = RectF(
                        cx - size,
                        cy - size,
                        cx + size,
                        cy + size,
                    )
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.primary
                        style = Paint.Style.STROKE
                        strokeWidth = baseSize * 0.0018f
                        strokeCap = Paint.Cap.ROUND
                        this.alpha = alpha
                    }
                    canvas.drawArc(rect, point.rotation, 120f, false, paint)
                }
                3 -> {
                    val offset = size * 0.3f
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.onSurfaceVariant
                        this.alpha = alpha
                    }
                    canvas.drawCircle(cx - offset, cy, size * 0.25f, paint)
                    canvas.drawCircle(cx + offset, cy, size * 0.25f, paint)
                }
                else -> {
                    canvas.save()
                    canvas.rotate(point.rotation, cx, cy)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.primary
                        style = Paint.Style.STROKE
                        strokeWidth = baseSize * 0.0018f
                        strokeCap = Paint.Cap.ROUND
                        this.alpha = alpha
                    }
                    canvas.drawLine(cx - size, cy, cx + size, cy, paint)
                    canvas.restore()
                }
            }
        }
    }

    private fun drawImageReadabilityOverlay(
        canvas: Canvas,
        mainRect: RectF,
    ) {
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                mainRect.top,
                0f,
                mainRect.bottom,
                intArrayOf(
                    Color.argb(60, 0, 0, 0),
                    Color.argb(100, 0, 0, 0),
                    Color.argb(180, 0, 0, 0),
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(mainRect, overlayPaint)
    }

    private fun drawBrandText(
        canvas: Canvas,
        brandRect: RectF,
        palette: MemoryImagePalette,
        isLandscape: Boolean,
    ) {
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurface
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = brandRect.height() * if (isLandscape) 0.38f else 0.42f
            letterSpacing = 0.12f
        }

        val textY = brandRect.top + brandRect.height() / 2f -
            (brandPaint.ascent() + brandPaint.descent()) / 2f
        canvas.drawText("The Day", brandRect.centerX(), textY, brandPaint)
    }

    private fun drawMainContent(
        canvas: Canvas,
        event: DayEvent,
        today: LocalDate,
        palette: MemoryImagePalette,
        hasBackgroundImage: Boolean,
        mainRect: RectF,
    ) {
        val locale = Locale.getDefault()
        val delta = DayMath.signedDays(event, today)
        val displayDate = DayMath.effectiveDate(event, today)

        val mainImageHeight = mainRect.height()

        val textColor = if (hasBackgroundImage) Color.WHITE else palette.onSurface
        val secondaryColor = if (hasBackgroundImage) {
            Color.argb(215, 255, 255, 255)
        } else {
            palette.onSurfaceVariant
        }

        val headerHeight = mainImageHeight * 0.18f
        val headerTop = mainRect.top + mainImageHeight * 0.08f

        drawEventHeader(
            canvas = canvas,
            event = event,
            delta = delta,
            palette = palette,
            hasBackgroundImage = hasBackgroundImage,
            headerTop = headerTop,
            headerHeight = headerHeight,
            mainRect = mainRect,
        )

        val centerTop = mainRect.top + mainImageHeight * 0.38f
        val centerBottom = mainRect.top + mainImageHeight * 0.68f
        val centerY = (centerTop + centerBottom) / 2f

        if (delta == 0L) {
            val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = mainImageHeight * 0.28f
            }
            canvas.drawText(
                "今天",
                mainRect.centerX(),
                centeredBaseline(todayPaint, centerY),
                todayPaint,
            )
        } else {
            drawLargeDayCount(
                canvas = canvas,
                count = abs(delta).toString(),
                color = textColor,
                secondaryColor = secondaryColor,
                centerY = centerY,
                mainImageHeight = mainImageHeight,
                mainRect = mainRect,
            )
        }

        val dateBottom = mainRect.bottom - mainImageHeight * 0.1f
        val dateText = "${DateFormatting.longDate(displayDate, locale)} · " +
            DateFormatting.weekday(displayDate, locale)

        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = mainImageHeight * 0.055f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val fittedDate = TextUtils.ellipsize(
            dateText,
            datePaint,
            mainRect.width() * 0.88f,
            TextUtils.TruncateAt.END,
        ).toString()

        canvas.drawText(
            fittedDate,
            mainRect.centerX(),
            centeredBaseline(datePaint, dateBottom),
            datePaint,
        )
    }

    private fun drawEventHeader(
        canvas: Canvas,
        event: DayEvent,
        delta: Long,
        palette: MemoryImagePalette,
        hasBackgroundImage: Boolean,
        headerTop: Float,
        headerHeight: Float,
        mainRect: RectF,
    ) {
        val safeTitle = event.title
            .trim()
            .replace(Regex("\\s+"), " ")
            .ifBlank { "这个日子" }

        val relation = when {
            delta > 0L -> "还有"
            delta < 0L -> "已经"
            else -> ""
        }

        val titleColor = if (hasBackgroundImage) Color.WHITE else palette.onSurface
        val relationColor = if (hasBackgroundImage) {
            Color.argb(215, 255, 255, 255)
        } else {
            palette.onSurfaceVariant
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = titleColor
            textSize = headerHeight * 0.38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val relationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = relationColor
            textSize = headerHeight * 0.32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val gap = if (relation.isEmpty()) 0f else headerHeight * 0.08f
        val relationWidth = relationPaint.measureText(relation)
        val availableTitleWidth = (
            mainRect.width() * 0.9f - relationWidth - gap
        ).coerceAtLeast(120f)

        val displayedTitle = TextUtils.ellipsize(
            safeTitle,
            titlePaint,
            availableTitleWidth,
            TextUtils.TruncateAt.END,
        ).toString()

        val titleWidth = titlePaint.measureText(displayedTitle)
        val combinedWidth = titleWidth + gap + relationWidth
        var x = mainRect.centerX() - combinedWidth / 2f
        val centerY = headerTop + headerHeight / 2f

        canvas.drawText(
            displayedTitle,
            x,
            centeredBaseline(titlePaint, centerY),
            titlePaint,
        )

        if (relation.isNotEmpty()) {
            x += titleWidth + gap
            canvas.drawText(
                relation,
                x,
                centeredBaseline(relationPaint, centerY),
                relationPaint,
            )
        }
    }

    private fun drawLargeDayCount(
        canvas: Canvas,
        count: String,
        color: Int,
        secondaryColor: Int,
        centerY: Float,
        mainImageHeight: Float,
        mainRect: RectF,
    ) {
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = mainImageHeight * 0.36f
        }

        fitTextSize(
            paint = numberPaint,
            text = count,
            maxWidth = mainRect.width() * 0.75f,
            minSize = mainImageHeight * 0.15f,
        )

        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = secondaryColor
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = mainImageHeight * 0.075f
        }

        val gap = mainImageHeight * 0.025f
        val numberWidth = numberPaint.measureText(count)
        val unitWidth = unitPaint.measureText("天")
        val totalWidth = numberWidth + gap + unitWidth
        val startX = mainRect.centerX() - totalWidth / 2f

        canvas.drawText(
            count,
            startX,
            centeredBaseline(numberPaint, centerY),
            numberPaint,
        )

        canvas.drawText(
            "天",
            startX + numberWidth + gap,
            centerY - numberPaint.textSize * 0.12f,
            unitPaint,
        )
    }

    private fun drawSloganText(
        canvas: Canvas,
        sloganRect: RectF,
        palette: MemoryImagePalette,
        isLandscape: Boolean,
    ) {
        val sloganPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurface
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = sloganRect.height() * if (isLandscape) 0.28f else 0.31f
        }

        val textY = sloganRect.top + sloganRect.height() / 2f -
            (sloganPaint.ascent() + sloganPaint.descent()) / 2f
        canvas.drawText(
            "记录值得记住的每一天",
            sloganRect.centerX(),
            textY,
            sloganPaint,
        )
    }

    private fun drawInternalDividers(
        canvas: Canvas,
        brandRect: RectF,
        sloganRect: RectF,
        cardRect: RectF,
        baseSize: Float,
        palette: MemoryImagePalette,
    ) {
        val dividerAlpha = if (palette.isDark) 48 else 55
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(palette.onSurfaceVariant, dividerAlpha)
            strokeWidth = baseSize * 0.0012f
        }

        canvas.drawLine(
            cardRect.left,
            brandRect.bottom,
            cardRect.right,
            brandRect.bottom,
            dividerPaint,
        )

        canvas.drawLine(
            cardRect.left,
            sloganRect.top,
            cardRect.right,
            sloganRect.top,
            dividerPaint,
        )
    }

    private fun decodeBackgroundImage(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        var sampleSize = 1
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (
            sampleSize <= Int.MAX_VALUE / 2 &&
            longEdge / (sampleSize * 2) >= SOURCE_IMAGE_MAX_LONG_EDGE
        ) {
            sampleSize *= 2
        }

        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    private fun drawFocusedCenterCrop(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        image: LocalImageReference,
    ) {
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            throw IOException("Invalid background image")
        }

        val focusX = if (image.focusX.isFinite()) {
            image.focusX.coerceIn(0f, 1f)
        } else {
            0.5f
        }
        val focusY = if (image.focusY.isFinite()) {
            image.focusY.coerceIn(0f, 1f)
        } else {
            0.5f
        }

        val scale = maxOf(
            destination.width() / bitmap.width.toFloat(),
            destination.height() / bitmap.height.toFloat(),
        )
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale
        val overflowX = (scaledWidth - destination.width()).coerceAtLeast(0f)
        val overflowY = (scaledHeight - destination.height()).coerceAtLeast(0f)

        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                destination.left - overflowX * focusX,
                destination.top - overflowY * focusY,
            )
        }

        canvas.drawBitmap(
            bitmap,
            matrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
    }

    private fun fitTextSize(
        paint: Paint,
        text: String,
        maxWidth: Float,
        minSize: Float,
    ) {
        while (
            paint.textSize > minSize &&
            paint.measureText(text) > maxWidth
        ) {
            paint.textSize -= 8f
        }
    }

    private fun centeredBaseline(
        paint: Paint,
        centerY: Float,
    ): Float {
        return centerY - (paint.ascent() + paint.descent()) / 2f
    }

    private fun withAlpha(
        color: Int,
        alpha: Int,
    ): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }
}
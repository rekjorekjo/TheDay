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
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.ImagePlacementTarget
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
    private const val OUTPUT_LONG_EDGE = 1440
    private const val DEFAULT_OUTPUT_ASPECT_RATIO = 3f / 4f
    private const val SOURCE_IMAGE_MAX_LONG_EDGE = 2160
    private const val LANDSCAPE_ASPECT_RATIO_THRESHOLD = 1.15f
    private const val TOP_RIGHT_DATE_MAX_WIDTH_FRACTION = 0.3f

    private const val MEMORY_MAIN_MIN_ASPECT_RATIO = 0.60f
    private const val MEMORY_MAIN_MAX_ASPECT_RATIO = 1.50f
    private data class MemoryImageLayout(
        val outputAspectRatio: Float,
        val isLandscape: Boolean,
    )

    private data class OutputSize(
        val width: Int,
        val height: Int,
    )

    /**
     * Ratio used by the bottom-sheet preview before the rendered bitmap is
     * ready. The 0.6:1 and 1.5:1 limits now apply directly to the exported
     * memorial image itself.
     */
    internal fun previewAspectRatio(
        image: LocalImageReference?,
    ): Float {
        return memoryImageLayout(image).outputAspectRatio
    }

    private fun memoryImageLayout(
        image: LocalImageReference?,
    ): MemoryImageLayout {
        val aspectRatio = image
            ?.takeIf { reference ->
                reference.width > 0 && reference.height > 0
            }
            ?.let { reference ->
                reference.width.toFloat() / reference.height.toFloat()
            }
            ?.takeIf { ratio ->
                ratio.isFinite() && ratio > 0f
            }
            ?.coerceIn(
                MEMORY_MAIN_MIN_ASPECT_RATIO,
                MEMORY_MAIN_MAX_ASPECT_RATIO,
            )
            ?: DEFAULT_OUTPUT_ASPECT_RATIO

        return MemoryImageLayout(
            outputAspectRatio = aspectRatio,
            isLandscape = aspectRatio >= LANDSCAPE_ASPECT_RATIO_THRESHOLD,
        )
    }

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


    private data class DecorationDistribution(
        val centerWeight: Int,
        val outerWeight: Int,
        val centerRegion: RectF,
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

    private fun decorationDistribution(
        hasBackgroundImage: Boolean,
    ): DecorationDistribution {
        return if (hasBackgroundImage) {
            DecorationDistribution(
                centerWeight = 1,
                outerWeight = 9,
                centerRegion = RectF(0.3f, 0.28f, 0.7f, 0.72f),
            )
        } else {
            DecorationDistribution(
                centerWeight = 1,
                outerWeight = 2,
                centerRegion = RectF(0.25f, 0.22f, 0.75f, 0.78f),
            )
        }
    }

    private fun randomCenterPoint(
        random: Random,
        centerRegion: RectF,
    ): DecorationPoint {

        val x = centerRegion.left + random.nextFloat() * centerRegion.width()
        val y = centerRegion.top + random.nextFloat() * centerRegion.height()

        val baseScale = 0.55f + random.nextFloat() * 0.3f
        val baseAlpha = 0.55f + random.nextFloat() * 0.23f

        val innerQuietLeft = centerRegion.left + centerRegion.width() * 0.22f
        val innerQuietRight = centerRegion.right - centerRegion.width() * 0.22f
        val innerQuietTop = centerRegion.top + centerRegion.height() * 0.28f
        val innerQuietBottom = centerRegion.bottom - centerRegion.height() * 0.25f

        val (finalScale, finalAlpha) = if (
            x in innerQuietLeft..innerQuietRight &&
            y in innerQuietTop..innerQuietBottom
        ) {
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

    private fun randomOuterPoint(
        random: Random,
        centerRegion: RectF,
    ): DecorationPoint {
        var x: Float
        var y: Float

        do {
            val candidateX = random.nextFloat()
            val candidateY = random.nextFloat()
            x = candidateX
            y = candidateY
        } while (x in centerRegion.left..centerRegion.right && y in centerRegion.top..centerRegion.bottom)

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
        hasBackgroundImage: Boolean,
    ): List<DecorationPoint> {
        val distribution = decorationDistribution(hasBackgroundImage)
        val totalWeight = distribution.centerWeight + distribution.outerWeight
        val centerCount = if (totalCount <= 1) {
            0
        } else {
            ((totalCount * distribution.centerWeight.toFloat()) / totalWeight)
                .roundToInt()
                .coerceIn(1, totalCount - 1)
        }
        val outerCount = totalCount - centerCount

        val centerPoints = List(centerCount) {
            randomCenterPoint(random, distribution.centerRegion)
        }
        val outerPoints = List(outerCount) {
            randomOuterPoint(random, distribution.centerRegion)
        }

        return (centerPoints + outerPoints).shuffled(random)
    }

    suspend fun render(
        context: Context,
        event: DayEvent,
        today: LocalDate,
        palette: MemoryImagePalette,
        template: MemoryImageTemplate,
        glassStyle: GlassExportStyle? = null,
    ): Result<Bitmap> {
        return try {
            val bitmap = withContext(Dispatchers.Default) {
                renderInternal(
                    context = context.applicationContext,
                    event = event,
                    today = today,
                    palette = palette,
                    template = template,
                    glassStyle = glassStyle,
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
        glassStyle: GlassExportStyle?,
    ): Bitmap {
        val imageReference = event.backgroundImage
        val imageFile = imageReference
            ?.let { reference ->
                LocalImageStore(context).fileFor(reference.fileName)
            }

        val hasBackgroundImage =
            imageFile?.isFile == true &&
                imageReference != null

        val memoryImageLayout = if (hasBackgroundImage) {
            memoryImageLayout(checkNotNull(imageReference))
        } else {
            memoryImageLayout(null)
        }
        val outputAspectRatio = memoryImageLayout.outputAspectRatio

        val outputSize = memoryImageOutputSize(outputAspectRatio)
        val width = outputSize.width
        val height = outputSize.height
        val isLandscape = memoryImageLayout.isLandscape

        val output = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(output)
        var sourceBitmap: Bitmap? = null

        try {
            val random = Random.Default

            if (hasBackgroundImage) {
                sourceBitmap = imageFile?.let(::decodeBackgroundImage)
            }

            val baseSize = min(width, height).toFloat()
            val canvasRect = RectF(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
            )
            val contentInsetX = width * if (isLandscape) 0.075f else 0.065f
            val contentInsetTop = height * if (isLandscape) 0.105f else 0.085f
            val contentInsetBottom = height * if (isLandscape) 0.07f else 0.06f
            val cardRect = RectF(
                contentInsetX,
                contentInsetTop,
                width - contentInsetX,
                height - contentInsetBottom,
            )
            val cardRadius = min(cardRect.width(), cardRect.height()) * 0.055f
            val cardBorderWidth = baseSize * 0.002f
            val cardPath = Path().apply {
                addRoundRect(
                    cardRect,
                    cardRadius,
                    cardRadius,
                    Path.Direction.CW,
                )
            }
            val contentRect = RectF(
                cardRect.left + cardRect.width() * 0.082f,
                cardRect.top + cardRect.height() * 0.1f,
                cardRect.right - cardRect.width() * 0.082f,
                cardRect.bottom - cardRect.height() * 0.09f,
            )

            drawBackdrop(
                canvas = canvas,
                canvasRect = canvasRect,
                palette = palette,
                template = template,
                hasBackgroundImage = hasBackgroundImage,
                random = random,
                glassStyle = glassStyle,
            )
            if (!hasBackgroundImage && glassStyle != null) {
                drawGlassCardShadowLayer(
                    canvas = canvas,
                    cardRect = cardRect,
                    cardRadius = cardRadius,
                    style = glassStyle,
                    baseSize = baseSize,
                )
            } else {
                drawCardShadowLayer(
                    canvas = canvas,
                    cardRect = cardRect,
                    cardRadius = cardRadius,
                    palette = palette,
                    baseSize = baseSize,
                )
            }
            drawMiddleLayerText(
                canvas = canvas,
                canvasRect = canvasRect,
                cardRect = cardRect,
                palette = palette,
                isLandscape = isLandscape,
                today = today,
            )

            val cardSaveCount = canvas.save()
            canvas.clipPath(cardPath)
            drawCardSurface(
                canvas = canvas,
                cardRect = cardRect,
                palette = palette,
                hasBackgroundImage = hasBackgroundImage,
                glassStyle = glassStyle,
            )

            if (sourceBitmap != null && imageReference != null) {
                drawFocusedCenterCrop(
                    canvas = canvas,
                    bitmap = sourceBitmap,
                    destination = cardRect,
                    image = imageReference,
                )
            }

            drawCardOverlay(
                canvas = canvas,
                cardRect = cardRect,
                palette = palette,
                hasBackgroundImage = hasBackgroundImage,
                glassStyle = glassStyle,
            )

            drawMainContent(
                canvas = canvas,
                event = event,
                today = today,
                palette = palette,
                hasBackgroundImage = hasBackgroundImage,
                mainRect = contentRect,
            )
            canvas.restoreToCount(cardSaveCount)

            if (!hasBackgroundImage && glassStyle != null) {
                drawGlassCardDepth(
                    canvas = canvas,
                    cardRect = cardRect,
                    cardRadius = cardRadius,
                    style = glassStyle,
                )
            } else {
                val borderColor = if (palette.isDark) {
                    withAlpha(palette.primary, 112)
                } else {
                    withAlpha(palette.primary, 132)
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = borderColor
                    this.style = Paint.Style.STROKE
                    strokeWidth = cardBorderWidth
                }
                canvas.drawRoundRect(
                    cardRect,
                    cardRadius,
                    cardRadius,
                    borderPaint,
                )
            }

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

    private fun memoryImageOutputSize(
        aspectRatio: Float,
    ): OutputSize {
        val safeAspectRatio = aspectRatio
            .takeIf { it.isFinite() && it > 0f }
            ?: DEFAULT_OUTPUT_ASPECT_RATIO

        return if (safeAspectRatio >= 1f) {
            OutputSize(
                width = OUTPUT_LONG_EDGE,
                height = (OUTPUT_LONG_EDGE / safeAspectRatio)
                    .roundToInt()
                    .coerceAtLeast(1),
            )
        } else {
            OutputSize(
                width = (OUTPUT_LONG_EDGE * safeAspectRatio)
                    .roundToInt()
                    .coerceAtLeast(1),
                height = OUTPUT_LONG_EDGE,
            )
        }
    }

    private fun drawBackdrop(
        canvas: Canvas,
        canvasRect: RectF,
        palette: MemoryImagePalette,
        template: MemoryImageTemplate,
        hasBackgroundImage: Boolean,
        random: Random,
        glassStyle: GlassExportStyle?,
    ) {
        if (glassStyle != null) {
            drawGlassBackdrop(canvas, canvasRect, glassStyle)
            return
        }

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                canvasRect.left,
                canvasRect.top,
                canvasRect.right,
                canvasRect.bottom,
                intArrayOf(
                    blendArgb(palette.background, palette.surface, if (palette.isDark) 0.16f else 0.1f),
                    palette.background,
                    blendArgb(palette.background, palette.primaryContainer, if (palette.isDark) 0.14f else 0.08f),
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(canvasRect, backgroundPaint)

        val glowRadius = min(canvasRect.width(), canvasRect.height())
        val primaryGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                canvasRect.left + canvasRect.width() * 0.18f,
                canvasRect.top + canvasRect.height() * 0.2f,
                glowRadius * 0.42f,
                withAlpha(palette.primary, if (palette.isDark) 48 else 34),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(canvasRect, primaryGlow)

        val secondaryGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                canvasRect.right - canvasRect.width() * 0.16f,
                canvasRect.bottom - canvasRect.height() * 0.18f,
                glowRadius * 0.48f,
                withAlpha(palette.primaryContainer, if (palette.isDark) 54 else 42),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(canvasRect, secondaryGlow)

        drawTemplateDecoration(
            canvas = canvas,
            cardRect = canvasRect,
            palette = palette,
            template = template,
            hasBackgroundImage = hasBackgroundImage,
        )
    }

    private fun drawGlassBackdrop(
        canvas: Canvas,
        canvasRect: RectF,
        style: GlassExportStyle,
    ) {
        GlassExportBackdrop.draw(canvas, canvasRect, style)
    }

    private fun drawCardShadowLayer(
        canvas: Canvas,
        cardRect: RectF,
        cardRadius: Float,
        palette: MemoryImagePalette,
        baseSize: Float,
    ) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(
                if (palette.isDark) palette.surface else Color.WHITE,
                if (palette.isDark) 38 else 120,
            )
            setShadowLayer(
                baseSize * 0.05f,
                0f,
                baseSize * 0.018f,
                withAlpha(Color.BLACK, if (palette.isDark) 118 else 64),
            )
        }
        val shadowRect = RectF(cardRect)
        canvas.drawRoundRect(shadowRect, cardRadius, cardRadius, shadowPaint)
    }

    private fun drawGlassCardShadowLayer(
        canvas: Canvas,
        cardRect: RectF,
        cardRadius: Float,
        style: GlassExportStyle,
        baseSize: Float,
    ) {
        val t = style.clarityFraction
        val depthAlpha = 0.20f + ((0.095f - 0.20f) * t)
        val tightAlpha = 0.16f + ((0.075f - 0.16f) * t)

        val depthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(Color.BLACK, 5)
            setShadowLayer(
                baseSize * 0.032f,
                0f,
                baseSize * 0.017f,
                withAlpha(Color.BLACK, (depthAlpha * 255f).roundToInt()),
            )
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, depthPaint)

        val tightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(Color.BLACK, 2)
            setShadowLayer(
                baseSize * 0.012f,
                0f,
                baseSize * 0.006f,
                withAlpha(Color.BLACK, (tightAlpha * 255f).roundToInt()),
            )
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, tightPaint)
    }

    private fun drawGlassCardDepth(
        canvas: Canvas,
        cardRect: RectF,
        cardRadius: Float,
        style: GlassExportStyle,
    ) {
        val scale = (cardRect.width() / 400f).coerceIn(1f, 4f)
        val t = style.clarityFraction
        val edgeAlpha = 0.35f + ((0.275f - 0.35f) * t)

        fun insetRect(amount: Float): RectF = RectF(
            cardRect.left + amount,
            cardRect.top + amount,
            cardRect.right - amount,
            cardRect.bottom - amount,
        )

        val outerInset = 0.52f * scale
        val outerRect = insetRect(outerInset)
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 1.04f * scale
            shader = LinearGradient(
                outerRect.left,
                outerRect.top,
                outerRect.right,
                outerRect.bottom,
                intArrayOf(
                    withAlpha(Color.WHITE, (edgeAlpha * 255f).roundToInt()),
                    withAlpha(Color.WHITE, (edgeAlpha * 0.72f * 255f).roundToInt()),
                    withAlpha(style.accent, (edgeAlpha * 0.46f * 255f).roundToInt()),
                    withAlpha(Color.WHITE, (edgeAlpha * 0.13f * 255f).roundToInt()),
                ),
                floatArrayOf(0f, 0.24f, 0.58f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(
            outerRect,
            (cardRadius - outerInset).coerceAtLeast(0f),
            (cardRadius - outerInset).coerceAtLeast(0f),
            outerPaint,
        )

        val refractInset = 1.28f * scale
        val refractRect = insetRect(refractInset)
        if (refractRect.width() > 0f && refractRect.height() > 0f) {
            val refractPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = 0.72f * scale
                shader = SweepGradient(
                    refractRect.centerX(),
                    refractRect.centerY(),
                    intArrayOf(
                        withAlpha(Color.WHITE, 11),
                        withAlpha(Color.WHITE, 48),
                        withAlpha(style.accent, 24),
                        Color.TRANSPARENT,
                        withAlpha(Color.BLACK, 18),
                        Color.TRANSPARENT,
                        withAlpha(Color.WHITE, 31),
                        withAlpha(Color.WHITE, 11),
                    ),
                    floatArrayOf(0f, 0.10f, 0.19f, 0.34f, 0.53f, 0.69f, 0.88f, 1f),
                )
            }
            canvas.drawRoundRect(
                refractRect,
                (cardRadius - refractInset).coerceAtLeast(0f),
                (cardRadius - refractInset).coerceAtLeast(0f),
                refractPaint,
            )
        }

        val innerInset = 1.88f * scale
        val innerRect = insetRect(innerInset)
        if (innerRect.width() > 0f && innerRect.height() > 0f) {
            val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = 0.56f * scale
                shader = LinearGradient(
                    innerRect.left,
                    innerRect.top,
                    innerRect.right,
                    innerRect.bottom,
                    intArrayOf(
                        withAlpha(Color.WHITE, if (style.isDark) 37 else 51),
                        Color.TRANSPARENT,
                        withAlpha(Color.BLACK, if (style.isDark) 29 else 17),
                    ),
                    floatArrayOf(0f, 0.48f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRoundRect(
                innerRect,
                (cardRadius - innerInset).coerceAtLeast(0f),
                (cardRadius - innerInset).coerceAtLeast(0f),
                innerPaint,
            )
        }
    }

    private fun drawMiddleLayerText(
        canvas: Canvas,
        canvasRect: RectF,
        cardRect: RectF,
        palette: MemoryImagePalette,
        isLandscape: Boolean,
        today: LocalDate,
    ) {
        val baseSize = min(canvasRect.width(), canvasRect.height())
        val topBand = RectF(
            canvasRect.left,
            canvasRect.top,
            canvasRect.right,
            cardRect.top,
        )
        val bottomBand = RectF(
            canvasRect.left,
            cardRect.bottom,
            canvasRect.right,
            canvasRect.bottom,
        )

        val brandColor = withAlpha(
            palette.onSurface,
            if (palette.isDark) 220 else 205,
        )
        val sloganColor = withAlpha(
            palette.onSurfaceVariant,
            if (palette.isDark) 185 else 170,
        )

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brandColor
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = min(
                baseSize * if (isLandscape) 0.027f else 0.032f,
                topBand.height() * 0.34f,
            )
            letterSpacing = 0.08f
        }
        val sloganPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = sloganColor
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = min(
                baseSize * if (isLandscape) 0.014f else 0.017f,
                bottomBand.height() * 0.24f,
            )
            letterSpacing = 0.04f
        }

        drawTopRightDateBadge(
            canvas = canvas,
            canvasRect = canvasRect,
            topBand = topBand,
            palette = palette,
            today = today,
            isLandscape = isLandscape,
        )

        val brandCenterY = topBand.centerY() + topBand.height() * 0.05f
        canvas.drawText(
            "The Day",
            canvasRect.centerX(),
            centeredBaseline(brandPaint, brandCenterY),
            brandPaint,
        )
        canvas.drawText(
            "记录值得记住的每一天",
            canvasRect.centerX(),
            centeredBaseline(sloganPaint, bottomBand.centerY()),
            sloganPaint,
        )
    }

    internal fun drawTopRightDateBadge(
        canvas: Canvas,
        canvasRect: RectF,
        topBand: RectF,
        palette: MemoryImagePalette,
        today: LocalDate,
        isLandscape: Boolean,
    ) {
        val dateText = String.format(
            Locale.US,
            "%04d/%02d/%02d",
            today.year,
            today.monthValue,
            today.dayOfMonth,
        )
        val baseSize = min(canvasRect.width(), canvasRect.height())
        val initialTextSize = min(
            baseSize * if (isLandscape) 0.14f else 0.14f,
            topBand.height() * 0.4f,
        )
        val rightMargin = canvasRect.width() * if (isLandscape) 0.05f else 0.045f
        val topMargin = topBand.height() * 0.28f
        val anchorX = canvasRect.right - rightMargin
        val maxDateWidth = canvasRect.width() * TOP_RIGHT_DATE_MAX_WIDTH_FRACTION
        val measuredInitialWidth = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = initialTextSize
        }.measureText(dateText)
        val textSize = if (measuredInitialWidth > maxDateWidth && measuredInitialWidth > 0f) {
            initialTextSize * (maxDateWidth / measuredInitialWidth)
        } else {
            initialTextSize
        }

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (palette.isDark) {
                Color.argb(110, 241, 209, 120)
            } else {
                Color.argb(72, 205, 156, 52)
            }
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            this.textSize = textSize
            setShadowLayer(
                textSize * 0.45f,
                0f,
                textSize * 0.1f,
                color,
            )
        }

        val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            this.textSize = textSize
            val dateWidth = measureText(dateText).coerceAtMost(maxDateWidth)
            shader = LinearGradient(
                anchorX - dateWidth,
                0f,
                anchorX,
                textSize,
                intArrayOf(
                    if (palette.isDark) Color.rgb(255, 244, 196) else Color.rgb(190, 141, 30),
                    if (palette.isDark) Color.rgb(232, 189, 93) else Color.rgb(229, 186, 71),
                    if (palette.isDark) Color.rgb(255, 232, 161) else Color.rgb(166, 119, 18),
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
        }

        val highlightPaint = Paint(goldPaint).apply {
            shader = null
            color = if (palette.isDark) {
                Color.argb(62, 255, 249, 226)
            } else {
                Color.argb(50, 255, 255, 255)
            }
        }

        val baseline = centeredBaseline(glowPaint, canvasRect.top + topMargin + textSize * 0.5f)
        canvas.drawText(dateText, anchorX, baseline, glowPaint)
        canvas.drawText(dateText, anchorX, baseline, goldPaint)
        canvas.drawText(dateText, anchorX, baseline - textSize * 0.055f, highlightPaint)
    }

    private fun drawCardSurface(
        canvas: Canvas,
        cardRect: RectF,
        palette: MemoryImagePalette,
        hasBackgroundImage: Boolean,
        glassStyle: GlassExportStyle?,
    ) {
        val colors = if (hasBackgroundImage) {
            intArrayOf(
                withAlpha(Color.WHITE, 14),
                Color.TRANSPARENT,
            )
        } else if (glassStyle != null) {
            val fillAlpha = glassStyle.surfaceFillAlpha
            intArrayOf(
                withAlpha(Color.WHITE, fillAlpha),
                withAlpha(glassStyle.accent, (fillAlpha * 0.18f).roundToInt()),
                withAlpha(Color.WHITE, (fillAlpha * 0.58f).roundToInt()),
            )
        } else {
            intArrayOf(
                withAlpha(palette.surface, if (palette.isDark) 28 else 24),
                withAlpha(palette.surface, if (palette.isDark) 12 else 8),
            )
        }
        val stops = if (!hasBackgroundImage && glassStyle != null) {
            floatArrayOf(0f, 0.56f, 1f)
        } else {
            floatArrayOf(0f, 1f)
        }
        val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cardRect.left,
                cardRect.top,
                cardRect.right,
                cardRect.bottom,
                colors,
                stops,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(cardRect, surfacePaint)
    }

    private fun drawCardOverlay(
        canvas: Canvas,
        cardRect: RectF,
        palette: MemoryImagePalette,
        hasBackgroundImage: Boolean,
        glassStyle: GlassExportStyle?,
    ) {
        if (!hasBackgroundImage && glassStyle != null) {
            val highlightRadius = min(cardRect.width(), cardRect.height()) * 0.52f
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(
                    cardRect.left + cardRect.width() * 0.16f,
                    cardRect.top + cardRect.height() * 0.12f,
                    highlightRadius,
                    intArrayOf(
                        withAlpha(Color.WHITE, if (glassStyle.isDark) 22 else 34),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(cardRect, highlightPaint)

            val transmissionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    cardRect.left,
                    cardRect.top,
                    cardRect.right,
                    cardRect.bottom,
                    intArrayOf(
                        Color.TRANSPARENT,
                        withAlpha(Color.BLACK, if (glassStyle.isDark) 17 else 9),
                    ),
                    floatArrayOf(0.46f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(cardRect, transmissionPaint)
            return
        }

        if (hasBackgroundImage) {
            val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    cardRect.top,
                    0f,
                    cardRect.bottom,
                    intArrayOf(
                        Color.argb(86, 0, 0, 0),
                        Color.argb(48, 0, 0, 0),
                        Color.argb(112, 0, 0, 0),
                        Color.argb(186, 0, 0, 0),
                    ),
                    floatArrayOf(0f, 0.22f, 0.58f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(cardRect, overlayPaint)
        } else {
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    cardRect.left,
                    cardRect.top,
                    cardRect.left,
                    cardRect.bottom,
                    intArrayOf(
                        withAlpha(Color.WHITE, if (palette.isDark) 26 else 72),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 0.32f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(cardRect, highlightPaint)
        }
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
        hasBackgroundImage: Boolean,
    ) {
        val random = Random.Default

        when (template) {
            MemoryImageTemplate.CIRCLES -> drawCircleTemplate(
                canvas = canvas,
                cardRect = cardRect,
                palette = palette,
                random = random,
                hasBackgroundImage = hasBackgroundImage,
            )

            MemoryImageTemplate.STARS -> drawStarTemplate(
                canvas = canvas,
                cardRect = cardRect,
                palette = palette,
                random = random,
                hasBackgroundImage = hasBackgroundImage,
            )

            MemoryImageTemplate.HEARTS -> drawHeartTemplate(
                canvas = canvas,
                cardRect = cardRect,
                palette = palette,
                random = random,
                hasBackgroundImage = hasBackgroundImage,
            )

            MemoryImageTemplate.METEORS -> drawMeteorTemplate(
                canvas = canvas,
                cardRect = cardRect,
                palette = palette,
                random = random,
                hasBackgroundImage = hasBackgroundImage,
            )

            MemoryImageTemplate.WAVES -> drawWaveTemplate(
                canvas = canvas,
                cardRect = cardRect,
                palette = palette,
                random = random,
                hasBackgroundImage = hasBackgroundImage,
            )

            MemoryImageTemplate.MINIMAL -> drawMinimalTemplate(
                canvas = canvas,
                cardRect = cardRect,
                palette = palette,
                random = random,
                hasBackgroundImage = hasBackgroundImage,
            )
        }
    }

    private fun drawCircleTemplate(
        canvas: Canvas,
        cardRect: RectF,
        palette: MemoryImagePalette,
        random: Random,
        hasBackgroundImage: Boolean,
    ) {
        val baseSize = minOf(cardRect.width(), cardRect.height())
        val profile = decorationAlphaProfile(palette.isDark)
        val totalCount = if (hasBackgroundImage) {
            14 + random.nextInt(4)
        } else {
            8 + random.nextInt(3)
        }
        val points = generateDecorationPoints(random, totalCount, hasBackgroundImage)
        val filledCount = (totalCount * 3f / 5f)
            .roundToInt()
            .coerceIn(0, totalCount)
        val outlineCount = totalCount - filledCount
        val circleStyles = MutableList(filledCount) { false }.apply {
            repeat(outlineCount) { add(true) }
            shuffle(random)
        }

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
            this.style = Paint.Style.STROKE
            strokeWidth = baseSize * 0.003f
        }

        var largeOuterCount = 0
        points.forEachIndexed { index, point ->
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
            val isStroke = circleStyles.getOrElse(index) { false }

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
        hasBackgroundImage: Boolean,
    ) {
        val baseSize = minOf(cardRect.width(), cardRect.height())
        val profile = decorationAlphaProfile(palette.isDark)
        val totalCount = if (hasBackgroundImage) {
            16 + random.nextInt(6)
        } else {
            12 + random.nextInt(4)
        }
        val points = generateDecorationPoints(random, totalCount, hasBackgroundImage)
        val filledCount = (totalCount * 3f / 5f)
            .roundToInt()
            .coerceIn(0, totalCount)
        val outlineCount = totalCount - filledCount
        val starStyles = MutableList(filledCount) { false }.apply {
            repeat(outlineCount) { add(true) }
            shuffle(random)
        }

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
            this.style = Paint.Style.STROKE
        }

        var largeOuterCount = 0
        points.forEachIndexed { index, point ->
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
            val isStroke = starStyles.getOrElse(index) { false }

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

    private fun drawHeartTemplate(
        canvas: Canvas,
        cardRect: RectF,
        palette: MemoryImagePalette,
        random: Random,
        hasBackgroundImage: Boolean,
    ) {
        val baseSize = minOf(cardRect.width(), cardRect.height())
        val profile = decorationAlphaProfile(palette.isDark)
        val totalCount = if (hasBackgroundImage) {
            20 + random.nextInt(6)
        } else {
            16 + random.nextInt(5)
        }
        val points = generateDecorationPoints(
            random = random,
            totalCount = totalCount,
            hasBackgroundImage = hasBackgroundImage,
        )
        val filledCount = (totalCount * 3f / 5f)
            .roundToInt()
            .coerceIn(0, totalCount)
        val outlineCount = totalCount - filledCount
        val heartStyles = MutableList(filledCount) { false }.apply {
            repeat(outlineCount) { add(true) }
            shuffle(random)
        }

        val roseColor = blendArgb(
            palette.primary,
            Color.rgb(236, 72, 118),
            if (palette.isDark) 0.34f else 0.44f,
        )
        val softRoseColor = blendArgb(
            palette.primaryContainer,
            Color.rgb(255, 164, 188),
            if (palette.isDark) 0.3f else 0.4f,
        )
        val primaryFillAlpha = random.nextInt(
            profile.primaryFillMin + 4,
            profile.primaryFillMax + 9,
        )
        val variantFillAlpha = random.nextInt(
            profile.variantFillMin + 3,
            profile.variantFillMax + 8,
        )
        val strokeAlpha = random.nextInt(
            profile.primaryStrokeMin + 3,
            profile.primaryStrokeMax + 10,
        )

        val primaryFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = roseColor
            this.style = Paint.Style.FILL
        }
        val softFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = softRoseColor
            this.style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = roseColor
            this.style = Paint.Style.STROKE
            strokeWidth = baseSize * 0.0026f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        var largeOuterCount = 0
        points.forEachIndexed { index, point ->
            val cx = cardRect.left + cardRect.width() * point.x
            val cy = cardRect.top + cardRect.height() * point.y
            val baseRadius = if (point.isCenter) {
                baseSize * (0.018f + random.nextFloat() * 0.026f)
            } else if (
                largeOuterCount < 2 &&
                random.nextFloat() < 0.22f
            ) {
                largeOuterCount++
                baseSize * (0.07f + random.nextFloat() * 0.025f)
            } else {
                baseSize * (0.028f + random.nextFloat() * 0.045f)
            }
            val radius = baseRadius * point.scale
            val isStroke = heartStyles.getOrElse(index) { false }
            val paint = if (isStroke) {
                Paint(strokePaint).apply {
                    alpha = (strokeAlpha * point.alphaScale)
                        .roundToInt()
                        .coerceIn(0, 255)
                }
            } else {
                val basePaint = if (random.nextFloat() < 0.62f) {
                    primaryFillPaint
                } else {
                    softFillPaint
                }
                val baseAlpha = if (basePaint === primaryFillPaint) {
                    primaryFillAlpha
                } else {
                    variantFillAlpha
                }
                Paint(basePaint).apply {
                    alpha = (baseAlpha * point.alphaScale)
                        .roundToInt()
                        .coerceIn(0, 255)
                    if (!point.isCenter && random.nextFloat() < 0.28f) {
                        setShadowLayer(
                            radius * 0.32f,
                            0f,
                            radius * 0.08f,
                            withAlpha(roseColor, 70),
                        )
                    }
                }
            }
            val tilt = (point.rotation % 50f) - 25f
            drawHeart(
                canvas = canvas,
                cx = cx,
                cy = cy,
                radius = radius,
                paint = paint,
                rotation = tilt,
            )
        }
    }

    private fun drawHeart(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        paint: Paint,
        rotation: Float,
    ) {
        val path = Path().apply {
            moveTo(0f, radius * 0.62f)
            cubicTo(
                -radius * 0.12f,
                radius * 0.42f,
                -radius * 0.82f,
                radius * 0.06f,
                -radius * 0.82f,
                -radius * 0.38f,
            )
            cubicTo(
                -radius * 0.82f,
                -radius * 0.82f,
                -radius * 0.28f,
                -radius * 0.98f,
                0f,
                -radius * 0.48f,
            )
            cubicTo(
                radius * 0.28f,
                -radius * 0.98f,
                radius * 0.82f,
                -radius * 0.82f,
                radius * 0.82f,
                -radius * 0.38f,
            )
            cubicTo(
                radius * 0.82f,
                radius * 0.06f,
                radius * 0.12f,
                radius * 0.42f,
                0f,
                radius * 0.62f,
            )
            close()
        }
        val saveCount = canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotation)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(saveCount)
    }

    private fun drawMeteorTemplate(
        canvas: Canvas,
        cardRect: RectF,
        palette: MemoryImagePalette,
        random: Random,
        hasBackgroundImage: Boolean,
    ) {
        val baseSize = minOf(cardRect.width(), cardRect.height())
        val profile = decorationAlphaProfile(palette.isDark)
        val totalCount = if (hasBackgroundImage) {
            20 + random.nextInt(4)
        } else {
            7 + random.nextInt(3)
        }
        val points = generateDecorationPoints(random, totalCount, hasBackgroundImage)

        val strokeAlpha = random.nextInt(profile.primaryStrokeMin, profile.primaryStrokeMax + 1)
        val headAlpha = random.nextInt(profile.variantFillMin, profile.variantFillMax + 1)

        val baseTailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
            strokeWidth = baseSize * 0.003f
            strokeCap = Paint.Cap.ROUND
            this.style = Paint.Style.STROKE
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
        hasBackgroundImage: Boolean,
    ) {
        val baseSize = minOf(cardRect.width(), cardRect.height())
        val profile = decorationAlphaProfile(palette.isDark)

        val basePrimaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
            strokeWidth = baseSize * 0.0022f
            this.style = Paint.Style.STROKE
        }
        val baseVariantPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurfaceVariant
            strokeWidth = baseSize * 0.0018f
            this.style = Paint.Style.STROKE
        }

        val width = cardRect.width()
        val height = cardRect.height()

        val totalCount = if (hasBackgroundImage) {
            20 + random.nextInt(4)
        } else {
            10 + random.nextInt(3)
        }
        val distribution = decorationDistribution(hasBackgroundImage)
        val centerCount = ((totalCount * distribution.centerWeight.toFloat()) /
            (distribution.centerWeight + distribution.outerWeight))
            .roundToInt()
            .coerceIn(1, totalCount - 1)
        val outerCount = totalCount - centerCount

        for (i in 0 until centerCount) {
            val path = Path()
            val y = if (hasBackgroundImage) {
                cardRect.top + height * (0.34f + random.nextFloat() * 0.32f)
            } else {
                cardRect.top + height * (0.28f + random.nextFloat() * 0.44f)
            }
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
        hasBackgroundImage: Boolean,
    ) {
        val baseSize = minOf(cardRect.width(), cardRect.height())
        val totalCount = if (hasBackgroundImage) {
            9 + random.nextInt(4)
        } else {
            7 + random.nextInt(3)
        }
        val points = generateDecorationPoints(random, totalCount, hasBackgroundImage)

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
                        this.style = Paint.Style.STROKE
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
                        this.style = Paint.Style.STROKE
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
                        this.style = Paint.Style.STROKE
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
                glowColor = palette.primary,
                centerY = centerY,
                mainRect = mainRect,
            )
        }

        val dateText = "${DateFormatting.longDate(displayDate, locale)} · " +
            DateFormatting.weekday(displayDate, locale)
        val dateReferenceSize = min(
            mainRect.width(),
            mainRect.height(),
        )

        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = dateReferenceSize * 0.055f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val dateMaxWidth = mainRect.width() * 0.88f
        fitTextSize(
            paint = datePaint,
            text = dateText,
            maxWidth = dateMaxWidth,
            minSize = dateReferenceSize * 0.032f,
        )
        val measuredDateWidth = datePaint.measureText(dateText)
        if (measuredDateWidth > dateMaxWidth) {
            datePaint.textSize *= dateMaxWidth / measuredDateWidth
        }

        val dateBaseline =
            mainRect.bottom - mainImageHeight * 0.07f - datePaint.descent()
        canvas.drawText(
            dateText,
            mainRect.centerX(),
            dateBaseline,
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
        glowColor: Int,
        centerY: Float,
        mainRect: RectF,
    ) {
        val sizeReference = min(
            mainRect.width(),
            mainRect.height(),
        )
        val isPortrait =
            mainRect.height() > mainRect.width() * 1.08f
        val numberSizeFactor = if (isPortrait) {
            0.24f
        } else {
            0.31f
        }

        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = sizeReference * numberSizeFactor
        }

        fitTextSize(
            paint = numberPaint,
            text = count,
            maxWidth = mainRect.width() * 0.64f,
            minSize = sizeReference * 0.14f,
        )

        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = secondaryColor
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = sizeReference * 0.05f
        }

        val gap = sizeReference * 0.025f
        val numberWidth = numberPaint.measureText(count)
        val unitWidth = unitPaint.measureText("天")
        val totalWidth = numberWidth + gap + unitWidth
        val startX = mainRect.centerX() - totalWidth / 2f
        val numberBaseline = centeredBaseline(
            numberPaint,
            centerY,
        )

        val innerGlowColor = blendArgb(
            glowColor,
            Color.WHITE,
            0.20f,
        )
        val outerGlowPaint = Paint(numberPaint).apply {
            this.color = withAlpha(glowColor, 28)
            setShadowLayer(
                numberPaint.textSize * 0.16f,
                0f,
                0f,
                withAlpha(glowColor, 205),
            )
        }
        val innerGlowPaint = Paint(numberPaint).apply {
            this.color = withAlpha(innerGlowColor, 22)
            setShadowLayer(
                numberPaint.textSize * 0.065f,
                0f,
                0f,
                withAlpha(innerGlowColor, 175),
            )
        }

        canvas.drawText(
            count,
            startX,
            numberBaseline,
            outerGlowPaint,
        )
        canvas.drawText(
            count,
            startX,
            numberBaseline,
            innerGlowPaint,
        )
        canvas.drawText(
            count,
            startX,
            numberBaseline,
            numberPaint,
        )

        val unitBaseline =
            numberBaseline + numberPaint.descent() - unitPaint.descent()
        canvas.drawText(
            "天",
            startX + numberWidth + gap,
            unitBaseline,
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

        val transform = image.transformFor(ImagePlacementTarget.DETAIL)
        val focusX = transform.focusX
        val focusY = transform.focusY

        val scale = maxOf(
            destination.width() / bitmap.width.toFloat(),
            destination.height() / bitmap.height.toFloat(),
        ) * transform.zoom
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

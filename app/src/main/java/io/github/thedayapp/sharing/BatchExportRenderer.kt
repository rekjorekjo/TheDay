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
import android.text.TextPaint
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.ImagePlacementTarget
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.media.LocalImageStore
import io.github.thedayapp.util.DateFormatting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private enum class ExportPageKind {
    LONG_IMAGE,
    LIST,
}

object BatchExportRenderer {
    private const val PAGE_WIDTH = 1440
    private const val MAX_PAGE_HEIGHT = 8192
    private const val MIN_PAGE_HEIGHT = 680
    private const val OUTER_MARGIN = 72f
    private const val HEADER_TOP = 90f
    private const val HEADER_HEIGHT = 205f
    private const val FOOTER_HEIGHT = 128f
    private const val CONTENT_GAP = 46f
    private const val LIST_ROW_HEIGHT = 132f
    private const val LIST_ROW_GAP = 10f

    fun estimateLongImagePageCount(events: List<DayEvent>): Int {
        return paginateLongCards(events).size
    }

    fun estimateListPageCount(events: List<DayEvent>): Int {
        return paginateListCards(events).size
    }

    suspend fun renderLongImagePages(
        context: Context,
        events: List<DayEvent>,
        today: LocalDate,
        locale: Locale,
        palette: MemoryImagePalette,
        template: MemoryImageTemplate,
        title: String?,
        glassStyle: GlassExportStyle? = null,
        onProgress: suspend (Float) -> Unit = {},
    ): Result<List<Bitmap>> {
        return renderPages(
            context = context,
            events = events,
            today = today,
            locale = locale,
            palette = palette,
            template = template,
            title = title,
            glassStyle = glassStyle,
            kind = ExportPageKind.LONG_IMAGE,
            onProgress = onProgress,
        )
    }

    suspend fun renderListPages(
        context: Context,
        events: List<DayEvent>,
        today: LocalDate,
        locale: Locale,
        palette: MemoryImagePalette,
        template: MemoryImageTemplate,
        title: String?,
        glassStyle: GlassExportStyle? = null,
        onProgress: suspend (Float) -> Unit = {},
    ): Result<List<Bitmap>> {
        return renderPages(
            context = context,
            events = events,
            today = today,
            locale = locale,
            palette = palette,
            template = template,
            title = title,
            glassStyle = glassStyle,
            kind = ExportPageKind.LIST,
            onProgress = onProgress,
        )
    }

    private suspend fun renderPages(
        context: Context,
        events: List<DayEvent>,
        today: LocalDate,
        locale: Locale,
        palette: MemoryImagePalette,
        template: MemoryImageTemplate,
        title: String?,
        glassStyle: GlassExportStyle?,
        kind: ExportPageKind,
        onProgress: suspend (Float) -> Unit,
    ): Result<List<Bitmap>> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val imageStore = LocalImageStore(context)
                val pageBatches = when (kind) {
                    ExportPageKind.LONG_IMAGE -> paginateLongCards(events)
                    ExportPageKind.LIST -> paginateListCards(events)
                }

                val renderedPages = mutableListOf<Bitmap>()
                for (index in pageBatches.indices) {
                    val pageEvents = pageBatches[index]
                    val pageHeight = pageHeightFor(
                        kind = kind,
                        events = pageEvents,
                    )
                    val pageBitmap = Bitmap.createBitmap(
                        PAGE_WIDTH,
                        pageHeight,
                        Bitmap.Config.ARGB_8888,
                    )
                    val canvas = Canvas(pageBitmap)

                    drawPageBackground(
                        canvas = canvas,
                        pageHeight = pageHeight,
                        palette = palette,
                        template = template,
                        seed = (events.hashCode() * 31 + index * 17).toLong(),
                        kind = kind,
                        glassStyle = glassStyle,
                    )
                    drawPageHeader(
                        canvas = canvas,
                        palette = palette,
                        title = title,
                        today = today,
                        showDate = index == 0,
                    )

                    when (kind) {
                        ExportPageKind.LONG_IMAGE -> drawLongPageContent(
                            canvas = canvas,
                            pageHeight = pageHeight,
                            pageEvents = pageEvents,
                            imageStore = imageStore,
                            today = today,
                            locale = locale,
                            palette = palette,
                            glassStyle = glassStyle,
                        )

                        ExportPageKind.LIST -> drawListPageContent(
                            canvas = canvas,
                            pageHeight = pageHeight,
                            pageEvents = pageEvents,
                            today = today,
                            locale = locale,
                            palette = palette,
                            glassStyle = glassStyle,
                        )
                    }

                    drawPageFooter(
                        canvas = canvas,
                        pageHeight = pageHeight,
                        palette = palette,
                        pageIndex = index,
                        pageCount = pageBatches.size,
                    )
                    renderedPages += pageBitmap
                    onProgress((index + 1f) / pageBatches.size.coerceAtLeast(1))
                }
                renderedPages
            }
        }
    }

    private fun contentStart(kind: ExportPageKind): Float {
        return when (kind) {
            ExportPageKind.LONG_IMAGE -> HEADER_TOP + HEADER_HEIGHT + 28f
            ExportPageKind.LIST -> HEADER_TOP + HEADER_HEIGHT + 18f
        }
    }

    private fun pageHeightFor(
        kind: ExportPageKind,
        events: List<DayEvent>,
    ): Int {
        val contentHeight = when (kind) {
            ExportPageKind.LONG_IMAGE -> {
                events.sumOf { event -> longCardHeight(event).toDouble() }.toFloat() +
                    CONTENT_GAP * (events.size - 1).coerceAtLeast(0)
            }

            ExportPageKind.LIST -> {
                LIST_ROW_HEIGHT * events.size +
                    LIST_ROW_GAP * (events.size - 1).coerceAtLeast(0)
            }
        }

        val desiredHeight = contentStart(kind) + contentHeight + FOOTER_HEIGHT
        return ceil(desiredHeight.toDouble())
            .toInt()
            .coerceIn(MIN_PAGE_HEIGHT, MAX_PAGE_HEIGHT)
    }

    private fun paginateLongCards(events: List<DayEvent>): List<List<DayEvent>> {
        if (events.isEmpty()) return listOf(emptyList())

        val pages = mutableListOf<MutableList<DayEvent>>()
        var currentPage = mutableListOf<DayEvent>()
        var usedHeight = contentStart(ExportPageKind.LONG_IMAGE)
        val maxContentBottom = MAX_PAGE_HEIGHT - FOOTER_HEIGHT

        events.forEach { event ->
            val cardHeight = longCardHeight(event)
            val requiredHeight = cardHeight + if (currentPage.isEmpty()) 0f else CONTENT_GAP

            if (
                currentPage.isNotEmpty() &&
                usedHeight + requiredHeight > maxContentBottom
            ) {
                pages += currentPage
                currentPage = mutableListOf()
                usedHeight = contentStart(ExportPageKind.LONG_IMAGE)
            }

            if (currentPage.isNotEmpty()) {
                usedHeight += CONTENT_GAP
            }
            currentPage += event
            usedHeight += cardHeight
        }

        if (currentPage.isNotEmpty()) {
            pages += currentPage
        }

        return pages
    }

    private fun paginateListCards(events: List<DayEvent>): List<List<DayEvent>> {
        if (events.isEmpty()) return listOf(emptyList())

        val pages = mutableListOf<MutableList<DayEvent>>()
        var currentPage = mutableListOf<DayEvent>()
        var usedHeight = contentStart(ExportPageKind.LIST)
        val maxContentBottom = MAX_PAGE_HEIGHT - FOOTER_HEIGHT

        events.forEach { event ->
            val requiredHeight = LIST_ROW_HEIGHT +
                if (currentPage.isEmpty()) 0f else LIST_ROW_GAP

            if (
                currentPage.isNotEmpty() &&
                usedHeight + requiredHeight > maxContentBottom
            ) {
                pages += currentPage
                currentPage = mutableListOf()
                usedHeight = contentStart(ExportPageKind.LIST)
            }

            if (currentPage.isNotEmpty()) {
                usedHeight += LIST_ROW_GAP
            }
            currentPage += event
            usedHeight += LIST_ROW_HEIGHT
        }

        if (currentPage.isNotEmpty()) {
            pages += currentPage
        }

        return pages
    }

    private fun longCardHeight(event: DayEvent): Float {
        val cardWidth = PAGE_WIDTH - OUTER_MARGIN * 2f
        val image = event.backgroundImage
        val imageAspectRatio = if (
            image != null &&
            image.width > 0 &&
            image.height > 0
        ) {
            image.width.toFloat() / image.height.toFloat()
        } else {
            1.50f
        }

        // The detail cards all share a width, but their heights follow each
        // image ratio. These are the same 0.60..1.50 bounds used by the detail
        // preview, so landscape and portrait cards remain visibly different.
        val boundedAspectRatio = imageAspectRatio
            .takeIf { ratio -> ratio.isFinite() && ratio > 0f }
            ?.coerceIn(0.60f, 1.50f)
            ?: 1.50f

        return cardWidth / boundedAspectRatio
    }

    private fun drawPageBackground(
        canvas: Canvas,
        pageHeight: Int,
        palette: MemoryImagePalette,
        template: MemoryImageTemplate,
        seed: Long,
        kind: ExportPageKind,
        glassStyle: GlassExportStyle?,
    ) {
        if (glassStyle != null) {
            drawGlassPageBackground(canvas, pageHeight, glassStyle)
            return
        }

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                PAGE_WIDTH.toFloat(),
                pageHeight.toFloat(),
                blendArgb(palette.background, palette.surface, 0.35f),
                blendArgb(palette.surface, palette.primaryContainer, 0.22f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), pageHeight.toFloat(), backgroundPaint)
        drawDecorations(
            canvas = canvas,
            template = template,
            palette = palette,
            seed = seed,
            dense = kind == ExportPageKind.LONG_IMAGE,
            pageHeight = pageHeight,
        )
    }

    private fun drawGlassPageBackground(
        canvas: Canvas,
        pageHeight: Int,
        style: GlassExportStyle,
    ) {
        GlassExportBackdrop.draw(
            canvas,
            RectF(0f, 0f, PAGE_WIDTH.toFloat(), pageHeight.toFloat()),
            style,
        )
    }

    private fun drawPageHeader(
        canvas: Canvas,
        palette: MemoryImagePalette,
        title: String?,
        today: LocalDate,
        showDate: Boolean,
    ) {
        val centerX = PAGE_WIDTH / 2f
        val headerTitle = title?.trim().orEmpty().ifBlank { "The Day" }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurface
            textSize = 66f
            typeface = android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL,
            )
            textAlign = Paint.Align.CENTER
            setShadowLayer(
                10f,
                0f,
                4f,
                withAlpha(palette.primary, if (palette.isDark) 80 else 56),
            )
        }

        // The title remains centered in the middle layer. The date is no
        // longer treated as a subtitle; it uses the exact gilded top-right
        // renderer from the single memorial image.
        canvas.drawText(
            headerTitle,
            centerX,
            HEADER_TOP + 92f,
            titlePaint,
        )

        if (showDate) {
            val canvasRect = RectF(
                0f,
                0f,
                PAGE_WIDTH.toFloat(),
                MAX_PAGE_HEIGHT.toFloat(),
            )
            val topBand = RectF(
                0f,
                0f,
                PAGE_WIDTH.toFloat(),
                HEADER_TOP + HEADER_HEIGHT,
            )
            EventMemoryImageRenderer.drawTopRightDateBadge(
                canvas = canvas,
                canvasRect = canvasRect,
                topBand = topBand,
                palette = palette,
                today = today,
                isLandscape = false,
            )
        }
    }

    private fun drawPageFooter(
        canvas: Canvas,
        pageHeight: Int,
        palette: MemoryImagePalette,
        pageIndex: Int,
        pageCount: Int,
    ) {
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(palette.onSurfaceVariant, if (palette.isDark) 225 else 205)
            textSize = 30f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        val text = if (pageIndex == pageCount - 1) {
            "记录值得记住的每一天"
        } else {
            "${pageIndex + 1} / $pageCount"
        }
        canvas.drawText(text, PAGE_WIDTH / 2f, pageHeight - 46f, footerPaint)
    }

    private fun drawLongPageContent(
        canvas: Canvas,
        pageHeight: Int,
        pageEvents: List<DayEvent>,
        imageStore: LocalImageStore,
        today: LocalDate,
        locale: Locale,
        palette: MemoryImagePalette,
        glassStyle: GlassExportStyle?,
    ) {
        if (pageEvents.isEmpty()) {
            val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(palette.onSurfaceVariant, 215)
                textSize = 42f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("还没有可导出的日子", PAGE_WIDTH / 2f, pageHeight / 2f, emptyPaint)
            return
        }

        var top = contentStart(ExportPageKind.LONG_IMAGE)
        pageEvents.forEach { event ->
            val rect = RectF(
                OUTER_MARGIN,
                top,
                PAGE_WIDTH - OUTER_MARGIN,
                top + longCardHeight(event),
            )
            drawLongCard(
                canvas = canvas,
                rect = rect,
                event = event,
                imageStore = imageStore,
                today = today,
                locale = locale,
                palette = palette,
                glassStyle = glassStyle,
            )
            top = rect.bottom + CONTENT_GAP
        }
    }

    private fun drawLongCard(
        canvas: Canvas,
        rect: RectF,
        event: DayEvent,
        imageStore: LocalImageStore,
        today: LocalDate,
        locale: Locale,
        palette: MemoryImagePalette,
        glassStyle: GlassExportStyle?,
    ) {
        val rounding = 42f
        val cardPath = Path().apply {
            addRoundRect(rect, rounding, rounding, Path.Direction.CW)
        }
        val bitmap = loadEventBitmap(imageStore, event)
        if (bitmap == null && glassStyle != null) {
            drawGlassExportCardShadow(
                canvas = canvas,
                rect = rect,
                radius = rounding,
                style = glassStyle,
                baseSize = min(rect.width(), rect.height()),
            )
        }
        val cardSurfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                bitmap != null -> withAlpha(palette.surface, if (palette.isDark) 28 else 18)
                glassStyle != null -> Color.TRANSPARENT
                else -> withAlpha(palette.surface, if (palette.isDark) 74 else 38)
            }
        }
        canvas.drawPath(cardPath, cardSurfacePaint)

        if (bitmap != null) {
            val saveCount = canvas.save()
            canvas.clipPath(cardPath)
            drawBitmapCover(
                canvas = canvas,
                bitmap = bitmap,
                rect = rect,
                focusX = event.backgroundImage?.transformFor(ImagePlacementTarget.DETAIL)?.focusX ?: 0.5f,
                focusY = event.backgroundImage?.transformFor(ImagePlacementTarget.DETAIL)?.focusY ?: 0.5f,
                zoom = event.backgroundImage?.transformFor(ImagePlacementTarget.DETAIL)?.zoom ?: 1f,
            )
            val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    rect.centerX(),
                    rect.top,
                    rect.centerX(),
                    rect.bottom,
                    intArrayOf(
                        withAlpha(Color.BLACK, 32),
                        withAlpha(Color.BLACK, 76),
                        withAlpha(Color.BLACK, 154),
                    ),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(rect, gradientPaint)
            canvas.restoreToCount(saveCount)
        } else if (glassStyle != null) {
            drawGlassExportCardSurface(
                canvas = canvas,
                rect = rect,
                radius = rounding,
                style = glassStyle,
            )
            drawGlassExportCardOverlay(
                canvas = canvas,
                rect = rect,
                radius = rounding,
                style = glassStyle,
            )
            drawGlassExportCardDepth(
                canvas = canvas,
                rect = rect,
                radius = rounding,
                style = glassStyle,
            )
        } else {
            val emptyBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                    blendArgb(palette.primaryContainer, palette.surface, 0.30f),
                    withAlpha(palette.surface, if (palette.isDark) 34 else 16),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawPath(cardPath, emptyBackgroundPaint)
        }

        val textColor = if (bitmap != null) Color.WHITE else palette.onSurface
        val subColor = if (bitmap != null) withAlpha(Color.WHITE, 226) else palette.onSurfaceVariant
        val delta = DayMath.signedDays(event, today)
        val effectiveDate = DayMath.effectiveDate(event, today)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 54f
            typeface = android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL,
            )
        }
        val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subColor
            textSize = 38f
            typeface = android.graphics.Typeface.create(
                "sans-serif",
                android.graphics.Typeface.NORMAL,
            )
        }
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subColor
            textSize = 34f
            typeface = android.graphics.Typeface.create(
                "sans-serif",
                android.graphics.Typeface.NORMAL,
            )
            textAlign = Paint.Align.CENTER
        }
        val bigNumberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 154f
            typeface = android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL,
            )
            setShadowLayer(
                28f,
                0f,
                0f,
                withAlpha(palette.primary, if (palette.isDark) 150 else 120),
            )
        }
        val dayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subColor
            textSize = 44f
            typeface = android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL,
            )
        }

        val centerX = rect.centerX()
        val contentCenterY = rect.centerY()
        val titleBaseline = contentCenterY - 128f
        val safeTitle = trimToWidth(
            event.title,
            titlePaint,
            rect.width() - 260f,
        )
        val statusText = when {
            delta > 0L -> "还有"
            delta < 0L -> "已经"
            else -> ""
        }
        val titleWidth = titlePaint.measureText(safeTitle)
        val statusWidth = if (statusText.isEmpty()) {
            0f
        } else {
            statusPaint.measureText(statusText) + 16f
        }
        val titleGroupLeft = centerX - (titleWidth + statusWidth) / 2f
        canvas.drawText(safeTitle, titleGroupLeft, titleBaseline, titlePaint)
        if (statusText.isNotEmpty()) {
            canvas.drawText(
                statusText,
                titleGroupLeft + titleWidth + 16f,
                titleBaseline,
                statusPaint,
            )
        }

        val numberBaseline = contentCenterY + 54f
        if (delta == 0L) {
            val todayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = 138f
                typeface = android.graphics.Typeface.create(
                    "sans-serif-medium",
                    android.graphics.Typeface.NORMAL,
                )
                textAlign = Paint.Align.CENTER
                setShadowLayer(
                    24f,
                    0f,
                    0f,
                    withAlpha(palette.primary, if (palette.isDark) 130 else 108),
                )
            }
            canvas.drawText("今天", centerX, numberBaseline, todayPaint)
        } else {
            val numberText = abs(delta).toString()
            val numberWidth = bigNumberPaint.measureText(numberText)
            val dayWidth = dayPaint.measureText("天")
            val totalWidth = numberWidth + 16f + dayWidth
            val numberLeft = centerX - totalWidth / 2f
            canvas.drawText(numberText, numberLeft, numberBaseline, bigNumberPaint)
            canvas.drawText(
                "天",
                numberLeft + numberWidth + 16f,
                numberBaseline - 12f,
                dayPaint,
            )
        }

        val dateText = "${DateFormatting.longDate(effectiveDate, locale)} · " +
            DateFormatting.weekday(effectiveDate, locale)
        canvas.drawText(
            dateText,
            centerX,
            contentCenterY + 142f,
            datePaint,
        )

        bitmap?.recycle()
    }

    private fun drawListPageContent(
        canvas: Canvas,
        pageHeight: Int,
        pageEvents: List<DayEvent>,
        today: LocalDate,
        locale: Locale,
        palette: MemoryImagePalette,
        glassStyle: GlassExportStyle?,
    ) {
        if (pageEvents.isEmpty()) {
            val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(palette.onSurfaceVariant, 215)
                textSize = 42f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("还没有可导出的日子", PAGE_WIDTH / 2f, pageHeight / 2f, emptyPaint)
            return
        }

        var top = contentStart(ExportPageKind.LIST)
        pageEvents.forEach { event ->
            val rect = RectF(
                OUTER_MARGIN,
                top,
                PAGE_WIDTH - OUTER_MARGIN,
                top + LIST_ROW_HEIGHT,
            )
            drawListRow(canvas, rect, event, today, locale, palette, glassStyle)
            top = rect.bottom + LIST_ROW_GAP
        }
    }

    private fun drawGlassExportCardShadow(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        style: GlassExportStyle,
        baseSize: Float,
    ) {
        val t = style.clarityFraction
        val depthAlpha = 0.20f + ((0.095f - 0.20f) * t)
        val tightAlpha = 0.16f + ((0.075f - 0.16f) * t)

        canvas.drawRoundRect(
            rect,
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(Color.BLACK, 5)
                setShadowLayer(
                    baseSize * 0.032f,
                    0f,
                    baseSize * 0.017f,
                    withAlpha(Color.BLACK, (depthAlpha * 255f).toInt()),
                )
            },
        )
        canvas.drawRoundRect(
            rect,
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(Color.BLACK, 2)
                setShadowLayer(
                    baseSize * 0.012f,
                    0f,
                    baseSize * 0.006f,
                    withAlpha(Color.BLACK, (tightAlpha * 255f).toInt()),
                )
            },
        )
    }

    private fun drawGlassExportCardSurface(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        style: GlassExportStyle,
    ) {
        val fillAlpha = style.surfaceFillAlpha
        val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    withAlpha(Color.WHITE, fillAlpha),
                    withAlpha(style.accent, (fillAlpha * 0.18f).toInt()),
                    withAlpha(Color.WHITE, (fillAlpha * 0.58f).toInt()),
                ),
                floatArrayOf(0f, 0.56f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, radius, radius, surfacePaint)
    }

    private fun drawGlassExportCardOverlay(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        style: GlassExportStyle,
    ) {
        val saveCount = canvas.save()
        canvas.clipPath(Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        })
        val highlightRadius = min(rect.width(), rect.height()) * 0.52f
        canvas.drawRect(
            rect,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(
                    rect.left + rect.width() * 0.16f,
                    rect.top + rect.height() * 0.12f,
                    highlightRadius,
                    intArrayOf(
                        withAlpha(Color.WHITE, if (style.isDark) 22 else 34),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
        canvas.drawRect(
            rect,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                    intArrayOf(
                        Color.TRANSPARENT,
                        withAlpha(Color.BLACK, if (style.isDark) 17 else 9),
                    ),
                    floatArrayOf(0.46f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
        canvas.restoreToCount(saveCount)
    }

    private fun drawGlassExportCardDepth(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        style: GlassExportStyle,
    ) {
        val scale = (rect.width() / 400f).coerceIn(1f, 4f)
        val t = style.clarityFraction
        val edgeAlpha = 0.35f + ((0.275f - 0.35f) * t)

        fun insetRect(amount: Float): RectF = RectF(
            rect.left + amount,
            rect.top + amount,
            rect.right - amount,
            rect.bottom - amount,
        )

        val outerInset = 0.52f * scale
        val outerRect = insetRect(outerInset)
        canvas.drawRoundRect(
            outerRect,
            (radius - outerInset).coerceAtLeast(0f),
            (radius - outerInset).coerceAtLeast(0f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = 1.04f * scale
                shader = LinearGradient(
                    outerRect.left,
                    outerRect.top,
                    outerRect.right,
                    outerRect.bottom,
                    intArrayOf(
                        withAlpha(Color.WHITE, (edgeAlpha * 255f).toInt()),
                        withAlpha(Color.WHITE, (edgeAlpha * 0.72f * 255f).toInt()),
                        withAlpha(style.accent, (edgeAlpha * 0.46f * 255f).toInt()),
                        withAlpha(Color.WHITE, (edgeAlpha * 0.13f * 255f).toInt()),
                    ),
                    floatArrayOf(0f, 0.24f, 0.58f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )

        val refractInset = 1.28f * scale
        val refractRect = insetRect(refractInset)
        if (refractRect.width() > 0f && refractRect.height() > 0f) {
            canvas.drawRoundRect(
                refractRect,
                (radius - refractInset).coerceAtLeast(0f),
                (radius - refractInset).coerceAtLeast(0f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
                },
            )
        }

        val innerInset = 1.88f * scale
        val innerRect = insetRect(innerInset)
        if (innerRect.width() > 0f && innerRect.height() > 0f) {
            canvas.drawRoundRect(
                innerRect,
                (radius - innerInset).coerceAtLeast(0f),
                (radius - innerInset).coerceAtLeast(0f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
                },
            )
        }
    }

    private fun drawListRow(
        canvas: Canvas,
        rect: RectF,
        event: DayEvent,
        today: LocalDate,
        locale: Locale,
        palette: MemoryImagePalette,
        glassStyle: GlassExportStyle?,
    ) {
        if (glassStyle != null) {
            drawGlassExportCardShadow(
                canvas = canvas,
                rect = rect,
                radius = 28f,
                style = glassStyle,
                baseSize = 390f,
            )
            drawGlassExportCardSurface(
                canvas = canvas,
                rect = rect,
                radius = 28f,
                style = glassStyle,
            )
            drawGlassExportCardOverlay(
                canvas = canvas,
                rect = rect,
                radius = 28f,
                style = glassStyle,
            )
            drawGlassExportCardDepth(
                canvas = canvas,
                rect = rect,
                radius = 28f,
                style = glassStyle,
            )
        } else {
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(palette.surface, if (palette.isDark) 214 else 240)
            }
            canvas.drawRoundRect(rect, 28f, 28f, cardPaint)
        }

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
        }
        val accentRect = RectF(rect.left + 14f, rect.top + 24f, rect.left + 24f, rect.bottom - 24f)
        canvas.drawRoundRect(accentRect, 99f, 99f, accentPaint)

        val delta = DayMath.signedDays(event, today)
        val effectiveDate = DayMath.effectiveDate(event, today)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurface
            textSize = 38f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onSurfaceVariant
            textSize = 28f
        }
        val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
            textSize = 32f
            textAlign = Paint.Align.RIGHT
            typeface = android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL,
            )
        }

        val left = rect.left + 50f
        val right = rect.right - 42f
        val statusText = relativeDayText(delta)
        val statusWidth = statusPaint.measureText(statusText)
        val titleMaxWidth = (right - left - statusWidth - 42f).coerceAtLeast(120f)

        canvas.drawText(
            trimToWidth(event.title, titlePaint, titleMaxWidth),
            left,
            rect.top + 50f,
            titlePaint,
        )

        val metaText = buildString {
            append(event.category.ifBlank { "未分类" })
            append(" · ")
            append(DateFormatting.compactDate(effectiveDate, locale))
        }
        canvas.drawText(metaText, left, rect.top + 94f, metaPaint)
        canvas.drawText(statusText, right, rect.centerY() + 10f, statusPaint)
    }

    private fun drawBitmapCover(
        canvas: Canvas,
        bitmap: Bitmap,
        rect: RectF,
        focusX: Float,
        focusY: Float,
        zoom: Float,
    ) {
        val baseScale = max(
            rect.width() / bitmap.width.toFloat(),
            rect.height() / bitmap.height.toFloat(),
        )
        val totalScale = baseScale * zoom.coerceIn(1f, 4f)
        val scaledWidth = bitmap.width * totalScale
        val scaledHeight = bitmap.height * totalScale
        val maxOffsetX = max(0f, scaledWidth - rect.width())
        val maxOffsetY = max(0f, scaledHeight - rect.height())
        val left = rect.left - maxOffsetX * focusX.coerceIn(0f, 1f)
        val top = rect.top - maxOffsetY * focusY.coerceIn(0f, 1f)

        val matrix = Matrix().apply {
            postScale(totalScale, totalScale)
            postTranslate(left, top)
        }
        canvas.drawBitmap(bitmap, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
    }

    private fun loadEventBitmap(
        imageStore: LocalImageStore,
        event: DayEvent,
    ): Bitmap? {
        val image = event.backgroundImage ?: return null
        val file = imageStore.fileFor(image.fileName) ?: return null
        return decodeScaledBitmap(file, 2400)
    }

    private fun decodeScaledBitmap(
        file: File,
        maxLongEdge: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longEdge = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (
            sample <= Int.MAX_VALUE / 2 &&
            longEdge / (sample * 2) >= maxLongEdge
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun drawDecorations(
        canvas: Canvas,
        template: MemoryImageTemplate,
        palette: MemoryImagePalette,
        seed: Long,
        dense: Boolean,
        pageHeight: Int,
    ) {
        when (template) {
            MemoryImageTemplate.CIRCLES -> drawCircleDecorations(canvas, palette, seed, dense, pageHeight)
            MemoryImageTemplate.STARS -> drawStarDecorations(canvas, palette, seed, dense, pageHeight)
            MemoryImageTemplate.HEARTS -> drawHeartDecorations(canvas, palette, seed, dense, pageHeight)
            MemoryImageTemplate.METEORS -> drawMeteorDecorations(canvas, palette, seed, dense, pageHeight)
            MemoryImageTemplate.WAVES -> drawWaveDecorations(canvas, palette, seed, dense, pageHeight)
            MemoryImageTemplate.MINIMAL -> drawMinimalDecorations(canvas, palette, seed, pageHeight)
        }
    }

    private fun drawCircleDecorations(canvas: Canvas, palette: MemoryImagePalette, seed: Long, dense: Boolean, pageHeight: Int) {
        val random = Random(seed.toInt())
        repeat(if (dense) 44 else 28) {
            val radius = 12f + random.nextFloat() * 46f
            val x = random.nextFloat() * PAGE_WIDTH
            val y = random.nextFloat() * pageHeight
            val filled = random.nextFloat() < 0.6f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
                strokeWidth = 3f + random.nextFloat() * 2.2f
                color = if (filled) {
                    withAlpha(if (random.nextBoolean()) palette.primary else palette.primaryContainer, 34 + random.nextInt(48))
                } else {
                    withAlpha(if (random.nextBoolean()) palette.primary else palette.onSurfaceVariant, 58 + random.nextInt(46))
                }
            }
            canvas.drawCircle(x, y, radius, paint)
        }
    }

    private fun drawStarDecorations(canvas: Canvas, palette: MemoryImagePalette, seed: Long, dense: Boolean, pageHeight: Int) {
        val random = Random(seed.toInt())
        repeat(if (dense) 34 else 22) {
            val cx = random.nextFloat() * PAGE_WIDTH
            val cy = random.nextFloat() * pageHeight
            val radius = 14f + random.nextFloat() * 30f
            val filled = random.nextFloat() < 0.6f
            val path = polygonStarPath(cx, cy, radius, radius * 0.44f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
                strokeWidth = 3f
                color = if (filled) {
                    withAlpha(if (random.nextBoolean()) palette.primary else palette.primaryContainer, 36 + random.nextInt(44))
                } else {
                    withAlpha(palette.onSurfaceVariant, 72 + random.nextInt(40))
                }
            }
            canvas.save()
            canvas.rotate(random.nextFloat() * 180f, cx, cy)
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }

    private fun drawHeartDecorations(canvas: Canvas, palette: MemoryImagePalette, seed: Long, dense: Boolean, pageHeight: Int) {
        val random = Random(seed.toInt())
        repeat(if (dense) 36 else 24) {
            val size = 18f + random.nextFloat() * 36f
            val x = random.nextFloat() * PAGE_WIDTH
            val y = random.nextFloat() * pageHeight
            val filled = random.nextFloat() < 0.6f
            val path = heartPath(x, y, size)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
                strokeWidth = 3f
                color = if (filled) {
                    withAlpha(if (random.nextBoolean()) palette.primary else palette.primaryContainer, 36 + random.nextInt(46))
                } else {
                    withAlpha(if (random.nextBoolean()) palette.primary else palette.onSurfaceVariant, 76 + random.nextInt(44))
                }
            }
            canvas.save()
            canvas.rotate(random.nextFloat() * 28f - 14f, x, y)
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }

    private fun drawMeteorDecorations(canvas: Canvas, palette: MemoryImagePalette, seed: Long, dense: Boolean, pageHeight: Int) {
        val random = Random(seed.toInt())
        repeat(if (dense) 18 else 12) {
            val startX = random.nextFloat() * PAGE_WIDTH
            val startY = random.nextFloat() * pageHeight
            val length = 120f + random.nextFloat() * 220f
            val angle = (-40f + random.nextFloat() * 20f) * PI.toFloat() / 180f
            val endX = startX + cos(angle) * length
            val endY = startY + sin(angle) * length
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 4f + random.nextFloat() * 3f
                strokeCap = Paint.Cap.ROUND
                shader = LinearGradient(
                    startX,
                    startY,
                    endX,
                    endY,
                    withAlpha(palette.primary, 0),
                    withAlpha(if (random.nextBoolean()) palette.primary else palette.onPrimaryContainer, 140),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawLine(startX, startY, endX, endY, paint)
            canvas.drawCircle(endX, endY, 6f + random.nextFloat() * 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(palette.primary, 160)
            })
        }
        drawStarDecorations(canvas, palette, seed + 41, dense = false, pageHeight = pageHeight)
    }

    private fun drawWaveDecorations(canvas: Canvas, palette: MemoryImagePalette, seed: Long, dense: Boolean, pageHeight: Int) {
        val random = Random(seed.toInt())
        repeat(if (dense) 20 else 14) {
            val left = random.nextFloat() * (PAGE_WIDTH - 240f)
            val top = random.nextFloat() * pageHeight
            val width = 120f + random.nextFloat() * 200f
            val amplitude = 10f + random.nextFloat() * 18f
            val path = Path().apply {
                moveTo(left, top)
                val step = width / 4f
                for (i in 0 until 4) {
                    val midX = left + step * (i + 0.5f)
                    val endX = left + step * (i + 1f)
                    val direction = if (i % 2 == 0) -1f else 1f
                    quadTo(midX, top + amplitude * direction, endX, top)
                }
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = withAlpha(if (random.nextBoolean()) palette.primary else palette.onSurfaceVariant, 84 + random.nextInt(46))
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawMinimalDecorations(canvas: Canvas, palette: MemoryImagePalette, seed: Long, pageHeight: Int) {
        val random = Random(seed.toInt())
        repeat(12) {
            val x = random.nextFloat() * PAGE_WIDTH
            val y = random.nextFloat() * pageHeight
            canvas.drawCircle(
                x,
                y,
                6f + random.nextFloat() * 12f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(if (random.nextBoolean()) palette.primary else palette.onSurfaceVariant, 52 + random.nextInt(34))
                },
            )
        }
    }

    private fun polygonStarPath(cx: Float, cy: Float, outerRadius: Float, innerRadius: Float): Path {
        val path = Path()
        for (index in 0 until 10) {
            val angle = (-90f + index * 36f) * PI.toFloat() / 180f
            val radius = if (index % 2 == 0) outerRadius else innerRadius
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun heartPath(cx: Float, cy: Float, size: Float): Path {
        val path = Path()
        path.moveTo(cx, cy + size * 0.9f)
        path.cubicTo(
            cx - size * 1.2f,
            cy + size * 0.2f,
            cx - size * 1.2f,
            cy - size * 0.7f,
            cx,
            cy - size * 0.12f,
        )
        path.cubicTo(
            cx + size * 1.2f,
            cy - size * 0.7f,
            cx + size * 1.2f,
            cy + size * 0.2f,
            cx,
            cy + size * 0.9f,
        )
        path.close()
        return path
    }

    private fun relativeDayText(delta: Long): String {
        return when {
            delta > 0L -> "还有 ${abs(delta)} 天"
            delta < 0L -> "已经 ${abs(delta)} 天"
            else -> "今天"
        }
    }

    private fun trimToWidth(text: String, paint: TextPaint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        if (maxWidth <= 0f) return ""
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) > maxWidth) {
            end -= 1
        }
        return text.substring(0, end.coerceAtLeast(1)) + "…"
    }

    private fun blendArgb(from: Int, to: Int, fraction: Float): Int {
        val clamped = fraction.coerceIn(0f, 1f)
        val fromA = Color.alpha(from)
        val fromR = Color.red(from)
        val fromG = Color.green(from)
        val fromB = Color.blue(from)
        val toA = Color.alpha(to)
        val toR = Color.red(to)
        val toG = Color.green(to)
        val toB = Color.blue(to)
        return Color.argb(
            (fromA + (toA - fromA) * clamped).toInt(),
            (fromR + (toR - fromR) * clamped).toInt(),
            (fromG + (toG - fromG) * clamped).toInt(),
            (fromB + (toB - fromB) * clamped).toInt(),
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }
}

package io.github.thedayapp.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import io.github.thedayapp.data.LocalImageReference
import io.github.thedayapp.media.LocalImageStore
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object WidgetImageRenderer {
    private const val MAX_LONG_EDGE = 1000
    private const val MAX_PIXELS = 600_000

    fun render(
        context: Context,
        image: LocalImageReference,
        widthDp: Int,
        heightDp: Int,
    ): Bitmap? {
        val density = context.resources.displayMetrics.density
        val widthPx = max(1, (widthDp * density).roundToInt())
        val heightPx = max(1, (heightDp * density).roundToInt())

        val file = LocalImageStore(context.applicationContext).fileFor(image.fileName) ?: return null

        var decodedBitmap: Bitmap? = null
        var outputBitmap: Bitmap? = null

        try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            val sourceWidth = options.outWidth
            val sourceHeight = options.outHeight

            var targetWidth = widthPx
            var targetHeight = heightPx

            val longEdge = max(targetWidth, targetHeight)
            if (longEdge > MAX_LONG_EDGE) {
                val scale = MAX_LONG_EDGE.toFloat() / longEdge
                targetWidth = (targetWidth * scale).roundToInt()
                targetHeight = (targetHeight * scale).roundToInt()
            }

            val pixels = targetWidth * targetHeight
            if (pixels > MAX_PIXELS) {
                val scale = sqrt(MAX_PIXELS.toFloat() / pixels)
                targetWidth = (targetWidth * scale).roundToInt()
                targetHeight = (targetHeight * scale).roundToInt()
            }

            targetWidth = max(1, targetWidth)
            targetHeight = max(1, targetHeight)

            options.inJustDecodeBounds = false
            options.inSampleSize = calculateSampleSize(sourceWidth, sourceHeight, targetWidth, targetHeight)

            decodedBitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            if (decodedBitmap == null) return null

            val focusX = if (image.focusX.isFinite()) image.focusX.coerceIn(0f, 1f) else 0.5f
            val focusY = if (image.focusY.isFinite()) image.focusY.coerceIn(0f, 1f) else 0.5f

            outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)

            val outputScale = minOf(
                targetWidth.toFloat() / widthPx.toFloat(),
                targetHeight.toFloat() / heightPx.toFloat(),
            )
            val cornerRadius = 24f * density * outputScale

            val clipBounds = RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat())
            val clipPath = Path().apply {
                addRoundRect(clipBounds, cornerRadius, cornerRadius, Path.Direction.CW)
            }
            val saveCount = canvas.save()
            canvas.clipPath(clipPath)

            val targetAspectRatio = targetWidth.toFloat() / targetHeight
            val sourceAspectRatio = decodedBitmap.width.toFloat() / decodedBitmap.height

            val cropRect: RectF
            if (sourceAspectRatio > targetAspectRatio) {
                val cropHeight = decodedBitmap.height.toFloat()
                val cropWidth = cropHeight * targetAspectRatio
                val left = (decodedBitmap.width - cropWidth) * focusX
                cropRect = RectF(
                    left.coerceIn(0f, decodedBitmap.width - cropWidth),
                    0f,
                    left.coerceIn(0f, decodedBitmap.width - cropWidth) + cropWidth,
                    cropHeight,
                )
            } else {
                val cropWidth = decodedBitmap.width.toFloat()
                val cropHeight = cropWidth / targetAspectRatio
                val top = (decodedBitmap.height - cropHeight) * focusY
                cropRect = RectF(
                    0f,
                    top.coerceIn(0f, decodedBitmap.height - cropHeight),
                    cropWidth,
                    top.coerceIn(0f, decodedBitmap.height - cropHeight) + cropHeight,
                )
            }

            val paint = Paint(
                Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG,
            ).apply {
                isAntiAlias = true
                isFilterBitmap = true
            }
            canvas.drawBitmap(
                decodedBitmap,
                Rect(
                    cropRect.left.roundToInt(),
                    cropRect.top.roundToInt(),
                    cropRect.right.roundToInt(),
                    cropRect.bottom.roundToInt(),
                ),
                RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat()),
                paint,
            )

            val gradient = LinearGradient(
                0f,
                0f,
                0f,
                targetHeight.toFloat(),
                intArrayOf(
                    0x2E000000.toInt(),
                    0x61000000.toInt(),
                    0xAD000000.toInt(),
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = gradient
            }
            canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), gradientPaint)

            canvas.restoreToCount(saveCount)

            val result = outputBitmap
            outputBitmap = null
            return result
        } catch (exception: Exception) {
            Log.w("TheDayWidget", "Failed to render widget background", exception)
            return null
        } finally {
            decodedBitmap?.recycle()
            outputBitmap?.recycle()
        }
    }

    private fun calculateSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        var sampleSize = 1
        val halfWidth = sourceWidth / 2
        val halfHeight = sourceHeight / 2

        while (halfWidth / sampleSize >= targetWidth && halfHeight / sampleSize >= targetHeight) {
            sampleSize *= 2
        }

        return sampleSize
    }
}
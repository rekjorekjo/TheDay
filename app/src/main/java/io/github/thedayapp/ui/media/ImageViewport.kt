package io.github.thedayapp.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.thedayapp.data.ImageTransform
import kotlin.math.roundToInt

private data class SourceWindow(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

@Composable
fun LocalImageViewport(
    bitmap: ImageBitmap,
    transform: ImageTransform,
    modifier: Modifier = Modifier,
) {
    val safeTransform = transform.normalized()

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val sourceWindow = calculateSourceWindow(
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            viewportWidth = size.width,
            viewportHeight = size.height,
            transform = safeTransform,
        )

        val sourceLeft = sourceWindow.left
            .roundToInt()
            .coerceIn(0, bitmap.width - 1)
        val sourceTop = sourceWindow.top
            .roundToInt()
            .coerceIn(0, bitmap.height - 1)
        val sourceRight = (sourceWindow.left + sourceWindow.width)
            .roundToInt()
            .coerceIn(sourceLeft + 1, bitmap.width)
        val sourceBottom = (sourceWindow.top + sourceWindow.height)
            .roundToInt()
            .coerceIn(sourceTop + 1, bitmap.height)

        drawImage(
            image = bitmap,
            srcOffset = IntOffset(
                x = sourceLeft,
                y = sourceTop,
            ),
            srcSize = IntSize(
                width = sourceRight - sourceLeft,
                height = sourceBottom - sourceTop,
            ),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(
                width = size.width.roundToInt().coerceAtLeast(1),
                height = size.height.roundToInt().coerceAtLeast(1),
            ),
            filterQuality = FilterQuality.High,
        )
    }
}

@Composable
fun TransformableLocalImageViewport(
    bitmap: ImageBitmap,
    transform: ImageTransform,
    onTransformChange: (ImageTransform) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var liveTransform by remember { mutableStateOf(transform.normalized()) }
    val currentOnTransformChange by rememberUpdatedState(onTransformChange)

    LaunchedEffect(transform) {
        liveTransform = transform.normalized()
    }

    LocalImageViewport(
        bitmap = bitmap,
        transform = liveTransform,
        modifier = modifier
            .onSizeChanged { viewportSize = it }
            .pointerInput(bitmap, viewportSize) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    if (
                        viewportSize.width <= 0 ||
                        viewportSize.height <= 0
                    ) {
                        return@detectTransformGestures
                    }

                    val updatedTransform = transformAfterGesture(
                        current = liveTransform,
                        pan = pan,
                        zoomChange = zoomChange,
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        viewportWidth = viewportSize.width.toFloat(),
                        viewportHeight = viewportSize.height.toFloat(),
                    )
                    liveTransform = updatedTransform
                    currentOnTransformChange(updatedTransform)
                }
            },
    )
}

private fun transformAfterGesture(
    current: ImageTransform,
    pan: Offset,
    zoomChange: Float,
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Float,
    viewportHeight: Float,
): ImageTransform {
    val safeCurrent = current.normalized()
    val safeZoomChange = zoomChange
        .takeIf { it.isFinite() && it > 0f }
        ?: 1f
    val newZoom = (safeCurrent.zoom * safeZoomChange)
        .coerceIn(1f, 4f)

    val zoomed = safeCurrent.copy(zoom = newZoom)
    val window = calculateSourceWindow(
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        transform = zoomed,
    )

    val horizontalTravel = imageWidth.toFloat() - window.width
    val verticalTravel = imageHeight.toFloat() - window.height
    val sourcePixelsPerViewportPixelX = window.width / viewportWidth
    val sourcePixelsPerViewportPixelY = window.height / viewportHeight

    val newFocusX = if (horizontalTravel > 0.5f) {
        (
            zoomed.focusX -
                pan.x * sourcePixelsPerViewportPixelX / horizontalTravel
        ).coerceIn(0f, 1f)
    } else {
        0.5f
    }

    val newFocusY = if (verticalTravel > 0.5f) {
        (
            zoomed.focusY -
                pan.y * sourcePixelsPerViewportPixelY / verticalTravel
        ).coerceIn(0f, 1f)
    } else {
        0.5f
    }

    return ImageTransform(
        focusX = newFocusX,
        focusY = newFocusY,
        zoom = newZoom,
    ).normalized()
}

private fun calculateSourceWindow(
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Float,
    viewportHeight: Float,
    transform: ImageTransform,
): SourceWindow {
    val safeImageWidth = imageWidth.coerceAtLeast(1).toFloat()
    val safeImageHeight = imageHeight.coerceAtLeast(1).toFloat()
    val safeViewportWidth = viewportWidth.coerceAtLeast(1f)
    val safeViewportHeight = viewportHeight.coerceAtLeast(1f)
    val targetAspectRatio = safeViewportWidth / safeViewportHeight
    val sourceAspectRatio = safeImageWidth / safeImageHeight

    val baseWidth: Float
    val baseHeight: Float
    if (sourceAspectRatio > targetAspectRatio) {
        baseHeight = safeImageHeight
        baseWidth = baseHeight * targetAspectRatio
    } else {
        baseWidth = safeImageWidth
        baseHeight = baseWidth / targetAspectRatio
    }

    val safeTransform = transform.normalized()
    val windowWidth = (baseWidth / safeTransform.zoom)
        .coerceIn(1f, safeImageWidth)
    val windowHeight = (baseHeight / safeTransform.zoom)
        .coerceIn(1f, safeImageHeight)

    val maxLeft = (safeImageWidth - windowWidth).coerceAtLeast(0f)
    val maxTop = (safeImageHeight - windowHeight).coerceAtLeast(0f)

    return SourceWindow(
        left = maxLeft * safeTransform.focusX,
        top = maxTop * safeTransform.focusY,
        width = windowWidth,
        height = windowHeight,
    )
}

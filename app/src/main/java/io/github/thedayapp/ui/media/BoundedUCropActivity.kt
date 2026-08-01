package io.github.thedayapp.ui.media

import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.yalantis.ucrop.R as UCropR
import com.yalantis.ucrop.UCropActivity
import com.yalantis.ucrop.view.GestureCropImageView
import com.yalantis.ucrop.view.OverlayView
import com.yalantis.ucrop.view.UCropView
import kotlin.math.abs
import kotlin.math.hypot

/**
 * uCrop activity with a small, app-local interaction patch.
 *
 * Upstream freestyle mode limits the crop rectangle to the overlay view. On a
 * narrow image that can still let a crop corner enter the empty area outside
 * the transformed bitmap. This activity adds a transparent touch guard above
 * uCrop's normal overlay and clamps every corner/move gesture to the bitmap's
 * current transformed quadrilateral before forwarding it to uCrop.
 */
class BoundedUCropActivity : UCropActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uCropView = findViewById<UCropView>(UCropR.id.ucrop)
            ?: return
        val overlayView = uCropView.overlayView
        val cropImageView = uCropView.cropImageView

        val touchGuard = CropBoundsTouchGuard(
            context = this,
            overlayView = overlayView,
            cropImageView = cropImageView,
        ).apply {
            setPadding(
                overlayView.paddingLeft,
                overlayView.paddingTop,
                overlayView.paddingRight,
                overlayView.paddingBottom,
            )
            setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO,
            )
        }

        val sourceLayoutParams = overlayView.layoutParams
        val layoutParams = FrameLayout.LayoutParams(sourceLayoutParams)
        uCropView.addView(touchGuard, layoutParams)
    }
}

private class CropBoundsTouchGuard(
    context: Context,
    private val overlayView: OverlayView,
    private val cropImageView: GestureCropImageView,
) : View(context) {
    private enum class DragHandle {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT,
        MOVE,
    }

    private data class Point(
        val x: Float,
        val y: Float,
    )

    private val cornerTouchThreshold = resources.getDimensionPixelSize(
        UCropR.dimen.ucrop_default_crop_rect_corner_touch_threshold,
    ).toFloat()

    private var dragHandle: DragHandle? = null
    private var previousAcceptedPoint: Point? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> finishGesture(event)

            else -> dragHandle != null
        }
    }

    private fun handleDown(event: MotionEvent): Boolean {
        val cropRect = RectF(overlayView.cropViewRect)
        val handle = findDragHandle(
            cropRect = cropRect,
            x = event.x,
            y = event.y,
        ) ?: return false

        val handled = overlayView.onTouchEvent(event)
        if (!handled) {
            return false
        }

        dragHandle = handle
        previousAcceptedPoint = Point(event.x, event.y)
        return true
    }

    private fun handleMove(event: MotionEvent): Boolean {
        val handle = dragHandle ?: return false
        val previousPoint = previousAcceptedPoint ?: return false

        if (event.pointerCount != 1) {
            return true
        }

        val desiredPoint = Point(
            x = event.x.coerceIn(
                overlayView.paddingLeft.toFloat(),
                (overlayView.width - overlayView.paddingRight).toFloat(),
            ),
            y = event.y.coerceIn(
                overlayView.paddingTop.toFloat(),
                (overlayView.height - overlayView.paddingBottom).toFloat(),
            ),
        )

        val cropRect = RectF(overlayView.cropViewRect)
        val imagePolygon = currentImagePolygonInGuardCoordinates()

        val acceptedPoint = if (
            imagePolygon != null &&
            !isCandidateValid(
                cropRect = cropRect,
                handle = handle,
                previousPoint = previousPoint,
                candidatePoint = desiredPoint,
                imagePolygon = imagePolygon,
            )
        ) {
            findFarthestValidPoint(
                cropRect = cropRect,
                handle = handle,
                previousPoint = previousPoint,
                desiredPoint = desiredPoint,
                imagePolygon = imagePolygon,
            )
        } else {
            desiredPoint
        }

        val forwardedEvent = MotionEvent.obtain(event).apply {
            setLocation(acceptedPoint.x, acceptedPoint.y)
        }

        val handled = try {
            overlayView.onTouchEvent(forwardedEvent)
        } finally {
            forwardedEvent.recycle()
        }

        if (handled) {
            previousAcceptedPoint = acceptedPoint
        }

        return true
    }

    private fun finishGesture(event: MotionEvent): Boolean {
        val hadActiveGesture = dragHandle != null

        if (hadActiveGesture) {
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                val resetEvent = MotionEvent.obtain(event).apply {
                    setAction(MotionEvent.ACTION_UP)
                }
                try {
                    overlayView.onTouchEvent(resetEvent)
                } finally {
                    resetEvent.recycle()
                }
            } else {
                overlayView.onTouchEvent(event)
            }
        }

        dragHandle = null
        previousAcceptedPoint = null
        return hadActiveGesture
    }

    private fun findDragHandle(
        cropRect: RectF,
        x: Float,
        y: Float,
    ): DragHandle? {
        if (
            overlayView.freestyleCropMode ==
            OverlayView.FREESTYLE_CROP_MODE_DISABLE
        ) {
            return null
        }

        val corners = arrayOf(
            Point(cropRect.left, cropRect.top) to DragHandle.TOP_LEFT,
            Point(cropRect.right, cropRect.top) to DragHandle.TOP_RIGHT,
            Point(cropRect.right, cropRect.bottom) to DragHandle.BOTTOM_RIGHT,
            Point(cropRect.left, cropRect.bottom) to DragHandle.BOTTOM_LEFT,
        )

        val nearestCorner = corners
            .map { (point, handle) ->
                hypot(
                    (x - point.x).toDouble(),
                    (y - point.y).toDouble(),
                ) to handle
            }
            .minByOrNull { (distance, _) -> distance }

        if (
            nearestCorner != null &&
            nearestCorner.first < cornerTouchThreshold
        ) {
            return nearestCorner.second
        }

        return if (cropRect.contains(x, y)) {
            DragHandle.MOVE
        } else {
            null
        }
    }

    private fun findFarthestValidPoint(
        cropRect: RectF,
        handle: DragHandle,
        previousPoint: Point,
        desiredPoint: Point,
        imagePolygon: FloatArray,
    ): Point {
        var low = 0f
        var high = 1f

        repeat(BINARY_SEARCH_ITERATIONS) {
            val middle = (low + high) / 2f
            val candidatePoint = interpolate(
                from = previousPoint,
                to = desiredPoint,
                fraction = middle,
            )

            if (
                isCandidateValid(
                    cropRect = cropRect,
                    handle = handle,
                    previousPoint = previousPoint,
                    candidatePoint = candidatePoint,
                    imagePolygon = imagePolygon,
                )
            ) {
                low = middle
            } else {
                high = middle
            }
        }

        return interpolate(
            from = previousPoint,
            to = desiredPoint,
            fraction = low,
        )
    }

    private fun isCandidateValid(
        cropRect: RectF,
        handle: DragHandle,
        previousPoint: Point,
        candidatePoint: Point,
        imagePolygon: FloatArray,
    ): Boolean {
        val candidateRect = candidateCropRect(
            cropRect = cropRect,
            handle = handle,
            previousPoint = previousPoint,
            candidatePoint = candidatePoint,
        )

        if (
            candidateRect.width() <= 0f ||
            candidateRect.height() <= 0f
        ) {
            return false
        }

        return rectIsInsideConvexPolygon(
            rect = candidateRect,
            polygon = imagePolygon,
        )
    }

    private fun candidateCropRect(
        cropRect: RectF,
        handle: DragHandle,
        previousPoint: Point,
        candidatePoint: Point,
    ): RectF {
        return when (handle) {
            DragHandle.TOP_LEFT -> RectF(
                candidatePoint.x,
                candidatePoint.y,
                cropRect.right,
                cropRect.bottom,
            )

            DragHandle.TOP_RIGHT -> RectF(
                cropRect.left,
                candidatePoint.y,
                candidatePoint.x,
                cropRect.bottom,
            )

            DragHandle.BOTTOM_RIGHT -> RectF(
                cropRect.left,
                cropRect.top,
                candidatePoint.x,
                candidatePoint.y,
            )

            DragHandle.BOTTOM_LEFT -> RectF(
                candidatePoint.x,
                cropRect.top,
                cropRect.right,
                candidatePoint.y,
            )

            DragHandle.MOVE -> RectF(cropRect).apply {
                offset(
                    candidatePoint.x - previousPoint.x,
                    candidatePoint.y - previousPoint.y,
                )
            }
        }
    }

    private fun currentImagePolygonInGuardCoordinates(): FloatArray? {
        val drawable = cropImageView.drawable ?: return null
        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()

        if (
            !drawableWidth.isFinite() ||
            !drawableHeight.isFinite() ||
            drawableWidth <= 0f ||
            drawableHeight <= 0f
        ) {
            return null
        }

        val corners = floatArrayOf(
            0f,
            0f,
            drawableWidth,
            0f,
            drawableWidth,
            drawableHeight,
            0f,
            drawableHeight,
        )

        cropImageView.imageMatrix.mapPoints(corners)

        val imageLocation = IntArray(2)
        val guardLocation = IntArray(2)
        cropImageView.getLocationOnScreen(imageLocation)
        getLocationOnScreen(guardLocation)

        val offsetX = (imageLocation[0] - guardLocation[0]).toFloat()
        val offsetY = (imageLocation[1] - guardLocation[1]).toFloat()

        for (index in corners.indices step 2) {
            corners[index] += offsetX
            corners[index + 1] += offsetY
        }

        return corners
    }

    private fun rectIsInsideConvexPolygon(
        rect: RectF,
        polygon: FloatArray,
    ): Boolean {
        if (polygon.size != POLYGON_COORDINATE_COUNT) {
            return false
        }

        val rectCorners = floatArrayOf(
            rect.left,
            rect.top,
            rect.right,
            rect.top,
            rect.right,
            rect.bottom,
            rect.left,
            rect.bottom,
        )

        for (index in rectCorners.indices step 2) {
            if (
                !pointIsInsideConvexPolygon(
                    x = rectCorners[index],
                    y = rectCorners[index + 1],
                    polygon = polygon,
                )
            ) {
                return false
            }
        }

        return true
    }

    private fun pointIsInsideConvexPolygon(
        x: Float,
        y: Float,
        polygon: FloatArray,
    ): Boolean {
        var expectedSign = 0

        for (pointIndex in 0 until POLYGON_POINT_COUNT) {
            val nextPointIndex = (pointIndex + 1) % POLYGON_POINT_COUNT
            val startX = polygon[pointIndex * 2]
            val startY = polygon[pointIndex * 2 + 1]
            val endX = polygon[nextPointIndex * 2]
            val endY = polygon[nextPointIndex * 2 + 1]

            val crossProduct =
                (endX - startX) * (y - startY) -
                    (endY - startY) * (x - startX)

            if (abs(crossProduct) <= GEOMETRY_EPSILON) {
                continue
            }

            val sign = if (crossProduct > 0f) 1 else -1
            if (expectedSign == 0) {
                expectedSign = sign
            } else if (sign != expectedSign) {
                return false
            }
        }

        return true
    }

    private fun interpolate(
        from: Point,
        to: Point,
        fraction: Float,
    ): Point {
        return Point(
            x = from.x + (to.x - from.x) * fraction,
            y = from.y + (to.y - from.y) * fraction,
        )
    }

    private companion object {
        const val BINARY_SEARCH_ITERATIONS = 18
        const val POLYGON_POINT_COUNT = 4
        const val POLYGON_COORDINATE_COUNT = POLYGON_POINT_COUNT * 2
        const val GEOMETRY_EPSILON = 0.01f
    }
}

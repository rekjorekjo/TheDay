package io.github.thedayapp.ui.media

import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.yalantis.ucrop.R as UCropR
import com.yalantis.ucrop.UCropActivity
import com.yalantis.ucrop.view.OverlayView
import com.yalantis.ucrop.view.UCropView
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 为 uCrop 自由裁剪框补充四条边的拖动区域。边拖动会映射为对应角点手势并固定垂直坐标，
 * 从而实现常规矩形缩放，同时继续复用 uCrop 原有的裁剪、缩放、旋转和比例逻辑。
 */
class EdgeHandleUCropActivity : UCropActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uCropView = findViewById<UCropView>(UCropR.id.ucrop)
            ?: return
        val overlayView = uCropView.overlayView

        val edgeTouchLayer = CropEdgeTouchLayer(
            context = this,
            overlayView = overlayView,
        ).apply {
            setPadding(
                overlayView.paddingLeft,
                overlayView.paddingTop,
                overlayView.paddingRight,
                overlayView.paddingBottom,
            )
            importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val layoutParams = FrameLayout.LayoutParams(
            overlayView.layoutParams,
        )
        uCropView.addView(edgeTouchLayer, layoutParams)
    }
}

private class CropEdgeTouchLayer(
    context: Context,
    private val overlayView: OverlayView,
) : View(context) {
    private enum class Edge {
        LEFT,
        TOP,
        RIGHT,
        BOTTOM,
    }

    private data class Point(
        val x: Float,
        val y: Float,
    )

    private val touchThreshold = resources.getDimensionPixelSize(
        UCropR.dimen.ucrop_default_crop_rect_corner_touch_threshold,
    ).toFloat()

    private var activeEdge: Edge? = null
    private var initialCropRect: RectF? = null
    private var lastProjectedPoint: Point? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginEdgeDrag(event)
            MotionEvent.ACTION_MOVE -> continueEdgeDrag(event)
            MotionEvent.ACTION_UP -> finishEdgeDrag(event)
            MotionEvent.ACTION_CANCEL -> cancelEdgeDrag(event)
            else -> activeEdge != null
        }
    }

    private fun beginEdgeDrag(event: MotionEvent): Boolean {
        if (
            overlayView.freestyleCropMode ==
            OverlayView.FREESTYLE_CROP_MODE_DISABLE
        ) {
            return false
        }

        val cropRect = RectF(overlayView.cropViewRect)
        val edge = findTouchedEdge(
            cropRect = cropRect,
            x = event.x,
            y = event.y,
        ) ?: return false

        val cornerPoint = cornerPointForEdge(
            edge = edge,
            cropRect = cropRect,
        )
        val forwardedEvent = MotionEvent.obtain(event).apply {
            setLocation(cornerPoint.x, cornerPoint.y)
        }

        val handled = try {
            overlayView.onTouchEvent(forwardedEvent)
        } finally {
            forwardedEvent.recycle()
        }

        if (!handled) {
            return false
        }

        activeEdge = edge
        initialCropRect = cropRect
        lastProjectedPoint = cornerPoint
        return true
    }

    private fun continueEdgeDrag(event: MotionEvent): Boolean {
        val edge = activeEdge ?: return false
        val cropRect = initialCropRect ?: return false

        if (event.pointerCount != 1) {
            return true
        }

        val projectedPoint = projectedPointForEdge(
            edge = edge,
            cropRect = cropRect,
            x = event.x,
            y = event.y,
        )

        forwardEvent(
            source = event,
            action = MotionEvent.ACTION_MOVE,
            point = projectedPoint,
        )
        lastProjectedPoint = projectedPoint
        return true
    }

    private fun finishEdgeDrag(event: MotionEvent): Boolean {
        if (activeEdge == null) {
            return false
        }

        val point = lastProjectedPoint
            ?: Point(event.x, event.y)

        forwardEvent(
            source = event,
            action = MotionEvent.ACTION_UP,
            point = point,
        )
        clearGesture()
        return true
    }

    private fun cancelEdgeDrag(event: MotionEvent): Boolean {
        if (activeEdge == null) {
            return false
        }

        // OverlayView 只在 ACTION_UP 重置内部拖动状态，因此合成角点手势也用 ACTION_UP 正常收尾。
        val point = lastProjectedPoint
            ?: Point(event.x, event.y)

        forwardEvent(
            source = event,
            action = MotionEvent.ACTION_UP,
            point = point,
        )
        clearGesture()
        return true
    }

    private fun forwardEvent(
        source: MotionEvent,
        action: Int,
        point: Point,
    ) {
        val forwardedEvent = MotionEvent.obtain(source).apply {
            setAction(action)
            setLocation(point.x, point.y)
        }

        try {
            overlayView.onTouchEvent(forwardedEvent)
        } finally {
            forwardedEvent.recycle()
        }
    }

    private fun clearGesture() {
        activeEdge = null
        initialCropRect = null
        lastProjectedPoint = null
    }

    private fun findTouchedEdge(
        cropRect: RectF,
        x: Float,
        y: Float,
    ): Edge? {
        if (isNearCorner(cropRect, x, y)) {
            // 真正的角点拖动继续交给 uCrop 原生处理，其本身会固定另外两条边。
            return null
        }

        val candidates = buildList {
            if (y in cropRect.top..cropRect.bottom) {
                add(abs(x - cropRect.left) to Edge.LEFT)
                add(abs(x - cropRect.right) to Edge.RIGHT)
            }
            if (x in cropRect.left..cropRect.right) {
                add(abs(y - cropRect.top) to Edge.TOP)
                add(abs(y - cropRect.bottom) to Edge.BOTTOM)
            }
        }

        return candidates
            .minByOrNull { (distance, _) -> distance }
            ?.takeIf { (distance, _) -> distance <= touchThreshold }
            ?.second
    }

    private fun isNearCorner(
        cropRect: RectF,
        x: Float,
        y: Float,
    ): Boolean {
        val corners = arrayOf(
            Point(cropRect.left, cropRect.top),
            Point(cropRect.right, cropRect.top),
            Point(cropRect.right, cropRect.bottom),
            Point(cropRect.left, cropRect.bottom),
        )

        return corners.any { corner ->
            hypot(
                (x - corner.x).toDouble(),
                (y - corner.y).toDouble(),
            ) < touchThreshold
        }
    }

    private fun cornerPointForEdge(
        edge: Edge,
        cropRect: RectF,
    ): Point {
        return when (edge) {
            Edge.LEFT,
            Edge.TOP -> Point(cropRect.left, cropRect.top)

            Edge.RIGHT -> Point(cropRect.right, cropRect.top)
            Edge.BOTTOM -> Point(cropRect.left, cropRect.bottom)
        }
    }

    private fun projectedPointForEdge(
        edge: Edge,
        cropRect: RectF,
        x: Float,
        y: Float,
    ): Point {
        return when (edge) {
            Edge.LEFT -> Point(x, cropRect.top)
            Edge.TOP -> Point(cropRect.left, y)
            Edge.RIGHT -> Point(x, cropRect.top)
            Edge.BOTTOM -> Point(cropRect.left, y)
        }
    }
}

package io.github.thedayapp.ui.media

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.thedayapp.data.LocalImageReference

internal enum class EventImageCardType {
    HOME_HERO,
    DETAIL,
}

private const val HOME_HERO_MIN_ASPECT_RATIO = 0.80f
private const val DETAIL_MIN_ASPECT_RATIO = 0.60f
private const val HOME_HERO_PREVIEW_MAX_ASPECT_RATIO = 1.65f
private const val DETAIL_PREVIEW_MAX_ASPECT_RATIO = 1.50f

/**
 * 根据所选图片比例计算卡片最小高度。调用方把它作为下限而非固定高度，
 * 让文字内容仍可自然撑开；极端竖图会受最小宽高比限制，避免单张卡片占据过多纵向空间。
 */
internal fun adaptiveEventImageMinimumHeight(
    availableWidth: Dp,
    image: LocalImageReference?,
    type: EventImageCardType,
): Dp? {
    val imageAspectRatio = safeImageAspectRatio(image) ?: return null
    val widthValue = availableWidth.value

    if (!widthValue.isFinite() || widthValue <= 0f) {
        return null
    }

    val minimumAspectRatio = when (type) {
        EventImageCardType.HOME_HERO -> HOME_HERO_MIN_ASPECT_RATIO
        EventImageCardType.DETAIL -> DETAIL_MIN_ASPECT_RATIO
    }

    val boundedAspectRatio = imageAspectRatio.coerceAtLeast(minimumAspectRatio)
    return (widthValue / boundedAspectRatio).dp
}

/**
 * 为图片位置调整面板提供接近实际卡片的预览比例；宽图仍保留文字所需的安全卡片高度。
 */
internal fun adaptiveEventImagePreviewAspectRatio(
    image: LocalImageReference,
    type: EventImageCardType,
): Float {
    val minimumAspectRatio: Float
    val maximumAspectRatio: Float

    when (type) {
        EventImageCardType.HOME_HERO -> {
            minimumAspectRatio = HOME_HERO_MIN_ASPECT_RATIO
            maximumAspectRatio = HOME_HERO_PREVIEW_MAX_ASPECT_RATIO
        }

        EventImageCardType.DETAIL -> {
            minimumAspectRatio = DETAIL_MIN_ASPECT_RATIO
            maximumAspectRatio = DETAIL_PREVIEW_MAX_ASPECT_RATIO
        }
    }

    return safeImageAspectRatio(image)
        ?.coerceIn(minimumAspectRatio, maximumAspectRatio)
        ?: maximumAspectRatio
}

private fun safeImageAspectRatio(
    image: LocalImageReference?,
): Float? {
    if (image == null || image.width <= 0 || image.height <= 0) {
        return null
    }

    val ratio = image.width.toFloat() / image.height.toFloat()
    return ratio.takeIf { it.isFinite() && it > 0f }
}

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
 * Returns the minimum height needed to preserve the selected image ratio.
 *
 * The caller should apply this as a minimum rather than an exact height. That
 * lets the card's text content grow naturally when a very wide image would
 * otherwise make the card too short. Extremely tall images are capped by a
 * minimum supported aspect ratio so one card cannot occupy an unreasonable
 * amount of vertical space.
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
 * Gives the focus dialog a close approximation of the card shape users will
 * see. The upper bound represents the natural text-driven height of each card;
 * wider images keep that safe card shape instead of squeezing the text.
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

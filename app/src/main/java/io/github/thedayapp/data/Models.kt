package io.github.thedayapp.data

import java.time.LocalDate
import java.util.UUID

enum class RepeatMode {
    NONE,
    YEARLY,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class PaletteStyle {
    MIDNIGHT,
    CINNABAR,
    PINE,
    ANTIQUE_GOLD,
    BLOOM_PETAL,
    BLOOM_MIST,
    BLOOM_VERDANT,
    BLOOM_STONE,
    BLOOM_WHEAT,
    BLOOM_INK,
    BLOOM_AMBER,
    BLOOM_LAPIS,
    BLOOM_RIPPLE,
    BLOOM_CINNABAR,
    BLOOM_SAGE,
    BLOOM_SPRING,
}

enum class SortMode {
    SMART,
    DATE,
    TITLE,
    CREATED,
}

enum class SortDirection {
    ASCENDING,
    DESCENDING,
}

data class ImageTransform(
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val zoom: Float = 1f,
) {
    fun normalized(): ImageTransform {
        return ImageTransform(
            focusX = focusX.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f,
            focusY = focusY.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f,
            zoom = zoom.takeIf { it.isFinite() }?.coerceIn(1f, 4f) ?: 1f,
        )
    }
}

enum class ImagePlacementTarget {
    HOME,
    DETAIL,
}

/**
 * [fileName] points to the currently selected image range used by the UI.
 * [originalFileName] points to the retained uncropped source used when the
 * user chooses “重新裁剪”. The two screen transforms are non-destructive and
 * only describe how that image is framed inside each card.
 */
data class LocalImageReference(
    val fileName: String,
    val width: Int,
    val height: Int,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val originalFileName: String? = null,
    val homeTransform: ImageTransform = ImageTransform(),
    val detailTransform: ImageTransform = ImageTransform(),
) {
    fun transformFor(target: ImagePlacementTarget): ImageTransform {
        return when (target) {
            ImagePlacementTarget.HOME -> homeTransform.normalized()
            ImagePlacementTarget.DETAIL -> detailTransform.normalized()
        }
    }

    fun withTransform(
        target: ImagePlacementTarget,
        transform: ImageTransform,
    ): LocalImageReference {
        val safeTransform = transform.normalized()
        return when (target) {
            ImagePlacementTarget.HOME -> copy(homeTransform = safeTransform)
            ImagePlacementTarget.DETAIL -> copy(detailTransform = safeTransform)
        }
    }
}

data class NewEventDraft(
    val title: String = "",
    val date: LocalDate,
    val category: String = "",
    val note: String = "",
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val isPinned: Boolean = false,
    val reminderDaysBefore: Int? = null,
    val backgroundImage: LocalImageReference? = null,
)

data class DayAlbum(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val eventIds: List<String>,
    val coverEventId: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)
data class DayMilestone(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: LocalDate,
    val note: String = "",
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

data class DayEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: LocalDate,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val category: String = "",
    val note: String = "",
    val isPinned: Boolean = false,
    val reminderDaysBefore: Int? = null,
    val backgroundImage: LocalImageReference? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val paletteStyle: PaletteStyle = PaletteStyle.MIDNIGHT,
    val glassClarity: Int = 62,
    val backgroundMotionMode: String = "FLOW",
    val backgroundTexture: String = "DIAGONAL",
    val dynamicEdgeReflection: Boolean = true,
    val sortMode: SortMode = SortMode.SMART,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val showPastEvents: Boolean = true,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
)

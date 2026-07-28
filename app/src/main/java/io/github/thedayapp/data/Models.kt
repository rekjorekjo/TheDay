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

data class LocalImageReference(
    val fileName: String,
    val width: Int,
    val height: Int,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
)

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
    val sortMode: SortMode = SortMode.SMART,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val showPastEvents: Boolean = true,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
)

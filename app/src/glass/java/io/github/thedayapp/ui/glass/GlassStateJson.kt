package io.github.thedayapp.ui.glass

import android.content.Context
import io.github.thedayapp.BuildConfig
import io.github.thedayapp.data.AppSettings
import io.github.thedayapp.data.DayAlbum
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.DayMilestone
import io.github.thedayapp.data.ImageTransform
import io.github.thedayapp.data.LocalImageReference
import io.github.thedayapp.data.NewEventDraft
import io.github.thedayapp.data.RepeatMode
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.domain.EventOrdering
import io.github.thedayapp.device.HyperOsCompatibility
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

internal object GlassStateJson {
    fun snapshot(
        context: Context,
        state: TheDayState,
        isDark: Boolean,
        notificationGranted: Boolean,
        requestedEventId: String? = null,
        savedEventId: String? = null,
    ): String {
        val orderedEvents = EventOrdering.sort(
            events = state.events,
            mode = state.settings.sortMode,
            direction = state.settings.sortDirection,
            today = state.today,
        )
        val visibleEvents = orderedEvents.filter { event ->
            state.settings.showPastEvents || !DayMath.isPast(event, state.today)
        }
        val heroEvent = EventOrdering.heroEvent(visibleEvents, state.today)

        return JSONObject()
            .put("today", state.today.toString())
            .put("isDark", isDark)
            .put("notificationGranted", notificationGranted)
            .put("canPinWidget", !HyperOsCompatibility.shouldHideWidgetQuickAdd(context))
            .put("versionName", BuildConfig.VERSION_NAME)
            .put("edition", BuildConfig.EDITION)
            .put("requestedEventId", requestedEventId)
            .put("savedEventId", savedEventId)
            .put("heroEventId", heroEvent?.id)
            .put(
                "orderedEventIds",
                JSONArray().apply { visibleEvents.forEach { put(it.id) } },
            )
            .put("settings", settingsToJson(state.settings))
            .put(
                "newEventDraft",
                state.newEventDraft?.let { draftToJson(context, it) },
            )
            .put(
                "events",
                JSONArray().apply {
                    state.events.forEach { event ->
                        put(eventToJson(context, event, state.today))
                    }
                },
            )
            .put(
                "milestones",
                JSONArray().apply {
                    state.milestones.forEach { put(milestoneToJson(it)) }
                },
            )
            .put(
                "albums",
                JSONArray().apply {
                    state.albums.forEach { put(albumToJson(it)) }
                },
            )
            .put(
                "categoryCovers",
                JSONObject().apply {
                    state.categoryCovers.forEach { (name, image) ->
                        put(name, imageToJson(context, image))
                    }
                },
            )
            .toString()
    }

    fun parseEvent(raw: String, existing: DayEvent?): DayEvent {
        val json = JSONObject(raw)
        val id = json.optString("id").takeIf { it.isNotBlank() && it != "null" }
            ?: existing?.id
            ?: UUID.randomUUID().toString()
        val date = parseRequiredDate(json, existing?.date)

        return DayEvent(
            id = id,
            title = json.optString("title").trim(),
            date = date,
            repeatMode = enumOrDefault(
                json.optString("repeatMode"),
                existing?.repeatMode ?: RepeatMode.NONE,
            ),
            category = json.optString("category").trim(),
            note = json.optString("note").trim(),
            isPinned = json.optBoolean("isPinned", existing?.isPinned ?: false),
            reminderDaysBefore = if (
                json.isNull("reminderDaysBefore") || !json.has("reminderDaysBefore")
            ) {
                null
            } else {
                json.optInt("reminderDaysBefore").coerceAtLeast(0)
            },
            backgroundImage = json.optJSONObject("backgroundImage")?.let(::parseImage)
                ?: if (json.has("backgroundImage") && json.isNull("backgroundImage")) {
                    null
                } else {
                    existing?.backgroundImage
                },
            createdAtEpochMillis = json.optLong(
                "createdAtEpochMillis",
                existing?.createdAtEpochMillis ?: System.currentTimeMillis(),
            ),
        )
    }

    private fun parseRequiredDate(json: JSONObject, fallback: LocalDate?): LocalDate {
        val hasDateParts = json.has("dateYear") || json.has("dateMonth") || json.has("dateDay")
        if (hasDateParts) {
            if (!json.has("dateYear") || !json.has("dateMonth") || !json.has("dateDay")) {
                throw IllegalArgumentException("Incomplete event date")
            }
            return LocalDate.of(
                json.getInt("dateYear"),
                json.getInt("dateMonth"),
                json.getInt("dateDay"),
            )
        }

        val rawDate = json.optString("date").trim()
        if (rawDate.isNotEmpty()) {
            return LocalDate.parse(rawDate)
        }
        return fallback ?: throw IllegalArgumentException("Event date is required")
    }

    fun parseMilestone(raw: String, existing: DayMilestone?): DayMilestone {
        val json = JSONObject(raw)
        val date = runCatching { LocalDate.parse(json.optString("date")) }
            .getOrElse { existing?.date ?: LocalDate.now() }
        return DayMilestone(
            id = json.optString("id").takeIf { it.isNotBlank() && it != "null" }
                ?: existing?.id
                ?: UUID.randomUUID().toString(),
            title = json.optString("title").trim(),
            date = date,
            note = json.optString("note").trim(),
            createdAtEpochMillis = json.optLong(
                "createdAtEpochMillis",
                existing?.createdAtEpochMillis ?: System.currentTimeMillis(),
            ),
        )
    }

    fun parseNewEventDraft(raw: String, fallbackDate: LocalDate): NewEventDraft {
        val json = JSONObject(raw)
        val date = runCatching { parseRequiredDate(json, fallbackDate) }
            .getOrElse { fallbackDate }
        return NewEventDraft(
            title = json.optString("title").trim(),
            date = date,
            category = json.optString("category").trim(),
            note = json.optString("note").trim(),
            repeatMode = enumOrDefault(json.optString("repeatMode"), RepeatMode.NONE),
            isPinned = json.optBoolean("isPinned", false),
            reminderDaysBefore = if (
                json.isNull("reminderDaysBefore") || !json.has("reminderDaysBefore")
            ) {
                null
            } else {
                json.optInt("reminderDaysBefore").coerceAtLeast(0)
            },
            backgroundImage = json.optJSONObject("backgroundImage")?.let(::parseImage),
        )
    }

    fun parseAlbum(raw: String, existing: DayAlbum?): DayAlbum {
        val json = JSONObject(raw)
        val eventIdsJson = json.optJSONArray("eventIds") ?: JSONArray()
        val eventIds = buildList {
            for (index in 0 until eventIdsJson.length()) {
                eventIdsJson.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }.distinct()
        return DayAlbum(
            id = json.optString("id").takeIf { it.isNotBlank() && it != "null" }
                ?: existing?.id
                ?: UUID.randomUUID().toString(),
            title = json.optString("title").trim(),
            eventIds = eventIds,
            coverEventId = json.optString("coverEventId").takeIf { it.isNotBlank() },
            createdAtEpochMillis = json.optLong(
                "createdAtEpochMillis",
                existing?.createdAtEpochMillis ?: System.currentTimeMillis(),
            ),
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    fun parseSettings(raw: String, current: AppSettings): AppSettings {
        val json = JSONObject(raw)
        return current.copy(
            themeMode = enumOrDefault(json.optString("themeMode"), current.themeMode),
            paletteStyle = enumOrDefault(json.optString("paletteStyle"), current.paletteStyle),
            glassClarity = json.optInt("glassClarity", current.glassClarity).coerceIn(0, 100),
            backgroundMotionMode = json.optString("backgroundMotionMode")
                .takeIf { it in setOf("STATIC", "FLOW", "AURORA") }
                ?: if (json.has("dynamicBackground")) {
                    if (json.optBoolean("dynamicBackground", true)) "FLOW" else "STATIC"
                } else {
                    current.backgroundMotionMode
                },
            backgroundTexture = json.optString("backgroundTexture", current.backgroundTexture)
                .takeIf { it in setOf("NONE", "DIAGONAL", "WAVE", "STARS", "CONSTELLATION", "HEART") }
                ?: current.backgroundTexture,
            dynamicEdgeReflection = json.optBoolean("dynamicEdgeReflection", current.dynamicEdgeReflection),
            sortMode = enumOrDefault(json.optString("sortMode"), current.sortMode),
            sortDirection = enumOrDefault(
                json.optString("sortDirection"),
                current.sortDirection,
            ),
            showPastEvents = json.optBoolean("showPastEvents", current.showPastEvents),
            reminderHour = json.optInt("reminderHour", current.reminderHour).coerceIn(0, 23),
            reminderMinute = json.optInt("reminderMinute", current.reminderMinute).coerceIn(0, 59),
        )
    }

    fun imageToJsonString(context: Context, image: LocalImageReference): String =
        imageToJson(context, image).toString()

    fun parseImageString(raw: String): LocalImageReference = parseImage(JSONObject(raw))

    private fun eventToJson(
        context: Context,
        event: DayEvent,
        today: LocalDate,
    ): JSONObject {
        val effectiveDate = DayMath.effectiveDate(event, today)
        return JSONObject()
            .put("id", event.id)
            .put("title", event.title)
            .put("date", event.date.toString())
            .put("effectiveDate", effectiveDate.toString())
            .put("signedDays", DayMath.signedDays(event, today))
            .put("repeatMode", event.repeatMode.name)
            .put("category", event.category)
            .put("note", event.note)
            .put("isPinned", event.isPinned)
            .put("reminderDaysBefore", event.reminderDaysBefore)
            .put("backgroundImage", event.backgroundImage?.let { imageToJson(context, it) })
            .put("createdAtEpochMillis", event.createdAtEpochMillis)
    }

    private fun milestoneToJson(milestone: DayMilestone): JSONObject = JSONObject()
        .put("id", milestone.id)
        .put("title", milestone.title)
        .put("date", milestone.date.toString())
        .put("note", milestone.note)
        .put("createdAtEpochMillis", milestone.createdAtEpochMillis)

    private fun albumToJson(album: DayAlbum): JSONObject = JSONObject()
        .put("id", album.id)
        .put("title", album.title)
        .put("eventIds", JSONArray(album.eventIds))
        .put("coverEventId", album.coverEventId)
        .put("createdAtEpochMillis", album.createdAtEpochMillis)
        .put("updatedAtEpochMillis", album.updatedAtEpochMillis)

    private fun draftToJson(context: Context, draft: NewEventDraft): JSONObject = JSONObject()
        .put("title", draft.title)
        .put("date", draft.date.toString())
        .put("category", draft.category)
        .put("note", draft.note)
        .put("repeatMode", draft.repeatMode.name)
        .put("isPinned", draft.isPinned)
        .put("reminderDaysBefore", draft.reminderDaysBefore)
        .put("backgroundImage", draft.backgroundImage?.let { imageToJson(context, it) })

    private fun imageToJson(context: Context, image: LocalImageReference): JSONObject {
        val file = File(File(context.filesDir, "images"), image.fileName)
        return JSONObject()
            .put("fileName", image.fileName)
            .put("filePath", file.takeIf { it.isFile }?.absolutePath)
            .put("width", image.width)
            .put("height", image.height)
            .put("originalFileName", image.originalFileName)
            .put("homeTransform", transformToJson(image.homeTransform))
            .put("detailTransform", transformToJson(image.detailTransform))
    }

    private fun settingsToJson(settings: AppSettings): JSONObject = JSONObject()
        .put("themeMode", settings.themeMode.name)
        .put("paletteStyle", settings.paletteStyle.name)
        .put("glassClarity", settings.glassClarity.coerceIn(0, 100))
        .put("backgroundMotionMode", settings.backgroundMotionMode)
        .put("dynamicBackground", settings.backgroundMotionMode != "STATIC")
        .put("backgroundTexture", settings.backgroundTexture)
        .put("dynamicEdgeReflection", settings.dynamicEdgeReflection)
        .put("sortMode", settings.sortMode.name)
        .put("sortDirection", settings.sortDirection.name)
        .put("showPastEvents", settings.showPastEvents)
        .put("reminderHour", settings.reminderHour)
        .put("reminderMinute", settings.reminderMinute)

    private fun transformToJson(transform: ImageTransform): JSONObject = JSONObject()
        .put("focusX", transform.focusX)
        .put("focusY", transform.focusY)
        .put("zoom", transform.zoom)

    private fun parseImage(json: JSONObject): LocalImageReference {
        return LocalImageReference(
            fileName = json.optString("fileName"),
            width = json.optInt("width", 1).coerceAtLeast(1),
            height = json.optInt("height", 1).coerceAtLeast(1),
            focusX = json.optDouble("focusX", 0.5).toFloat().coerceIn(0f, 1f),
            focusY = json.optDouble("focusY", 0.5).toFloat().coerceIn(0f, 1f),
            originalFileName = json.optString("originalFileName").takeIf { it.isNotBlank() },
            homeTransform = parseTransform(json.optJSONObject("homeTransform")),
            detailTransform = parseTransform(json.optJSONObject("detailTransform")),
        )
    }

    private fun parseTransform(json: JSONObject?): ImageTransform {
        if (json == null) return ImageTransform()
        return ImageTransform(
            focusX = json.optDouble("focusX", 0.5).toFloat(),
            focusY = json.optDouble("focusY", 0.5).toFloat(),
            zoom = json.optDouble("zoom", 1.0).toFloat(),
        ).normalized()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}

package io.github.thedayapp.data

import android.content.Context
import android.util.Log
import io.github.thedayapp.domain.normalizedCategoryName
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

class DayRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadEvents(): List<DayEvent> {
        val raw = preferences.getString(KEY_EVENTS, null) ?: return emptyList()
        // 本地事件按条解析：单条记录损坏时跳过该条，避免影响其余可恢复的数据。
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    runCatching {
                        val json = array.getJSONObject(index)
                        DayEvent(
                            id = json.getString("id"),
                            title = json.getString("title"),
                            date = LocalDate.parse(json.getString("date")),
                            repeatMode = enumValueOrDefault(
                                json.optString("repeatMode"),
                                RepeatMode.NONE,
                            ),
                            category = json.optString("category"),
                            note = json.optString("note"),
                            isPinned = json.optBoolean("isPinned", false),
                            reminderDaysBefore = if (json.isNull("reminderDaysBefore")) {
                                null
                            } else {
                                json.optInt("reminderDaysBefore")
                            },
                            backgroundImage = imageReferenceFromJson(
                                json.optJSONObject("backgroundImage"),
                            ),
                            createdAtEpochMillis = json.optLong(
                                "createdAtEpochMillis",
                                System.currentTimeMillis(),
                            ),
                        )
                    }.onFailure { exception ->
                        Log.w(TAG, "Skipping malformed event at index $index", exception)
                    }.getOrNull()
                        ?.takeIf { it.title.isNotBlank() }
                        ?.let(::add)
                }
            }
        }.onFailure { exception ->
            Log.w(TAG, "Failed to load events", exception)
        }.getOrDefault(emptyList())
    }

    fun saveEvents(events: List<DayEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject()
                    .put("id", event.id)
                    .put("title", event.title)
                    .put("date", event.date.toString())
                    .put("repeatMode", event.repeatMode.name)
                    .put("category", event.category)
                    .put("note", event.note)
                    .put("isPinned", event.isPinned)
                    .put("reminderDaysBefore", event.reminderDaysBefore ?: JSONObject.NULL)
                    .put(
                        "backgroundImage",
                        event.backgroundImage
                            ?.let(::imageReferenceToJson)
                            ?: JSONObject.NULL,
                    )
                    .put("createdAtEpochMillis", event.createdAtEpochMillis),
            )
        }
        preferences.edit().putString(KEY_EVENTS, array.toString()).apply()
    }

    fun loadMilestones(): List<DayMilestone> {
        val raw = preferences.getString(KEY_MILESTONES, null) ?: return emptyList()
        // 纪念碑数据同样按条容错，保留能够正常解析的记录。
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    runCatching {
                        val json = array.getJSONObject(index)
                        DayMilestone(
                            id = json.getString("id"),
                            title = json.getString("title"),
                            date = LocalDate.parse(json.getString("date")),
                            note = json.optString("note"),
                            createdAtEpochMillis = json.optLong(
                                "createdAtEpochMillis",
                                System.currentTimeMillis(),
                            ),
                        )
                    }.onFailure { exception ->
                        Log.w(TAG, "Skipping malformed milestone at index $index", exception)
                    }.getOrNull()
                        ?.takeIf { it.title.isNotBlank() }
                        ?.let(::add)
                }
            }
        }.onFailure { exception ->
            Log.w(TAG, "Failed to load milestones", exception)
        }.getOrDefault(emptyList())
    }

    fun saveMilestones(milestones: List<DayMilestone>) {
        val array = JSONArray()
        milestones.forEach { milestone ->
            array.put(
                JSONObject()
                    .put("id", milestone.id)
                    .put("title", milestone.title)
                    .put("date", milestone.date.toString())
                    .put("note", milestone.note)
                    .put("createdAtEpochMillis", milestone.createdAtEpochMillis),
            )
        }
        preferences.edit().putString(KEY_MILESTONES, array.toString()).apply()
    }


    fun loadAlbums(): List<DayAlbum> {
        val raw = preferences.getString(KEY_ALBUMS, null) ?: return emptyList()
        // 纪念册引用的日子可能已被删除，加载阶段只负责恢复结构，关联关系由状态层再校正。
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    runCatching {
                        val json = array.getJSONObject(index)
                        val eventIdsJson = json.optJSONArray("eventIds") ?: JSONArray()
                        val eventIds = buildList {
                            for (eventIndex in 0 until eventIdsJson.length()) {
                                eventIdsJson.optString(eventIndex)
                                    .takeIf { it.isNotBlank() }
                                    ?.let(::add)
                            }
                        }.distinct()

                        DayAlbum(
                            id = json.getString("id"),
                            title = json.getString("title"),
                            eventIds = eventIds,
                            coverEventId = json.optString("coverEventId")
                                .takeIf { it.isNotBlank() },
                            createdAtEpochMillis = json.optLong(
                                "createdAtEpochMillis",
                                System.currentTimeMillis(),
                            ),
                            updatedAtEpochMillis = json.optLong(
                                "updatedAtEpochMillis",
                                System.currentTimeMillis(),
                            ),
                        )
                    }.onFailure { exception ->
                        Log.w(TAG, "Skipping malformed album at index $index", exception)
                    }.getOrNull()
                        ?.takeIf { it.title.isNotBlank() }
                        ?.let(::add)
                }
            }
        }.onFailure { exception ->
            Log.w(TAG, "Failed to load albums", exception)
        }.getOrDefault(emptyList())
    }

    fun saveAlbums(albums: List<DayAlbum>) {
        val array = JSONArray()
        albums.forEach { album ->
            val eventIds = JSONArray()
            album.eventIds.distinct().forEach(eventIds::put)

            array.put(
                JSONObject()
                    .put("id", album.id)
                    .put("title", album.title)
                    .put("eventIds", eventIds)
                    .put("coverEventId", album.coverEventId ?: JSONObject.NULL)
                    .put("createdAtEpochMillis", album.createdAtEpochMillis)
                    .put("updatedAtEpochMillis", album.updatedAtEpochMillis),
            )
        }
        preferences.edit().putString(KEY_ALBUMS, array.toString()).apply()
    }
    fun loadSettings(): AppSettings {
        val raw = preferences.getString(KEY_SETTINGS, null) ?: return AppSettings()
        // 设置项兼容旧版本字段；整体解析失败时回退默认配置，保证应用仍可启动。
        return runCatching {
            val json = JSONObject(raw)
            val sortMode = enumValueOrDefault(
                json.optString("sortMode"),
                SortMode.SMART,
            )
            val sortDirection = if (json.has("sortDirection")) {
                enumValueOrDefault(
                    json.optString("sortDirection"),
                    SortDirection.ASCENDING,
                )
            } else {
                if (sortMode == SortMode.CREATED) {
                    SortDirection.DESCENDING
                } else {
                    SortDirection.ASCENDING
                }
            }
            AppSettings(
                themeMode = enumValueOrDefault(
                    json.optString("themeMode"),
                    ThemeMode.SYSTEM,
                ),
                paletteStyle = paletteStyleFromRaw(
                    json.optString("paletteStyle"),
                ),
                glassClarity = json.optInt("glassClarity", 62).coerceIn(0, 100),
                backgroundMotionMode = json.optString("backgroundMotionMode")
                    .takeIf { it in setOf("STATIC", "FLOW", "AURORA") }
                    ?: if (json.optBoolean("dynamicBackground", true)) "FLOW" else "STATIC",
                backgroundTexture = json.optString("backgroundTexture", "DIAGONAL")
                    .takeIf { it in setOf("NONE", "DIAGONAL", "WAVE", "STARS", "CONSTELLATION", "HEART") }
                    ?: "DIAGONAL",
                dynamicEdgeReflection = json.optBoolean("dynamicEdgeReflection", true),
                sortMode = sortMode,
                sortDirection = sortDirection,
                reminderHour = json.optInt("reminderHour", 9).coerceIn(0, 23),
                reminderMinute = json.optInt("reminderMinute", 0).coerceIn(0, 59),
            )
        }.onFailure { exception ->
            Log.w(TAG, "Failed to load settings; using defaults", exception)
        }.getOrDefault(AppSettings())
    }

    fun saveSettings(settings: AppSettings) {
        val json = JSONObject()
            .put("themeMode", settings.themeMode.name)
            .put("paletteStyle", settings.paletteStyle.name)
            .put("glassClarity", settings.glassClarity.coerceIn(0, 100))
            .put("backgroundMotionMode", settings.backgroundMotionMode)
            .put("dynamicBackground", settings.backgroundMotionMode != "STATIC")
            .put("backgroundTexture", settings.backgroundTexture)
            .put("dynamicEdgeReflection", settings.dynamicEdgeReflection)
            .put("sortMode", settings.sortMode.name)
            .put("sortDirection", settings.sortDirection.name)
            .put("reminderHour", settings.reminderHour)
            .put("reminderMinute", settings.reminderMinute)
        preferences.edit().putString(KEY_SETTINGS, json.toString()).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    fun loadNewEventDraft(): NewEventDraft? {
        val raw = preferences.getString(KEY_NEW_EVENT_DRAFT, null) ?: return null
        // 草稿只用于恢复未完成编辑，损坏时直接忽略，不阻塞正式事件数据加载。
        return runCatching {
            val json = JSONObject(raw)
            NewEventDraft(
                title = json.optString("title"),
                date = LocalDate.parse(json.getString("date")),
                category = json.optString("category"),
                note = json.optString("note"),
                repeatMode = enumValueOrDefault(
                    json.optString("repeatMode"),
                    RepeatMode.NONE,
                ),
                isPinned = json.optBoolean("isPinned", false),
                reminderDaysBefore = if (json.isNull("reminderDaysBefore")) {
                    null
                } else {
                    json.optInt("reminderDaysBefore")
                },
                backgroundImage = imageReferenceFromJson(
                    json.optJSONObject("backgroundImage"),
                ),
            )
        }.onFailure { exception ->
            Log.w(TAG, "Failed to load new-event draft", exception)
        }.getOrNull()
    }

    fun saveNewEventDraft(draft: NewEventDraft) {
        val json = JSONObject()
            .put("title", draft.title)
            .put("date", draft.date.toString())
            .put("category", draft.category)
            .put("note", draft.note)
            .put("repeatMode", draft.repeatMode.name)
            .put("isPinned", draft.isPinned)
            .put("reminderDaysBefore", draft.reminderDaysBefore ?: JSONObject.NULL)
            .put(
                "backgroundImage",
                draft.backgroundImage
                    ?.let(::imageReferenceToJson)
                    ?: JSONObject.NULL,
            )
        preferences.edit().putString(KEY_NEW_EVENT_DRAFT, json.toString()).apply()
    }

    fun clearNewEventDraft() {
        preferences.edit().remove(KEY_NEW_EVENT_DRAFT).apply()
    }

    private fun imageReferenceToJson(image: LocalImageReference): JSONObject {
        return JSONObject()
            .put("fileName", image.fileName)
            .put("width", image.width)
            .put("height", image.height)
            .put("focusX", image.focusX)
            .put("focusY", image.focusY)
            .put(
                "originalFileName",
                image.originalFileName ?: JSONObject.NULL,
            )
            .put(
                "homeTransform",
                imageTransformToJson(image.homeTransform),
            )
            .put(
                "detailTransform",
                imageTransformToJson(image.detailTransform),
            )
    }

    private fun imageTransformToJson(transform: ImageTransform): JSONObject {
        val safe = transform.normalized()
        return JSONObject()
            .put("focusX", safe.focusX)
            .put("focusY", safe.focusY)
            .put("zoom", safe.zoom)
    }

    private fun imageReferenceFromJson(json: JSONObject?): LocalImageReference? {
        if (json == null) return null

        val fileName = json.optString("fileName")
        if (fileName.isEmpty()) return null
        if (File(fileName).name != fileName) return null
        if (!fileName.endsWith(".webp")) return null

        val width = json.optInt("width", 0)
        val height = json.optInt("height", 0)
        if (width <= 0 || height <= 0) return null

        val focusX = json.optDouble("focusX", 0.5)
            .takeIf { it.isFinite() }
            ?.toFloat()
            ?.coerceIn(0f, 1f)
            ?: 0.5f

        val focusY = json.optDouble("focusY", 0.5)
            .takeIf { it.isFinite() }
            ?.toFloat()
            ?.coerceIn(0f, 1f)
            ?: 0.5f

        val originalFileName = json
            .optString("originalFileName")
            .takeIf { candidate ->
                candidate.isNotEmpty() &&
                    File(candidate).name == candidate &&
                    candidate.endsWith(".webp")
            }

        val legacyTransform = ImageTransform(
            focusX = focusX,
            focusY = focusY,
            zoom = 1f,
        )
        val homeTransform = imageTransformFromJson(
            json.optJSONObject("homeTransform"),
            fallback = legacyTransform,
        )
        val detailTransform = imageTransformFromJson(
            json.optJSONObject("detailTransform"),
            fallback = legacyTransform,
        )

        return LocalImageReference(
            fileName = fileName,
            width = width,
            height = height,
            focusX = focusX,
            focusY = focusY,
            originalFileName = originalFileName,
            homeTransform = homeTransform,
            detailTransform = detailTransform,
        )
    }

    private fun imageTransformFromJson(
        json: JSONObject?,
        fallback: ImageTransform,
    ): ImageTransform {
        if (json == null) return fallback.normalized()

        val focusX = json.optDouble("focusX", fallback.focusX.toDouble())
            .takeIf { it.isFinite() }
            ?.toFloat()
            ?: fallback.focusX
        val focusY = json.optDouble("focusY", fallback.focusY.toDouble())
            .takeIf { it.isFinite() }
            ?.toFloat()
            ?: fallback.focusY
        val zoom = json.optDouble("zoom", fallback.zoom.toDouble())
            .takeIf { it.isFinite() }
            ?.toFloat()
            ?: fallback.zoom

        return ImageTransform(
            focusX = focusX,
            focusY = focusY,
            zoom = zoom,
        ).normalized()
    }

    fun loadCategoryCovers(): Map<String, LocalImageReference> {
        val raw = preferences.getString(KEY_CATEGORY_COVERS, null)
            ?: return emptyMap()
        // 分类封面仅恢复通过文件名和尺寸校验的图片引用，异常数据不进入运行状态。

        return runCatching {
            val array = JSONArray(raw)
            val covers = mutableMapOf<String, LocalImageReference>()

            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: continue

                if (!json.has("categoryName")) {
                    continue
                }

                val categoryName = normalizedCategoryName(
                    json.optString("categoryName"),
                )

                val image = imageReferenceFromJson(
                    json.optJSONObject("image"),
                ) ?: continue

                covers[categoryName] = image
            }

            covers.toMap()
        }.onFailure { exception ->
            Log.w(TAG, "Failed to load category covers", exception)
        }.getOrDefault(emptyMap())
    }

    fun saveCategoryCovers(covers: Map<String, LocalImageReference>) {
        val normalizedCovers = linkedMapOf<String, LocalImageReference>()
        covers.forEach { (categoryName, image) ->
            normalizedCovers[normalizedCategoryName(categoryName)] = image
        }

        val array = JSONArray()
        normalizedCovers.toSortedMap().forEach { (categoryName, image) ->
            array.put(
                JSONObject()
                    .put("categoryName", categoryName)
                    .put("image", imageReferenceToJson(image)),
            )
        }

        preferences.edit()
            .putString(KEY_CATEGORY_COVERS, array.toString())
            .apply()
    }

    private fun paletteStyleFromRaw(raw: String): PaletteStyle =
        when (raw) {
            "CATPPUCCIN" -> PaletteStyle.BLOOM_SPRING
            "ROSE_PINE" -> PaletteStyle.BLOOM_PETAL
            "NORD" -> PaletteStyle.BLOOM_MIST
            "SOLARIZED" -> PaletteStyle.BLOOM_RIPPLE
            "GRUVBOX" -> PaletteStyle.BLOOM_STONE
            "DRACULA" -> PaletteStyle.BLOOM_LAPIS
            else -> enumValueOrDefault(
                raw,
                PaletteStyle.MIDNIGHT,
            )
        }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: default

    companion object {
        private const val TAG = "DayRepository"
        const val PREFS_NAME = "the_day_store"
        private const val KEY_EVENTS = "events_json"
        private const val KEY_SETTINGS = "settings_json"
        private const val KEY_NEW_EVENT_DRAFT = "new_event_draft_json"
        private const val KEY_CATEGORY_COVERS = "category_covers_json"
        private const val KEY_MILESTONES = "milestones_json"
        private const val KEY_ALBUMS = "albums_json"
    }
}

package io.github.thedayapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.view.WindowCompat
import com.yalantis.ucrop.UCrop
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.github.thedayapp.data.LocalImageReference
import io.github.thedayapp.media.LocalImageStore
import io.github.thedayapp.data.PaletteStyle
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.notification.InitialNotificationPermission
import io.github.thedayapp.sharing.BatchExportRenderer
import io.github.thedayapp.sharing.EventShareActions
import io.github.thedayapp.sharing.EventMemoryImageRenderer
import io.github.thedayapp.sharing.GlassExportStyle
import io.github.thedayapp.sharing.MemoryImagePalette
import io.github.thedayapp.sharing.MemoryImageTemplate
import io.github.thedayapp.sharing.MemoryImageTemplatePreferences
import io.github.thedayapp.ui.glass.GlassStateJson
import io.github.thedayapp.ui.media.EdgeHandleUCropActivity
import io.github.thedayapp.update.AppUpdateManager
import io.github.thedayapp.update.StartupUpdateChecker
import io.github.thedayapp.update.UpdateCheckResult
import io.github.thedayapp.update.UpdateDownloadState
import io.github.thedayapp.update.UpdatePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

class GlassFlutterActivity : FlutterActivity() {
    private val appState by lazy { TheDayState(applicationContext) }
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val updateManager by lazy { AppUpdateManager(applicationContext) }
    private val updatePreferences by lazy { UpdatePreferences(applicationContext) }
    private var channel: MethodChannel? = null
    private var pendingEventId: String? = null
    private var notificationPermissionResult: MethodChannel.Result? = null
    private var imagePickResult: MethodChannel.Result? = null
    private var pendingSourceUri: Uri? = null
    private var pendingCropUri: Uri? = null
    private var pendingRecropImage: LocalImageReference? = null
    private var pendingSourceTemporary = false

    override fun onCreate(savedInstanceState: Bundle?) {
        pendingEventId = intent.getStringExtra(MainActivity.EXTRA_OPEN_EVENT_ID)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        requestInitialNotificationPermissionIfNeeded()
        StartupUpdateChecker.check(this)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        channel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL_NAME,
        ).also { methodChannel ->
            methodChannel.setMethodCallHandler { call, result ->
                when (call.method) {
                    "getSnapshot" -> {
                        val requestedEventId = pendingEventId
                        pendingEventId = null
                        result.success(snapshot(requestedEventId))
                    }

                    "updateSettings" -> {
                        val raw = call.arguments as? String
                        if (raw == null) {
                            result.error("invalid_arguments", "Settings JSON is required", null)
                            return@setMethodCallHandler
                        }
                        val next = GlassStateJson.parseSettings(raw, appState.settings)
                        appState.updateSettings(next)
                        result.success(snapshot())
                    }

                    "saveEvent" -> {
                        val raw = call.arguments as? String
                        if (raw == null) {
                            result.error("invalid_arguments", "Event JSON is required", null)
                            return@setMethodCallHandler
                        }
                        val id = runCatching { JSONObject(raw).optString("id") }.getOrNull()
                        val existing = id?.takeIf { it.isNotBlank() && it != "null" }?.let(appState::eventById)
                        val event = runCatching { GlassStateJson.parseEvent(raw, existing) }
                            .getOrElse { error ->
                                result.error(
                                    "invalid_event",
                                    error.message ?: "Event data is invalid",
                                    null,
                                )
                                return@setMethodCallHandler
                            }
                        if (event.title.isBlank()) {
                            result.error("invalid_event", "Event title is required", null)
                            return@setMethodCallHandler
                        }
                        runCatching { appState.upsertEvent(event) }
                            .getOrElse { error ->
                                result.error(
                                    "save_failed",
                                    error.message ?: "Event persistence failed",
                                    null,
                                )
                                return@setMethodCallHandler
                            }
                        val savedEvent = appState.eventById(event.id)
                        if (savedEvent == null) {
                            result.error("save_failed", "Saved event is missing from native state", null)
                            return@setMethodCallHandler
                        }
                        if (savedEvent.date != event.date) {
                            result.error("save_failed", "Saved event date did not round-trip correctly", null)
                            return@setMethodCallHandler
                        }
                        if (existing == null) appState.clearNewEventDraft()
                        result.success(snapshot(savedEventId = event.id))
                    }

                    "saveNewEventDraft" -> saveNewEventDraft(call.arguments as? String, result)
                    "clearNewEventDraft" -> {
                        appState.clearNewEventDraft()
                        result.success(snapshot())
                    }

                    "deleteEvent" -> {
                        val eventId = call.arguments as? String
                        if (eventId.isNullOrBlank()) {
                            result.error("invalid_arguments", "Event id is required", null)
                            return@setMethodCallHandler
                        }
                        appState.deleteEvent(eventId)
                        result.success(snapshot())
                    }

                    "togglePinned" -> {
                        val eventId = call.arguments as? String
                        if (eventId.isNullOrBlank()) {
                            result.error("invalid_arguments", "Event id is required", null)
                            return@setMethodCallHandler
                        }
                        appState.togglePinned(eventId)
                        result.success(snapshot())
                    }

                    "saveMilestone" -> saveMilestone(call.arguments as? String, result)
                    "deleteMilestone" -> deleteMilestone(call.arguments as? String, result)
                    "moveMilestone" -> moveMilestone(call.arguments as? String, result)
                    "moveMilestoneToIndex" -> moveMilestoneToIndex(call.arguments as? String, result)
                    "saveAlbum" -> saveAlbum(call.arguments as? String, result)
                    "deleteAlbum" -> deleteAlbum(call.arguments as? String, result)
                    "updateCategoryCover" -> updateCategoryCover(call.arguments as? String, result)
                    "deleteCategory" -> {
                        val category = call.arguments as? String
                        if (category.isNullOrBlank()) {
                            result.error("invalid_arguments", "Category name is required", null)
                            return@setMethodCallHandler
                        }
                        appState.deleteCategory(category)
                        result.success(snapshot())
                    }
                    "clearAllEvents" -> {
                        appState.clearAllEvents()
                        result.success(snapshot())
                    }
                    "requestNotificationPermission" -> requestNotificationPermission(result)
                    "pickImage" -> pickImage(result)
                    "recropImage" -> recropImage(call.arguments as? String, result)
                    "shareText" -> shareText(call.arguments as? String, result)
                    "readDocument" -> readDocument(call.arguments as? String, result)
                    "openExternal" -> openExternal(call.arguments as? String, result)
                    "exportEvents" -> exportEvents(call.arguments as? String, result)
                    "estimateExportPages" -> estimateExportPages(call.arguments as? String, result)
                    "exportMilestones" -> exportMilestones(call.arguments as? String, result)
                    "shareEventImage" -> shareEventImage(call.arguments as? String, result)
                    "getMemoryImageTemplate" -> result.success(MemoryImageTemplatePreferences.load(applicationContext).name)
                    "renderEventImagePreview" -> renderEventImagePreview(call.arguments as? String, result)
                    "pinWidget" -> pinWidget(call.arguments as? String, result)
                    "getUpdateStatus" -> getUpdateStatus(result)
                    "setUpdateWifiOnly" -> setUpdateWifiOnly(call.arguments as? Boolean, result)
                    "checkForUpdate" -> checkForUpdate(result)
                    "requestInstallUpdate" -> requestInstallUpdate(result)
                    else -> result.notImplemented()
                }
            }
        }
    }

    private fun saveMilestone(raw: String?, result: MethodChannel.Result) {
        if (raw == null) {
            result.error("invalid_arguments", "Milestone JSON is required", null)
            return
        }
        val id = runCatching { JSONObject(raw).optString("id") }.getOrNull()
        val existing = id?.takeIf { it.isNotBlank() }
            ?.let { target -> appState.milestones.firstOrNull { it.id == target } }
        val milestone = GlassStateJson.parseMilestone(raw, existing)
        if (milestone.title.isBlank()) {
            result.error("invalid_milestone", "Milestone title is required", null)
            return
        }
        appState.upsertMilestone(milestone)
        result.success(snapshot())
    }

    private fun saveNewEventDraft(raw: String?, result: MethodChannel.Result) {
        if (raw == null) {
            result.error("invalid_arguments", "Draft JSON is required", null)
            return
        }
        val draft = GlassStateJson.parseNewEventDraft(raw, appState.today)
        appState.saveNewEventDraft(draft)
        result.success(snapshot())
    }

    private fun deleteMilestone(id: String?, result: MethodChannel.Result) {
        if (id.isNullOrBlank()) {
            result.error("invalid_arguments", "Milestone id is required", null)
            return
        }
        appState.deleteMilestone(id)
        result.success(snapshot())
    }

    private fun moveMilestone(raw: String?, result: MethodChannel.Result) {
        val json = raw?.let(::JSONObject)
        val id = json?.optString("id")
        val direction = json?.optInt("direction", 0) ?: 0
        if (id.isNullOrBlank() || direction == 0) {
            result.error("invalid_arguments", "Milestone id and direction are required", null)
            return
        }
        appState.moveMilestone(id, direction.coerceIn(-1, 1))
        result.success(snapshot())
    }

    private fun moveMilestoneToIndex(raw: String?, result: MethodChannel.Result) {
        val json = raw?.let(::JSONObject)
        val id = json?.optString("id")
        val index = json?.optInt("index", -1) ?: -1
        if (id.isNullOrBlank() || index < 0) {
            result.error("invalid_arguments", "Milestone id and index are required", null)
            return
        }
        appState.moveMilestoneToIndex(id, index)
        result.success(snapshot())
    }

    private fun saveAlbum(raw: String?, result: MethodChannel.Result) {
        if (raw == null) {
            result.error("invalid_arguments", "Album JSON is required", null)
            return
        }
        val id = runCatching { JSONObject(raw).optString("id") }.getOrNull()
        val existing = id?.takeIf { it.isNotBlank() }?.let(appState::albumById)
        val album = GlassStateJson.parseAlbum(raw, existing)
        if (album.title.isBlank()) {
            result.error("invalid_album", "Album title is required", null)
            return
        }
        appState.upsertAlbum(album)
        result.success(snapshot())
    }

    private fun deleteAlbum(id: String?, result: MethodChannel.Result) {
        if (id.isNullOrBlank()) {
            result.error("invalid_arguments", "Album id is required", null)
            return
        }
        appState.deleteAlbum(id)
        result.success(snapshot())
    }

    private fun updateCategoryCover(raw: String?, result: MethodChannel.Result) {
        if (raw == null) {
            result.error("invalid_arguments", "Category cover JSON is required", null)
            return
        }
        val json = JSONObject(raw)
        val name = json.optString("category").trim()
        if (name.isBlank()) {
            result.error("invalid_arguments", "Category is required", null)
            return
        }
        val image = if (json.isNull("image") || !json.has("image")) {
            null
        } else {
            GlassStateJson.parseImageString(json.getJSONObject("image").toString())
        }
        appState.updateCategoryCover(name, image)
        result.success(snapshot())
    }

    private fun requestNotificationPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationGranted()) {
            result.success(true)
            return
        }
        if (notificationPermissionResult != null) {
            result.error("busy", "A permission request is already active", null)
            return
        }
        notificationPermissionResult = result
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    private fun pickImage(result: MethodChannel.Result) {
        if (imagePickResult != null) {
            result.error("busy", "An image picker is already active", null)
            return
        }
        imagePickResult = result
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply { type = "image/*" }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
        }
        runCatching { startActivityForResult(intent, REQUEST_PICK_IMAGE) }
            .onFailure { finishImagePickWithError(it) }
    }

    private fun recropImage(raw: String?, result: MethodChannel.Result) {
        if (raw.isNullOrBlank()) {
            result.error("invalid_arguments", "Image JSON is required", null)
            return
        }
        if (imagePickResult != null) {
            result.error("busy", "An image operation is already active", null)
            return
        }
        val image = runCatching { GlassStateJson.parseImageString(raw) }.getOrElse {
            result.error("invalid_image", it.message, null)
            return
        }
        val sourceName = image.originalFileName ?: image.fileName
        val sourceFile = LocalImageStore(applicationContext).fileFor(sourceName)
        if (sourceFile == null || !sourceFile.isFile) {
            result.error("image_missing", "Original image is unavailable", null)
            return
        }

        imagePickResult = result
        val cacheDir = File(cacheDir, "glass_picker").apply { mkdirs() }
        val crop = File(cacheDir, "recrop-${UUID.randomUUID()}.png")
        val sourceUri = Uri.fromFile(sourceFile)
        val cropUri = Uri.fromFile(crop)
        pendingSourceUri = sourceUri
        pendingCropUri = cropUri
        pendingRecropImage = image
        pendingSourceTemporary = false

        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setHideBottomControls(false)
            setCompressionFormat(Bitmap.CompressFormat.PNG)
            setToolbarTitle(getString(R.string.crop_image))
        }
        val cropIntent = UCrop.of(sourceUri, cropUri)
            .withMaxResultSize(2048, 2048)
            .withOptions(options)
            .getIntent(this)
            .apply { setClass(this@GlassFlutterActivity, EdgeHandleUCropActivity::class.java) }
        runCatching { startActivityForResult(cropIntent, REQUEST_CROP_IMAGE) }
            .onFailure { finishImagePickWithError(it) }
    }


    private fun pinWidget(eventId: String?, result: MethodChannel.Result) {
        if (eventId.isNullOrBlank()) {
            result.error("invalid_arguments", "Event id is required", null)
            return
        }
        runCatching {
            startActivity(
                Intent(this, WidgetPinActivity::class.java).apply {
                    putExtra(WidgetPinActivity.EXTRA_EVENT_ID, eventId)
                },
            )
        }.onSuccess { result.success(true) }
            .onFailure { result.error("widget_pin_failed", it.message, null) }
    }

    private fun shareText(text: String?, result: MethodChannel.Result) {
        if (text.isNullOrBlank()) {
            result.error("invalid_arguments", "Text is required", null)
            return
        }
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(sendIntent, getString(R.string.app_name)))
        }.onSuccess { result.success(true) }
            .onFailure { result.error("share_failed", it.message, null) }
    }

    private fun readDocument(key: String?, result: MethodChannel.Result) {
        val resource = when (key) {
            "UPDATE_NOTES" -> R.raw.update_notes
            "USAGE_GUIDE" -> R.raw.usage_guide
            "PRIVACY_POLICY" -> R.raw.privacy_policy
            "OPEN_SOURCE_NOTICES" -> R.raw.open_source_notices
            else -> null
        }
        if (resource == null) {
            result.error("invalid_document", "Unknown document", null)
            return
        }
        runCatching {
            resources.openRawResource(resource).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.onSuccess(result::success)
            .onFailure { result.error("document_failed", it.message, null) }
    }

    private fun openExternal(url: String?, result: MethodChannel.Result) {
        val uri = url?.let(Uri::parse)
        if (uri == null || uri.scheme != "https") {
            result.error("invalid_url", "Only HTTPS URLs are allowed", null)
            return
        }
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onSuccess { result.success(true) }
            .onFailure { result.error("open_failed", it.message, null) }
    }

    private fun estimateExportPages(raw: String?, result: MethodChannel.Result) {
        if (raw.isNullOrBlank()) {
            result.error("invalid_arguments", "Export JSON is required", null)
            return
        }
        val json = JSONObject(raw)
        val idsJson = json.optJSONArray("eventIds") ?: JSONArray()
        val events = buildList {
            for (index in 0 until idsJson.length()) {
                appState.eventById(idsJson.optString(index))?.let(::add)
            }
        }
        val mode = json.optString("mode", "LONG_IMAGE")
        val count = if (mode == "LIST") {
            BatchExportRenderer.estimateListPageCount(events)
        } else {
            BatchExportRenderer.estimateLongImagePageCount(events)
        }
        result.success(count)
    }

    private fun exportEvents(raw: String?, result: MethodChannel.Result) {
        if (raw == null) {
            result.error("invalid_arguments", "Export JSON is required", null)
            return
        }
        val json = JSONObject(raw)
        val ids = json.optJSONArray("eventIds") ?: JSONArray()
        val events = buildList {
            for (index in 0 until ids.length()) {
                appState.eventById(ids.optString(index))?.let(::add)
            }
        }
        if (events.isEmpty()) {
            result.error("invalid_export", "Select at least one event", null)
            return
        }
        val mode = json.optString("mode", "LONG_IMAGE")
        val action = json.optString("action", "SHARE")
        val template = memoryTemplate(json.optString("template"))
        val title = json.optString("title").trim().takeIf { it.isNotEmpty() }
        val exportStyle = glassExportStyle(json)

        activityScope.launch {
            val rendered = if (mode == "LIST") {
                BatchExportRenderer.renderListPages(
                    context = applicationContext,
                    events = events,
                    today = appState.today,
                    locale = Locale.getDefault(),
                    palette = memoryPalette(),
                    template = template,
                    title = title,
                    glassStyle = exportStyle,
                )
            } else {
                BatchExportRenderer.renderLongImagePages(
                    context = applicationContext,
                    events = events,
                    today = appState.today,
                    locale = Locale.getDefault(),
                    palette = memoryPalette(),
                    template = template,
                    title = title,
                    glassStyle = exportStyle,
                )
            }

            val pages = rendered.getOrElse {
                result.error("render_failed", it.message, null)
                return@launch
            }
            val actionResult = if (action == "SAVE") {
                try {
                    pages.forEach { bitmap ->
                        EventShareActions.saveImageToGallery(applicationContext, bitmap).getOrThrow()
                    }
                    Result.success(Unit)
                } catch (exception: Exception) {
                    Result.failure(exception)
                }
            } else {
                EventShareActions.shareImages(this@GlassFlutterActivity, pages)
            }
            pages.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
            actionResult.onSuccess { result.success(true) }
                .onFailure { result.error("export_failed", it.message, null) }
        }
    }

    private fun exportMilestones(raw: String?, result: MethodChannel.Result) {
        if (raw == null) {
            result.error("invalid_arguments", "Export JSON is required", null)
            return
        }
        val json = JSONObject(raw)
        val ids = json.optJSONArray("milestoneIds") ?: JSONArray()
        val selected = buildList {
            for (index in 0 until ids.length()) {
                val id = ids.optString(index)
                appState.milestones.firstOrNull { it.id == id }?.let(::add)
            }
        }
        if (selected.isEmpty()) {
            result.error("invalid_export", "Select at least one milestone", null)
            return
        }
        val action = json.optString("action", "SHARE")
        val template = memoryTemplate(json.optString("template"))
        val title = json.optString("title").trim().takeIf { it.isNotEmpty() } ?: "里程碑"
        val exportStyle = glassExportStyle(json)
        val events = selected.map { milestone ->
            io.github.thedayapp.data.DayEvent(
                id = milestone.id,
                title = milestone.title,
                date = milestone.date,
                category = "里程碑",
                note = milestone.note,
                createdAtEpochMillis = milestone.createdAtEpochMillis,
            )
        }

        activityScope.launch {
            val rendered = BatchExportRenderer.renderListPages(
                context = applicationContext,
                events = events,
                today = appState.today,
                locale = Locale.getDefault(),
                palette = memoryPalette(),
                template = template,
                title = title,
                glassStyle = exportStyle,
            )
            val pages = rendered.getOrElse {
                result.error("render_failed", it.message, null)
                return@launch
            }
            val actionResult = if (action == "SAVE") {
                try {
                    pages.forEach { bitmap ->
                        EventShareActions.saveImageToGallery(applicationContext, bitmap).getOrThrow()
                    }
                    Result.success(Unit)
                } catch (exception: Exception) {
                    Result.failure(exception)
                }
            } else {
                EventShareActions.shareImages(this@GlassFlutterActivity, pages)
            }
            pages.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
            actionResult.onSuccess { result.success(true) }
                .onFailure { result.error("export_failed", it.message, null) }
        }
    }

    private fun renderEventImagePreview(raw: String?, result: MethodChannel.Result) {
        if (raw == null) {
            result.error("invalid_arguments", "Preview JSON is required", null)
            return
        }
        val json = JSONObject(raw)
        val eventId = json.optString("eventId")
        val template = memoryTemplate(json.optString("template"))
        val event = appState.eventById(eventId)
        if (event == null) {
            result.error("invalid_event", "Event not found", null)
            return
        }
        val exportStyle = glassExportStyle(json)
        activityScope.launch {
            val rendered = EventMemoryImageRenderer.render(
                context = applicationContext,
                event = event,
                today = appState.today,
                palette = memoryPalette(),
                template = template,
                glassStyle = exportStyle,
            )
            val bitmap = rendered.getOrElse {
                result.error("render_failed", it.message, null)
                return@launch
            }
            val previewPrefix = "glass-memory-preview-${event.id}-${template.name}-"
            val file = File(
                cacheDir,
                "$previewPrefix${System.nanoTime()}.png",
            )
            val saved = runCatching {
                withContext(Dispatchers.IO) {
                    cacheDir.listFiles { candidate ->
                        candidate.name.startsWith(previewPrefix) && candidate != file
                    }?.forEach { candidate ->
                        runCatching { candidate.delete() }
                    }
                    file.outputStream().buffered().use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                            throw IOException("Could not encode preview image")
                        }
                    }
                }
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            saved.onSuccess {
                MemoryImageTemplatePreferences.save(applicationContext, template)
                result.success(file.absolutePath)
            }.onFailure {
                result.error("preview_failed", it.message, null)
            }
        }
    }

    private fun shareEventImage(raw: String?, result: MethodChannel.Result) {
        if (raw == null) {
            result.error("invalid_arguments", "Share JSON is required", null)
            return
        }
        val json = JSONObject(raw)
        val eventId = json.optString("eventId")
        val action = json.optString("action", "SHARE")
        val template = memoryTemplate(json.optString("template"))
        val event = appState.eventById(eventId)
        if (event == null) {
            result.error("invalid_event", "Event not found", null)
            return
        }
        val exportStyle = glassExportStyle(json)
        activityScope.launch {
            val rendered = EventMemoryImageRenderer.render(
                context = applicationContext,
                event = event,
                today = appState.today,
                palette = memoryPalette(),
                template = template,
                glassStyle = exportStyle,
            )
            val bitmap = rendered.getOrElse {
                result.error("render_failed", it.message, null)
                return@launch
            }
            val actionResult = if (action == "SAVE") {
                EventShareActions.saveImageToGallery(applicationContext, bitmap).map { Unit }
            } else {
                EventShareActions.shareEventImage(this@GlassFlutterActivity, bitmap)
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            actionResult.onSuccess { result.success(true) }
                .onFailure { result.error("share_failed", it.message, null) }
        }
    }

    private fun updateStatusJson(extra: String? = null): JSONObject {
        val status = updateManager.currentStatus()
        return JSONObject()
            .put("state", status.state.name)
            .put("versionName", status.versionName)
            .put("progressPercent", status.progressPercent)
            .put("wifiOnly", updatePreferences.wifiOnly)
            .put("extra", extra)
    }

    private fun getUpdateStatus(result: MethodChannel.Result) {
        activityScope.launch {
            if (updateManager.currentStatus().state == UpdateDownloadState.VERIFYING) {
                updateManager.verifyPendingDownloadIfNeeded()
            }
            result.success(updateStatusJson().toString())
        }
    }

    private fun setUpdateWifiOnly(value: Boolean?, result: MethodChannel.Result) {
        if (value == null) {
            result.error("invalid_arguments", "wifiOnly is required", null)
            return
        }
        updatePreferences.wifiOnly = value
        result.success(updateStatusJson().toString())
    }

    private fun checkForUpdate(result: MethodChannel.Result) {
        activityScope.launch {
            updateManager.resetFailedDownloadForManualCheck()
            val extra = when (val check = updateManager.checkForUpdate()) {
                is UpdateCheckResult.UpdateAvailable -> {
                    if (updateManager.startDownload(check.release)) {
                        "DOWNLOAD_STARTED"
                    } else {
                        "DOWNLOAD_FAILED"
                    }
                }
                UpdateCheckResult.UpToDate -> "UP_TO_DATE"
                UpdateCheckResult.CheckFailed -> "CHECK_FAILED"
            }
            if (updateManager.currentStatus().state == UpdateDownloadState.VERIFYING) {
                updateManager.verifyPendingDownloadIfNeeded()
            }
            result.success(updateStatusJson(extra).toString())
        }
    }

    private fun requestInstallUpdate(result: MethodChannel.Result) {
        val launch = updateManager.requestInstall(this)
        result.success(
            JSONObject()
                .put("launch", launch.name)
                .put("status", updateStatusJson())
                .toString(),
        )
    }

    private fun glassExportStyle(json: JSONObject? = null): GlassExportStyle {
        val colors = when (appState.settings.paletteStyle) {
            PaletteStyle.MIDNIGHT -> intArrayOf(0xFF3987E8.toInt(), 0xFF7656D8.toInt(), 0xFF22BFA1.toInt(), 0xFF7EC6FF.toInt())
            PaletteStyle.CINNABAR -> intArrayOf(0xFFF05F68.toInt(), 0xFFE58C59.toInt(), 0xFF9B5ACB.toInt(), 0xFFFF9992.toInt())
            PaletteStyle.PINE -> intArrayOf(0xFF2FB58F.toInt(), 0xFF6DA56E.toInt(), 0xFF3F7EB9.toInt(), 0xFF69DCB7.toInt())
            PaletteStyle.ANTIQUE_GOLD -> intArrayOf(0xFFD6A43E.toInt(), 0xFFB86D4E.toInt(), 0xFF6B78A9.toInt(), 0xFFFFD37D.toInt())
            PaletteStyle.BLOOM_PETAL -> intArrayOf(0xFFE76F9D.toInt(), 0xFFA06FDC.toInt(), 0xFF668DD5.toInt(), 0xFFFFA4C4.toInt())
            PaletteStyle.BLOOM_MIST -> intArrayOf(0xFF61A5D7.toInt(), 0xFF6E84D5.toInt(), 0xFF4FC0B5.toInt(), 0xFF9FD7FF.toInt())
            PaletteStyle.BLOOM_VERDANT -> intArrayOf(0xFF70B85C.toInt(), 0xFF3CA17F.toInt(), 0xFF6B85BE.toInt(), 0xFFA4E48C.toInt())
            PaletteStyle.BLOOM_STONE -> intArrayOf(0xFF9C8A80.toInt(), 0xFF89768F.toInt(), 0xFF6C8D98.toInt(), 0xFFCDBEB2.toInt())
            PaletteStyle.BLOOM_WHEAT -> intArrayOf(0xFFD9A63D.toInt(), 0xFFBF7751.toInt(), 0xFF718D78.toInt(), 0xFFFFCB73.toInt())
            PaletteStyle.BLOOM_INK -> intArrayOf(0xFF526F91.toInt(), 0xFF766B95.toInt(), 0xFF3D929A.toInt(), 0xFF9AB8DC.toInt())
            PaletteStyle.BLOOM_AMBER -> intArrayOf(0xFFE38A32.toInt(), 0xFFAE6257.toInt(), 0xFF7E9250.toInt(), 0xFFFFB25B.toInt())
            PaletteStyle.BLOOM_LAPIS -> intArrayOf(0xFF4D77D4.toInt(), 0xFF6C62C5.toInt(), 0xFF389CAC.toInt(), 0xFF9BB8FF.toInt())
            PaletteStyle.BLOOM_RIPPLE -> intArrayOf(0xFF34AAA5.toInt(), 0xFF477FB2.toInt(), 0xFF7068C8.toInt(), 0xFF74E0DC.toInt())
            PaletteStyle.BLOOM_CINNABAR -> intArrayOf(0xFFE84F5C.toInt(), 0xFFB86576.toInt(), 0xFF8E8153.toInt(), 0xFFFF7E87.toInt())
            PaletteStyle.BLOOM_SAGE -> intArrayOf(0xFF42B69A.toInt(), 0xFF4A8EAF.toInt(), 0xFF7272B7.toInt(), 0xFF81E3C5.toInt())
            PaletteStyle.BLOOM_SPRING -> intArrayOf(0xFF9C6DE0.toInt(), 0xFFD45B9B.toInt(), 0xFF4D96C2.toInt(), 0xFFD3A4FF.toInt())
        }
        return GlassExportStyle(
            primary = colors[0],
            secondary = colors[1],
            tertiary = colors[2],
            accent = colors[3],
            clarity = appState.settings.glassClarity,
            isDark = resolveDarkMode(),
            backgroundPhase = (json?.optDouble("backgroundPhase", 0.18) ?: 0.18).toFloat(),
            backgroundMode = json?.optString(
                "backgroundMode",
                appState.settings.backgroundMotionMode,
            )?.takeIf { it in setOf("STATIC", "FLOW", "AURORA") }
                ?: appState.settings.backgroundMotionMode,
            backgroundTexture = json?.optString(
                "backgroundTexture",
                appState.settings.backgroundTexture,
            )?.takeIf { it in setOf("NONE", "DIAGONAL", "WAVE", "STARS", "CONSTELLATION", "HEART") }
                ?: appState.settings.backgroundTexture,
        )
    }

    private fun memoryPalette(): MemoryImagePalette {
        val dark = resolveDarkMode()
        val primary = accentColorFor(appState.settings.paletteStyle)
        val background = if (dark) Color.rgb(11, 17, 30) else Color.rgb(242, 247, 250)
        val surface = if (dark) Color.rgb(25, 32, 45) else Color.WHITE
        val primaryContainer = blend(primary, background, if (dark) 0.58f else 0.72f)
        return MemoryImagePalette(
            background = background,
            surface = surface,
            primary = primary,
            primaryContainer = primaryContainer,
            onPrimary = if (Color.luminance(primary) < 0.48) Color.WHITE else Color.rgb(16, 20, 24),
            onPrimaryContainer = if (dark) Color.WHITE else Color.rgb(24, 30, 36),
            onSurface = if (dark) Color.rgb(245, 247, 250) else Color.rgb(23, 32, 43),
            onSurfaceVariant = if (dark) Color.rgb(188, 198, 210) else Color.rgb(86, 97, 111),
            isDark = dark,
        )
    }

    private fun memoryTemplate(raw: String?): MemoryImageTemplate =
        MemoryImageTemplate.values().firstOrNull { it.name == raw }
            ?: MemoryImageTemplate.MINIMAL

    private fun accentColorFor(style: PaletteStyle): Int = when (style) {
        PaletteStyle.MIDNIGHT -> Color.rgb(126, 198, 255)
        PaletteStyle.CINNABAR -> Color.rgb(255, 153, 146)
        PaletteStyle.PINE -> Color.rgb(105, 220, 183)
        PaletteStyle.ANTIQUE_GOLD -> Color.rgb(255, 211, 125)
        PaletteStyle.BLOOM_PETAL -> Color.rgb(255, 164, 196)
        PaletteStyle.BLOOM_MIST -> Color.rgb(159, 215, 255)
        PaletteStyle.BLOOM_VERDANT -> Color.rgb(164, 228, 140)
        PaletteStyle.BLOOM_STONE -> Color.rgb(205, 190, 178)
        PaletteStyle.BLOOM_WHEAT -> Color.rgb(255, 203, 115)
        PaletteStyle.BLOOM_INK -> Color.rgb(154, 184, 220)
        PaletteStyle.BLOOM_AMBER -> Color.rgb(255, 178, 91)
        PaletteStyle.BLOOM_LAPIS -> Color.rgb(155, 184, 255)
        PaletteStyle.BLOOM_RIPPLE -> Color.rgb(116, 224, 220)
        PaletteStyle.BLOOM_CINNABAR -> Color.rgb(255, 126, 135)
        PaletteStyle.BLOOM_SAGE -> Color.rgb(129, 227, 197)
        PaletteStyle.BLOOM_SPRING -> Color.rgb(211, 164, 255)
    }

    private fun blend(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun channel(a: Int, b: Int): Int = (a + (b - a) * t).toInt().coerceIn(0, 255)
        return Color.rgb(
            channel(Color.red(from), Color.red(to)),
            channel(Color.green(from), Color.green(to)),
            channel(Color.blue(from), Color.blue(to)),
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_PICK_IMAGE -> handlePickedImage(resultCode, data)
            REQUEST_CROP_IMAGE -> handleCroppedImage(resultCode, data)
        }
    }

    private fun handlePickedImage(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            finishImagePick(null)
            return
        }
        val selected = data?.data ?: run {
            finishImagePickWithError(IOException("Image picker returned no URI"))
            return
        }
        runCatching {
            val cacheDir = File(cacheDir, "glass_picker").apply { mkdirs() }
            val source = File(cacheDir, "source-${UUID.randomUUID()}.bin")
            contentResolver.openInputStream(selected)?.use { input ->
                source.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: throw IOException("Unable to open selected image")
            if (!source.isFile || source.length() <= 0L) throw IOException("Selected image is empty")

            val crop = File(cacheDir, "crop-${UUID.randomUUID()}.png")
            val sourceUri = Uri.fromFile(source)
            val cropUri = Uri.fromFile(crop)
            pendingSourceUri = sourceUri
            pendingCropUri = cropUri
            pendingRecropImage = null
            pendingSourceTemporary = true

            val options = UCrop.Options().apply {
                setFreeStyleCropEnabled(true)
                setHideBottomControls(false)
                setCompressionFormat(Bitmap.CompressFormat.PNG)
                setToolbarTitle(getString(R.string.crop_image))
            }
            val cropIntent = UCrop.of(sourceUri, cropUri)
                .withMaxResultSize(2048, 2048)
                .withOptions(options)
                .getIntent(this)
                .apply { setClass(this@GlassFlutterActivity, EdgeHandleUCropActivity::class.java) }
            startActivityForResult(cropIntent, REQUEST_CROP_IMAGE)
        }.onFailure(::finishImagePickWithError)
    }

    private fun handleCroppedImage(resultCode: Int, data: Intent?) {
        val source = pendingSourceUri
        val fallbackCrop = pendingCropUri
        if (resultCode != Activity.RESULT_OK || source == null) {
            if (resultCode == UCrop.RESULT_ERROR) {
                finishImagePickWithError(data?.let { UCrop.getError(it) } ?: IOException("Image crop failed"))
            } else {
                finishImagePick(null)
            }
            return
        }
        val crop = data?.let { UCrop.getOutput(it) } ?: fallbackCrop
        if (crop == null) {
            finishImagePickWithError(IOException("Image crop returned no URI"))
            return
        }

        activityScope.launch {
            val existing = pendingRecropImage
            val imported = if (existing != null) {
                appState.recropLocalImage(existing, crop)
            } else {
                appState.importLocalImage(source, crop)
            }
            imported.onSuccess { image ->
                finishImagePick(GlassStateJson.imageToJsonString(applicationContext, image))
            }.onFailure(::finishImagePickWithError)
        }
    }

    private fun finishImagePick(value: String?) {
        val result = imagePickResult
        imagePickResult = null
        result?.success(value)
        clearPickerFiles()
    }

    private fun finishImagePickWithError(error: Throwable?) {
        val result = imagePickResult
        imagePickResult = null
        result?.error("image_failed", error?.message ?: "Image operation failed", null)
        clearPickerFiles()
    }

    private fun clearPickerFiles() {
        if (pendingSourceTemporary) {
            pendingSourceUri?.path?.let { runCatching { File(it).delete() } }
        }
        pendingCropUri?.path?.let { runCatching { File(it).delete() } }
        pendingSourceUri = null
        pendingCropUri = null
        pendingRecropImage = null
        pendingSourceTemporary = false
    }

    private fun requestInitialNotificationPermissionIfNeeded() {
        if (!InitialNotificationPermission.shouldRequest(this)) return
        InitialNotificationPermission.markRequested(this)
        window.decorView.post {
            if (!isFinishing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_INITIAL_NOTIFICATIONS,
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            notificationPermissionResult?.success(notificationGranted())
            notificationPermissionResult = null
            channel?.invokeMethod("stateChanged", snapshot())
        } else if (requestCode == REQUEST_INITIAL_NOTIFICATIONS) {
            channel?.invokeMethod("stateChanged", snapshot())
            StartupUpdateChecker.check(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val eventId = intent.getStringExtra(MainActivity.EXTRA_OPEN_EVENT_ID)
        if (!eventId.isNullOrBlank()) {
            pendingEventId = eventId
            channel?.invokeMethod("openEvent", eventId)
        }
    }

    override fun onResume() {
        super.onResume()
        appState.refreshClock()
        channel?.invokeMethod("stateChanged", snapshot())
    }

    override fun onDestroy() {
        notificationPermissionResult?.error("activity_destroyed", "Activity was destroyed", null)
        imagePickResult?.error("activity_destroyed", "Activity was destroyed", null)
        notificationPermissionResult = null
        imagePickResult = null
        clearPickerFiles()
        activityScope.cancel()
        channel?.setMethodCallHandler(null)
        channel = null
        super.onDestroy()
    }

    private fun snapshot(
        requestedEventId: String? = null,
        savedEventId: String? = null,
    ): String {
        return GlassStateJson.snapshot(
            context = applicationContext,
            state = appState,
            isDark = resolveDarkMode(),
            notificationGranted = notificationGranted(),
            requestedEventId = requestedEventId,
            savedEventId = savedEventId,
        )
    }

    // Glass is intentionally a dark-only visual system. Keep the persisted ThemeMode
    // untouched so Classic can continue to honor the user's Classic appearance choice.
    private fun resolveDarkMode(): Boolean = true

    private fun notificationGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_NAME = "io.github.thedayapp/glass"
        private const val REQUEST_NOTIFICATIONS = 7101
        private const val REQUEST_PICK_IMAGE = 7102
        private const val REQUEST_CROP_IMAGE = 7103
        private const val REQUEST_INITIAL_NOTIFICATIONS = 7104

        fun createIntent(context: android.content.Context, eventId: String? = null): Intent {
            return Intent(context, GlassFlutterActivity::class.java).apply {
                eventId?.let { putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, it) }
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }
}

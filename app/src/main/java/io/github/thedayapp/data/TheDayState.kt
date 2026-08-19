package io.github.thedayapp.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thedayapp.domain.UNCLASSIFIED_CATEGORY_NAME
import io.github.thedayapp.domain.normalizedCategoryName
import io.github.thedayapp.media.LocalImageStore
import io.github.thedayapp.notification.ReminderScheduler
import io.github.thedayapp.widget.DayWidgetProvider
import io.github.thedayapp.widget.MonthCalendarWidgetProvider
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

class TheDayState(context: Context) {
    private val appContext = context.applicationContext
    private val repository = DayRepository(appContext)
    private val imageStore = LocalImageStore(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    var events: List<DayEvent> by mutableStateOf(repository.loadEvents())
        private set

    var settings: AppSettings by mutableStateOf(repository.loadSettings())
        private set

    var milestones: List<DayMilestone> by mutableStateOf(repository.loadMilestones())
        private set

    var albums: List<DayAlbum> by mutableStateOf(repository.loadAlbums())
        private set

    var today: LocalDate by mutableStateOf(LocalDate.now())
        private set

    var newEventDraft: NewEventDraft? by mutableStateOf(repository.loadNewEventDraft())
        private set

    var categoryCovers: Map<String, LocalImageReference> by mutableStateOf(
        repository.loadCategoryCovers(),
    )
        private set

    suspend fun importLocalImage(
        originalUri: Uri,
        croppedUri: Uri,
    ): Result<LocalImageReference> {
        val useOriginalDirectly = originalUri == croppedUri
        val originalResult = imageStore.importOriginalImage(originalUri)
        if (originalResult.isFailure) {
            return Result.failure(
                originalResult.exceptionOrNull()
                    ?: IllegalStateException("Original image import failed"),
            )
        }
        val original = originalResult.getOrThrow()

        if (useOriginalDirectly) {
            return Result.success(
                LocalImageReference(
                    fileName = original.fileName,
                    width = original.width,
                    height = original.height,
                    focusX = 0.5f,
                    focusY = 0.5f,
                    originalFileName = original.fileName,
                    homeTransform = ImageTransform(),
                    detailTransform = ImageTransform(),
                ),
            )
        }

        val croppedResult = try {
            imageStore.importImage(croppedUri)
        } catch (exception: CancellationException) {
            runBestEffort("cleanup original image after cancelled crop") {
                imageStore.deleteImage(original.fileName)
            }
            throw exception
        }
        if (croppedResult.isFailure) {
            runBestEffort("cleanup original image after failed crop") {
                imageStore.deleteImage(original.fileName)
            }
            return Result.failure(
                croppedResult.exceptionOrNull()
                    ?: IllegalStateException("Cropped image import failed"),
            )
        }
        val cropped = croppedResult.getOrThrow()

        return Result.success(
            LocalImageReference(
                fileName = cropped.fileName,
                width = cropped.width,
                height = cropped.height,
                focusX = 0.5f,
                focusY = 0.5f,
                originalFileName = original.fileName,
                homeTransform = ImageTransform(),
                detailTransform = ImageTransform(),
            ),
        )
    }

    suspend fun recropLocalImage(
        image: LocalImageReference,
        croppedUri: Uri,
    ): Result<LocalImageReference> {
        return imageStore.importImage(croppedUri).map { cropped ->
            LocalImageReference(
                fileName = cropped.fileName,
                width = cropped.width,
                height = cropped.height,
                focusX = 0.5f,
                focusY = 0.5f,
                originalFileName = image.originalFileName ?: image.fileName,
                homeTransform = ImageTransform(),
                detailTransform = ImageTransform(),
            )
        }
    }

    fun updateEventImageTransform(
        eventId: String,
        target: ImagePlacementTarget,
        transform: ImageTransform,
    ) {
        val event = eventById(eventId) ?: return
        val image = event.backgroundImage ?: return
        upsertEvent(
            event.copy(
                backgroundImage = image.withTransform(target, transform),
            ),
        )
    }

    fun refreshClock() {
        today = LocalDate.now()
    }

    fun eventById(id: String): DayEvent? = events.firstOrNull { it.id == id }

    fun albumById(id: String): DayAlbum? = albums.firstOrNull { it.id == id }

    fun upsertEvent(event: DayEvent) {
        val existingEvent = eventById(event.id)
        val previousImageFileNames = imageFileNames(existingEvent?.backgroundImage)

        val existingIndex = events.indexOfFirst { it.id == event.id }
        events = if (existingIndex >= 0) {
            events.toMutableList().also { it[existingIndex] = event }
        } else {
            events + event
        }
        repository.saveEvents(events)

        // 事件持久化是主操作；提醒和小组件刷新属于附加动作，失败时不能把已保存事件回滚成“保存失败”。
        runBestEffort("schedule event reminder") { ReminderScheduler.schedule(appContext, event, settings) }
        runBestEffort("refresh day widget") { DayWidgetProvider.requestUpdate(appContext) }
        runBestEffort("refresh calendar widget") { MonthCalendarWidgetProvider.requestUpdate(appContext) }

        releaseImageFilesIfUnreferenced(
            previousImageFileNames - imageFileNames(event.backgroundImage),
        )
    }

    fun deleteEvent(id: String) {
        val removedEvent = eventById(id)
        val removedImageFileNames = imageFileNames(removedEvent?.backgroundImage)

        ReminderScheduler.cancel(appContext, id)
        events = events.filterNot { it.id == id }
        repository.saveEvents(events)
        removeEventFromAlbums(id)
        DayWidgetProvider.requestUpdate(appContext)
        MonthCalendarWidgetProvider.requestUpdate(appContext)

        releaseImageFilesIfUnreferenced(removedImageFileNames)
    }

    fun togglePinned(id: String) {
        val event = eventById(id) ?: return
        upsertEvent(event.copy(isPinned = !event.isPinned))
    }

    fun upsertAlbum(album: DayAlbum) {
        val validEventIds = album.eventIds
            .distinct()
            .filter { eventId -> eventById(eventId) != null }
        val coverEventId = album.coverEventId
            ?.takeIf { it in validEventIds }
            ?: validEventIds.firstOrNull()

        val cleanAlbum = album.copy(
            title = album.title.trim(),
            eventIds = validEventIds,
            coverEventId = coverEventId,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )

        val existingIndex = albums.indexOfFirst { it.id == cleanAlbum.id }
        albums = if (existingIndex >= 0) {
            albums.toMutableList().also { it[existingIndex] = cleanAlbum }
        } else {
            albums + cleanAlbum
        }
        repository.saveAlbums(albums)
    }

    fun deleteAlbum(id: String) {
        albums = albums.filterNot { it.id == id }
        repository.saveAlbums(albums)
    }
    fun upsertMilestone(milestone: DayMilestone) {
        val existingIndex = milestones.indexOfFirst { it.id == milestone.id }
        milestones = if (existingIndex >= 0) {
            milestones.toMutableList().also { it[existingIndex] = milestone }
        } else {
            milestones + milestone
        }
        repository.saveMilestones(milestones)
    }

    fun moveMilestone(id: String, direction: Int): Boolean {
        val fromIndex = milestones.indexOfFirst { it.id == id }
        if (fromIndex !in milestones.indices) return false

        val toIndex = (fromIndex + direction).coerceIn(0, milestones.lastIndex)
        if (fromIndex == toIndex) return false

        milestones = milestones.toMutableList().also { list ->
            val moved = list.removeAt(fromIndex)
            list.add(toIndex, moved)
        }
        repository.saveMilestones(milestones)
        return true
    }

    fun moveMilestoneToIndex(id: String, targetIndex: Int): Boolean {
        val fromIndex = milestones.indexOfFirst { it.id == id }
        if (fromIndex !in milestones.indices) return false

        val toIndex = targetIndex.coerceIn(0, milestones.lastIndex)
        if (fromIndex == toIndex) return false

        milestones = milestones.toMutableList().also { list ->
            val moved = list.removeAt(fromIndex)
            list.add(toIndex, moved)
        }
        repository.saveMilestones(milestones)
        return true
    }

    fun deleteMilestone(id: String) {
        milestones = milestones.filterNot { it.id == id }
        repository.saveMilestones(milestones)
    }

    fun updateSettings(newSettings: AppSettings) {
        val previousSettings = settings

        // 先发布新设置让 Compose 立即重绘；只有提醒时刻真正变化时才重新调度全部提醒。
        settings = newSettings
        repository.saveSettings(newSettings)

        if (
            previousSettings.reminderHour != newSettings.reminderHour ||
            previousSettings.reminderMinute != newSettings.reminderMinute
        ) {
            ReminderScheduler.rescheduleAll(appContext, events, newSettings)
        }

        if (previousSettings != newSettings) {
            // 延后一帧再刷新小组件，避免主题切换时设置页短暂残留上一套明暗配色。
            mainHandler.post {
                DayWidgetProvider.requestUpdate(appContext)
                MonthCalendarWidgetProvider.requestUpdate(appContext)
            }
        }
    }

    fun clearAllEvents() {
        val eventImageFileNames = events
            .flatMap { event -> imageFileNames(event.backgroundImage) }
            .toSet()

        events.forEach { ReminderScheduler.cancel(appContext, it.id) }
        events = emptyList()
        repository.saveEvents(events)
        if (albums.any { it.eventIds.isNotEmpty() || it.coverEventId != null }) {
            albums = albums.map { album ->
                album.copy(
                    eventIds = emptyList(),
                    coverEventId = null,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            }
            repository.saveAlbums(albums)
        }
        DayWidgetProvider.requestUpdate(appContext)
        MonthCalendarWidgetProvider.requestUpdate(appContext)

        releaseImageFilesIfUnreferenced(eventImageFileNames)
    }

    fun saveNewEventDraft(draft: NewEventDraft) {
        val previousImageFileNames = imageFileNames(newEventDraft?.backgroundImage)

        newEventDraft = draft
        repository.saveNewEventDraft(draft)

        releaseImageFilesIfUnreferenced(
            previousImageFileNames - imageFileNames(draft.backgroundImage),
        )
    }

    fun clearNewEventDraft() {
        val previousImageFileNames = imageFileNames(newEventDraft?.backgroundImage)

        newEventDraft = null
        repository.clearNewEventDraft()

        releaseImageFilesIfUnreferenced(previousImageFileNames)
    }

    fun releaseLocalImageIfUnreferenced(image: LocalImageReference?) {
        releaseImageFilesIfUnreferenced(imageFileNames(image))
    }

    fun categoryCoverFor(categoryName: String): LocalImageReference? {
        return categoryCovers[normalizedCategoryName(categoryName)]
    }

    fun updateCategoryCover(categoryName: String, image: LocalImageReference?) {
        val normalizedName = normalizedCategoryName(categoryName)
        val previousImageFileNames = imageFileNames(categoryCovers[normalizedName])

        categoryCovers = if (image == null) {
            categoryCovers - normalizedName
        } else {
            categoryCovers + (normalizedName to image)
        }

        repository.saveCategoryCovers(categoryCovers)

        releaseImageFilesIfUnreferenced(
            previousImageFileNames - imageFileNames(image),
        )
    }

    fun deleteCategory(categoryName: String): Boolean {
        val normalizedName = normalizedCategoryName(categoryName)
        if (normalizedName == UNCLASSIFIED_CATEGORY_NAME) return false

        val previousCoverFiles = imageFileNames(categoryCovers[normalizedName])
        var changed = false
        events = events.map { event ->
            if (normalizedCategoryName(event.category) == normalizedName) {
                changed = true
                event.copy(category = "")
            } else {
                event
            }
        }
        if (changed) {
            repository.saveEvents(events)
        }

        if (normalizedName in categoryCovers) {
            categoryCovers = categoryCovers - normalizedName
            repository.saveCategoryCovers(categoryCovers)
        }

        runBestEffort("refresh day widget after category deletion") {
            DayWidgetProvider.requestUpdate(appContext)
        }
        runBestEffort("refresh calendar widget after category deletion") {
            MonthCalendarWidgetProvider.requestUpdate(appContext)
        }
        releaseImageFilesIfUnreferenced(previousCoverFiles)
        return changed || previousCoverFiles.isNotEmpty()
    }

    private fun removeEventFromAlbums(eventId: String) {
        var changed = false
        val updatedAlbums = albums.map { album ->
            if (eventId !in album.eventIds && album.coverEventId != eventId) {
                album
            } else {
                changed = true
                val updatedEventIds = album.eventIds.filterNot { it == eventId }
                album.copy(
                    eventIds = updatedEventIds,
                    coverEventId = album.coverEventId
                        ?.takeIf { it != eventId && it in updatedEventIds }
                        ?: updatedEventIds.firstOrNull(),
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            }
        }
        if (changed) {
            albums = updatedAlbums
            repository.saveAlbums(albums)
        }
    }
    private fun imageFileNames(image: LocalImageReference?): Set<String> {
        if (image == null) return emptySet()

        return buildSet {
            add(image.fileName)
            image.originalFileName?.let(::add)
        }
    }

    private fun isImageReferenced(fileName: String): Boolean {
        return events.any { event ->
            fileName in imageFileNames(event.backgroundImage)
        } || fileName in imageFileNames(newEventDraft?.backgroundImage)
            || categoryCovers.values.any { image ->
                fileName in imageFileNames(image)
            }
    }

    private fun runBestEffort(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (exception: Exception) {
            Log.w(TAG, "Best-effort operation failed: $operation", exception)
        }
    }

    private fun releaseImageFilesIfUnreferenced(fileNames: Set<String>) {
        fileNames.forEach { fileName ->
            if (!isImageReferenced(fileName)) {
                runBestEffort("delete unreferenced image $fileName") {
                    imageStore.deleteImage(fileName)
                }
            }
        }
    }

    companion object {
        private const val TAG = "TheDayState"
    }
}

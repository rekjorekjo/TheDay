package io.github.thedayapp.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
            runCatching { imageStore.deleteImage(original.fileName) }
            throw exception
        }
        if (croppedResult.isFailure) {
            runCatching { imageStore.deleteImage(original.fileName) }
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
        ReminderScheduler.schedule(appContext, event, settings)
        DayWidgetProvider.requestUpdate(appContext)
        MonthCalendarWidgetProvider.requestUpdate(appContext)

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
        DayWidgetProvider.requestUpdate(appContext)
        MonthCalendarWidgetProvider.requestUpdate(appContext)

        releaseImageFilesIfUnreferenced(removedImageFileNames)
    }

    fun togglePinned(id: String) {
        val event = eventById(id) ?: return
        upsertEvent(event.copy(isPinned = !event.isPinned))
    }

    fun updateSettings(newSettings: AppSettings) {
        val previousSettings = settings

        // Publish the new settings first so Compose can redraw the theme in the
        // same frame. Expensive reminder work is now only performed when the
        // reminder clock actually changes.
        settings = newSettings
        repository.saveSettings(newSettings)

        if (
            previousSettings.reminderHour != newSettings.reminderHour ||
            previousSettings.reminderMinute != newSettings.reminderMinute
        ) {
            ReminderScheduler.rescheduleAll(appContext, events, newSettings)
        }

        if (previousSettings != newSettings) {
            // Defer widget broadcasts until after Compose has had a chance to
            // render the new color scheme. This avoids the settings top bar
            // briefly retaining the previous light/dark color.
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

    private fun releaseImageFilesIfUnreferenced(fileNames: Set<String>) {
        fileNames.forEach { fileName ->
            if (!isImageReferenced(fileName)) {
                runCatching {
                    imageStore.deleteImage(fileName)
                }
            }
        }
    }
}

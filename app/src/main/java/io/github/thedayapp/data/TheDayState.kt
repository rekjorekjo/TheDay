package io.github.thedayapp.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thedayapp.domain.normalizedCategoryName
import io.github.thedayapp.media.LocalImageStore
import io.github.thedayapp.notification.ReminderScheduler
import io.github.thedayapp.widget.DayWidgetProvider
import io.github.thedayapp.widget.MonthCalendarWidgetProvider
import java.time.LocalDate

class TheDayState(context: Context) {
    private val appContext = context.applicationContext
    private val repository = DayRepository(appContext)
    private val imageStore = LocalImageStore(appContext)

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

    suspend fun importLocalImage(uri: Uri): Result<LocalImageReference> {
        return imageStore.importImage(uri).map { stored ->
            LocalImageReference(
                fileName = stored.fileName,
                width = stored.width,
                height = stored.height,
                focusX = 0.5f,
                focusY = 0.5f,
            )
        }
    }

    fun refreshClock() {
        today = LocalDate.now()
    }

    fun eventById(id: String): DayEvent? = events.firstOrNull { it.id == id }

    fun upsertEvent(event: DayEvent) {
        val existingEvent = eventById(event.id)
        val previousImageFileName = existingEvent?.backgroundImage?.fileName

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

        if (previousImageFileName != event.backgroundImage?.fileName) {
            deleteImageIfUnreferenced(previousImageFileName)
        }
    }

    fun deleteEvent(id: String) {
        val removedEvent = eventById(id)
        val removedImageFileName = removedEvent?.backgroundImage?.fileName

        ReminderScheduler.cancel(appContext, id)
        events = events.filterNot { it.id == id }
        repository.saveEvents(events)
        DayWidgetProvider.requestUpdate(appContext)
        MonthCalendarWidgetProvider.requestUpdate(appContext)

        deleteImageIfUnreferenced(removedImageFileName)
    }

    fun togglePinned(id: String) {
        val event = eventById(id) ?: return
        upsertEvent(event.copy(isPinned = !event.isPinned))
    }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        repository.saveSettings(newSettings)
        ReminderScheduler.rescheduleAll(appContext, events, newSettings)
        DayWidgetProvider.requestUpdate(appContext)
        MonthCalendarWidgetProvider.requestUpdate(appContext)
    }

    fun clearAllEvents() {
        val imageFileNames = events
            .mapNotNull { it.backgroundImage?.fileName }
            .toSet()

        events.forEach { ReminderScheduler.cancel(appContext, it.id) }
        events = emptyList()
        repository.saveEvents(events)
        DayWidgetProvider.requestUpdate(appContext)
        MonthCalendarWidgetProvider.requestUpdate(appContext)

        imageFileNames.forEach { fileName ->
            deleteImageIfUnreferenced(fileName)
        }
    }

    fun saveNewEventDraft(draft: NewEventDraft) {
        val previousImageFileName = newEventDraft?.backgroundImage?.fileName

        newEventDraft = draft
        repository.saveNewEventDraft(draft)

        if (previousImageFileName != draft.backgroundImage?.fileName) {
            deleteImageIfUnreferenced(previousImageFileName)
        }
    }

    fun clearNewEventDraft() {
        val previousImageFileName = newEventDraft?.backgroundImage?.fileName

        newEventDraft = null
        repository.clearNewEventDraft()

        deleteImageIfUnreferenced(previousImageFileName)
    }

    fun releaseLocalImageIfUnreferenced(fileName: String?) {
        deleteImageIfUnreferenced(fileName)
    }

    fun categoryCoverFor(categoryName: String): LocalImageReference? {
        return categoryCovers[normalizedCategoryName(categoryName)]
    }

    fun updateCategoryCover(categoryName: String, image: LocalImageReference?) {
        val normalizedName = normalizedCategoryName(categoryName)
        val previousImageFileName = categoryCovers[normalizedName]?.fileName

        categoryCovers = if (image == null) {
            categoryCovers - normalizedName
        } else {
            categoryCovers + (normalizedName to image)
        }

        repository.saveCategoryCovers(categoryCovers)

        if (previousImageFileName != image?.fileName) {
            deleteImageIfUnreferenced(previousImageFileName)
        }
    }

    private fun isImageReferenced(fileName: String): Boolean {
        return events.any { event ->
            event.backgroundImage?.fileName == fileName
        } || newEventDraft?.backgroundImage?.fileName == fileName
            || categoryCovers.values.any { image ->
                image.fileName == fileName
            }
    }

    private fun deleteImageIfUnreferenced(fileName: String?) {
        if (fileName == null) return
        if (isImageReferenced(fileName)) return

        runCatching {
            imageStore.deleteImage(fileName)
        }
    }
}
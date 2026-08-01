package io.github.thedayapp

import android.app.Application
import android.content.pm.ShortcutManager
import io.github.thedayapp.data.DayRepository
import io.github.thedayapp.device.HyperOsCompatibility
import io.github.thedayapp.notification.NotificationChannels
import io.github.thedayapp.notification.ReminderScheduler
import io.github.thedayapp.widget.DayWidgetProvider
import io.github.thedayapp.widget.MonthCalendarWidgetProvider

class TheDayApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        HyperOsCompatibility.initialize(this)
        removeLegacyWidgetShortcut()

        NotificationChannels.ensure(this)
        val repository = DayRepository(this)
        ReminderScheduler.rescheduleAll(
            this,
            repository.loadEvents(),
            repository.loadSettings(),
        )
        DayWidgetProvider.requestUpdate(this)
        MonthCalendarWidgetProvider.requestUpdate(this)
    }

    private fun removeLegacyWidgetShortcut() {
        val shortcutManager = getSystemService(ShortcutManager::class.java)
            ?: return
        runCatching {
            shortcutManager.removeDynamicShortcuts(
                listOf(LEGACY_ADD_WIDGET_SHORTCUT_ID),
            )
        }
        runCatching {
            shortcutManager.disableShortcuts(
                listOf(LEGACY_ADD_WIDGET_SHORTCUT_ID),
            )
        }
    }

    private companion object {
        const val LEGACY_ADD_WIDGET_SHORTCUT_ID = "add_widget"
    }
}

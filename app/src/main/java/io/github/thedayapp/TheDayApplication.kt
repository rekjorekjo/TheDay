package io.github.thedayapp

import android.app.Application
import android.content.pm.ShortcutManager
import android.util.Log
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
        try {
            shortcutManager.removeDynamicShortcuts(
                listOf(LEGACY_ADD_WIDGET_SHORTCUT_ID),
            )
            shortcutManager.disableShortcuts(
                listOf(LEGACY_ADD_WIDGET_SHORTCUT_ID),
            )
        } catch (exception: Exception) {
            // 旧快捷方式清理失败不影响应用启动，后续启动仍会再次尝试。
            Log.w(TAG, "Failed to remove legacy widget shortcut", exception)
        }
    }

    private companion object {
        const val TAG = "TheDayApplication"
        const val LEGACY_ADD_WIDGET_SHORTCUT_ID = "add_widget"
    }
}

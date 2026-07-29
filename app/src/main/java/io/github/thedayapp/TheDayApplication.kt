package io.github.thedayapp

import android.app.Application
import io.github.thedayapp.data.DayRepository
import io.github.thedayapp.notification.NotificationChannels
import io.github.thedayapp.notification.ReminderScheduler
import io.github.thedayapp.widget.DayWidgetProvider
import io.github.thedayapp.widget.MonthCalendarWidgetProvider

class TheDayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
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
}

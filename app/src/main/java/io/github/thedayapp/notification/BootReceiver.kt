package io.github.thedayapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.thedayapp.data.DayRepository
import io.github.thedayapp.widget.DayWidgetProvider
import io.github.thedayapp.widget.MonthCalendarWidgetProvider

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val supported = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
        if (intent.action !in supported) return

        val repository = DayRepository(context)
        ReminderScheduler.rescheduleAll(
            context,
            repository.loadEvents(),
            repository.loadSettings(),
        )
        DayWidgetProvider.requestUpdate(context)
        MonthCalendarWidgetProvider.requestUpdate(context)
    }
}

package io.github.thedayapp.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.thedayapp.data.AppSettings
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.domain.DayMath
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {
    const val ACTION_REMIND = "io.github.thedayapp.action.REMIND"
    const val EXTRA_EVENT_ID = "event_id"

    fun schedule(context: Context, event: DayEvent, settings: AppSettings) {
        cancel(context, event.id)
        val triggerAt = DayMath.nextReminderDateTime(
            event = event,
            now = LocalDateTime.now(),
            reminderHour = settings.reminderHour,
            reminderMinute = settings.reminderMinute,
        ) ?: return

        val triggerMillis = triggerAt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerMillis,
            pendingIntent(context, event.id),
        )
    }

    fun cancel(context: Context, eventId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent(context, eventId))
    }

    fun rescheduleAll(
        context: Context,
        events: List<DayEvent>,
        settings: AppSettings,
    ) {
        events.forEach { event -> schedule(context, event, settings) }
    }

    private fun pendingIntent(context: Context, eventId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

package io.github.thedayapp.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.thedayapp.MainActivity
import io.github.thedayapp.R
import io.github.thedayapp.data.DayRepository
import io.github.thedayapp.data.RepeatMode
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.widget.DayWidgetProvider
import io.github.thedayapp.widget.MonthCalendarWidgetProvider
import java.time.LocalDate
import kotlin.math.abs

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "TheDayReminder"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val eventId = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID) ?: return

        val repository = DayRepository(context)
        val event = repository.loadEvents().firstOrNull { it.id == eventId } ?: return
        val settings = repository.loadSettings()
        val delta = DayMath.signedDays(event, LocalDate.now())

        val body = when {
            delta > 0 -> context.getString(R.string.notification_days_left, delta)
            delta < 0 -> context.getString(R.string.notification_days_passed, abs(delta))
            else -> context.getString(R.string.notification_today)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, event.id)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            event.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        NotificationChannels.ensure(context)
        val notification = Notification.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.title)
            .setContentText(body)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            context.getSystemService(NotificationManager::class.java)
                .notify(event.id.hashCode(), notification)
        } catch (exception: SecurityException) {
            Log.w(TAG, "Notification permission denied", exception)
        } catch (exception: RuntimeException) {
            // 通知失败不应中断后续的年度提醒续排和桌面小组件刷新。
            Log.w(TAG, "Failed to post reminder notification", exception)
        }

        if (event.repeatMode == RepeatMode.YEARLY) {
            ReminderScheduler.schedule(context, event, settings)
        }
        DayWidgetProvider.requestUpdate(context)
        MonthCalendarWidgetProvider.requestUpdate(context)
    }
}

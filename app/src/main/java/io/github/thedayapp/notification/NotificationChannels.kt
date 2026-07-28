package io.github.thedayapp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.thedayapp.R

object NotificationChannels {
    const val REMINDERS = "event_reminders"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            REMINDERS,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }
}

package io.github.thedayapp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import io.github.thedayapp.R

object NotificationChannels {
    const val REMINDERS = "event_reminders"
    const val APP_UPDATES = "app_updates"
    const val UPDATE_AVAILABLE = "update_available_heads_up"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val remindersChannel = NotificationChannel(
            REMINDERS,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            setShowBadge(true)
        }

        val updatesChannel = NotificationChannel(
            APP_UPDATES,
            context.getString(R.string.notification_channel_updates_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_updates_description)
            enableVibration(false)
            setShowBadge(false)
        }

        val updateAvailableChannel = NotificationChannel(
            UPDATE_AVAILABLE,
            context.getString(R.string.notification_channel_update_available_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_update_available_description)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            setShowBadge(false)
        }

        manager.createNotificationChannels(
            listOf(remindersChannel, updatesChannel, updateAvailableChannel),
        )
    }
}

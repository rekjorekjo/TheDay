package io.github.thedayapp.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.thedayapp.MainActivity
import io.github.thedayapp.R
import io.github.thedayapp.update.UpdateActions

object UpdateNotifier {
    private const val NOTIFICATION_ID_UPDATE_READY = 1001
    private const val NOTIFICATION_ID_UPDATE_FAILED = 1002

    fun sendUpdateReadyNotification(context: Context, versionName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionChecker = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            )
            if (permissionChecker != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = UpdateActions.ACTION_INSTALL_UPDATE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.APP_UPDATES)
            .setContentTitle(context.getString(R.string.notification_update_ready_title))
            .setContentText(context.getString(R.string.notification_update_ready_text, versionName))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATE_READY, notification)
    }

    fun sendVerificationFailedNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionChecker = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            )
            if (permissionChecker != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.APP_UPDATES)
            .setContentTitle(context.getString(R.string.notification_update_failed_title))
            .setContentText(context.getString(R.string.notification_update_failed_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATE_FAILED, notification)
    }

    fun cancelUpdateNotifications(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).apply {
            cancel(NOTIFICATION_ID_UPDATE_READY)
            cancel(NOTIFICATION_ID_UPDATE_FAILED)
        }
    }
}
package io.github.thedayapp.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.thedayapp.notification.NotificationChannels
import io.github.thedayapp.notification.UpdateNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            return
        }

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (downloadId < 0) {
            return
        }

        val preferences = UpdatePreferences(context.applicationContext)
        val pendingDownloadId = preferences.pendingDownloadId

        if (pendingDownloadId != downloadId) {
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val manager = AppUpdateManager(context.applicationContext)
                val verified = manager.verifyDownload(downloadId)

                NotificationChannels.ensure(context.applicationContext)

                val versionName = preferences.pendingVersionName

                if (verified && versionName != null) {
                    UpdateNotifier.sendUpdateReadyNotification(
                        context.applicationContext,
                        versionName,
                    )
                } else {
                    UpdateNotifier.sendVerificationFailedNotification(
                        context.applicationContext,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
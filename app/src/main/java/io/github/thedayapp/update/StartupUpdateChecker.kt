package io.github.thedayapp.update

import android.content.Context
import io.github.thedayapp.notification.UpdateNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs one lightweight update check when a launcher activity starts.
 * It never starts an APK download; it only posts a system notification when
 * a newer release exists.
 */
object StartupUpdateChecker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    fun check(context: Context) {
        if (!running.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        scope.launch {
            try {
                val manager = AppUpdateManager(appContext)
                val preferences = UpdatePreferences(appContext)
                when (val result = manager.checkForUpdate()) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        val version = result.release.versionName
                        if (preferences.lastNotifiedAvailableVersion != version) {
                            val posted = UpdateNotifier.sendUpdateAvailableNotification(
                                appContext,
                                version,
                            )
                            if (posted) {
                                preferences.lastNotifiedAvailableVersion = version
                            }
                        }
                    }
                    UpdateCheckResult.UpToDate -> {
                        preferences.lastNotifiedAvailableVersion = null
                        UpdateNotifier.cancelUpdateAvailableNotification(appContext)
                    }
                    UpdateCheckResult.CheckFailed -> Unit
                }
            } finally {
                running.set(false)
            }
        }
    }
}

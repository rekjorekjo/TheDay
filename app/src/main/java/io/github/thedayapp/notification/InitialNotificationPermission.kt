package io.github.thedayapp.notification

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Requests POST_NOTIFICATIONS once, only on a fresh Android 13+ install. */
object InitialNotificationPermission {
    private const val PREFS = "initial_permissions"
    private const val KEY_NOTIFICATION_REQUESTED = "notification_requested"

    fun shouldRequest(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATION_REQUESTED, false)) return false

        val packageInfo = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0)
        }.getOrNull() ?: return false

        return packageInfo.firstInstallTime == packageInfo.lastUpdateTime
    }

    fun markRequested(activity: Activity) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATION_REQUESTED, true)
            .apply()
    }
}

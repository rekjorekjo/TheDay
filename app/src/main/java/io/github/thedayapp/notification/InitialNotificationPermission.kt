package io.github.thedayapp.notification

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** 仅在 Android 13 及以上的首次安装启动时请求一次通知权限。 */
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

        val packageInfo = try {
            activity.packageManager.getPackageInfo(activity.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            // 无法确认是否为首次安装时不主动弹权限框，避免误打扰用户。
            return false
        }

        return packageInfo.firstInstallTime == packageInfo.lastUpdateTime
    }

    fun markRequested(activity: Activity) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATION_REQUESTED, true)
            .apply()
    }
}

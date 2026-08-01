package io.github.thedayapp.device

import android.content.Context
import android.os.Build

object HyperOsCompatibility {
    private const val PREFERENCES_NAME = "device_compatibility"
    private const val KEY_HIDE_WIDGET_QUICK_ADD = "hide_widget_quick_add"
    private const val KEY_CHECKED_BUILD_FINGERPRINT = "checked_build_fingerprint"

    /**
     * Performs the device check only when needed. A positive HyperOS result is
     * sticky so the quick-add entry never reappears after it has been hidden.
     * A negative result is checked again only after a system build change.
     */
    fun initialize(context: Context) {
        val preferences = preferences(context)
        if (preferences.getBoolean(KEY_HIDE_WIDGET_QUICK_ADD, false)) {
            return
        }

        val currentFingerprint = Build.FINGERPRINT.orEmpty()
        val checkedFingerprint = preferences.getString(
            KEY_CHECKED_BUILD_FINGERPRINT,
            null,
        )
        if (checkedFingerprint == currentFingerprint) {
            return
        }

        val isHyperOs = detectHyperOs()
        preferences.edit()
            .putString(KEY_CHECKED_BUILD_FINGERPRINT, currentFingerprint)
            .apply {
                if (isHyperOs) {
                    putBoolean(KEY_HIDE_WIDGET_QUICK_ADD, true)
                }
            }
            .apply()
    }

    fun shouldHideWidgetQuickAdd(context: Context): Boolean {
        return preferences(context).getBoolean(
            KEY_HIDE_WIDGET_QUICK_ADD,
            false,
        )
    }

    private fun detectHyperOs(): Boolean {
        if (!isXiaomiFamilyDevice()) {
            return false
        }

        val hyperOsName = readSystemProperty("ro.mi.os.version.name")
        if (!hyperOsName.isNullOrBlank()) {
            return true
        }

        val hyperOsCode = readSystemProperty("ro.mi.os.version.code")
            ?.toIntOrNull()
        if (hyperOsCode != null && hyperOsCode > 0) {
            return true
        }

        val miuiUiVersionCode = readSystemProperty(
            "ro.miui.ui.version.code",
        )?.toIntOrNull()
        return miuiUiVersionCode != null && miuiUiVersionCode >= 816
    }

    private fun isXiaomiFamilyDevice(): Boolean {
        return sequenceOf(
            Build.MANUFACTURER,
            Build.BRAND,
        ).filterNotNull().any { value ->
            value.equals("Xiaomi", ignoreCase = true) ||
                value.equals("Redmi", ignoreCase = true) ||
                value.equals("POCO", ignoreCase = true)
        }
    }

    private fun readSystemProperty(name: String): String? {
        return runCatching {
            ProcessBuilder("/system/bin/getprop", name)
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .use { reader ->
                    reader.readLine()?.trim()
                }
        }.getOrNull()?.takeIf(String::isNotBlank)
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
}

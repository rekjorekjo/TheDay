package io.github.thedayapp.device

import android.content.Context
import android.os.Build
import java.io.IOException

object HyperOsCompatibility {
    private const val PREFERENCES_NAME = "device_compatibility"
    private const val KEY_HIDE_WIDGET_QUICK_ADD = "hide_widget_quick_add"
    private const val KEY_CHECKED_BUILD_FINGERPRINT = "checked_build_fingerprint"

    /**
     * 仅在需要时检测设备。检测为 HyperOS 后永久隐藏快速添加入口；
     * 检测为非 HyperOS 时，仅在系统构建指纹变化后重新检查。
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
        // 厂商系统属性并非所有设备都允许读取，失败时按“未检测到”处理即可。
        val propertyValue = try {
            ProcessBuilder("/system/bin/getprop", name)
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .use { reader -> reader.readLine()?.trim() }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
        return propertyValue?.takeIf(String::isNotBlank)
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
}

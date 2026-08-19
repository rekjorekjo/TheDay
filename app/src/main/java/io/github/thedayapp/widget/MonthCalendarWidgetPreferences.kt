package io.github.thedayapp.widget

import android.content.Context
import java.time.YearMonth
import java.time.format.DateTimeParseException

object MonthCalendarWidgetPreferences {

    private const val PREFS_NAME = "month_calendar_widget_preferences"
    private const val KEY_MONTH_PREFIX = "month_"

    fun loadMonth(
        context: Context,
        appWidgetId: Int,
        fallback: YearMonth = YearMonth.now(),
    ): YearMonth {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val monthString = prefs.getString("$KEY_MONTH_PREFIX$appWidgetId", null)

        return if (monthString != null) {
            try {
                YearMonth.parse(monthString)
            } catch (_: DateTimeParseException) {
                // 旧配置或损坏数据无法解析时回到当前月份，不影响小组件继续使用。
                fallback
            }
        } else {
            fallback
        }
    }

    fun saveMonth(
        context: Context,
        appWidgetId: Int,
        month: YearMonth,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("$KEY_MONTH_PREFIX$appWidgetId", month.toString())
            .apply()
    }

    fun remove(
        context: Context,
        appWidgetId: Int,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("$KEY_MONTH_PREFIX$appWidgetId")
            .apply()
    }
}
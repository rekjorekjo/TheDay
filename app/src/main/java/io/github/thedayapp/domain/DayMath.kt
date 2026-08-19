package io.github.thedayapp.domain

import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.RepeatMode
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.max

object DayMath {
    /**
     * 计算每年重复事件在 [year] 中对应的日期；非闰年遇到 2 月 29 日时回退到 2 月 28 日。
     */
    fun annualDate(original: LocalDate, year: Int): LocalDate = try {
        LocalDate.of(year, original.month, original.dayOfMonth)
    } catch (_: DateTimeException) {
        LocalDate.of(year, 2, 28)
    }

    /**
     * 一次性事件直接返回原日期；每年重复事件返回不早于 [today] 且不早于原始年份的下一次日期。
     */
    fun effectiveDate(event: DayEvent, today: LocalDate): LocalDate {
        if (event.repeatMode == RepeatMode.NONE) return event.date

        var year = max(today.year, event.date.year)
        var candidate = annualDate(event.date, year)
        if (candidate.isBefore(today)) {
            year += 1
            candidate = annualDate(event.date, year)
        }
        return candidate
    }

    /** 正数表示剩余天数，0 表示今天，负数表示已经过去的天数。 */
    fun signedDays(event: DayEvent, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, effectiveDate(event, today))

    fun isPast(event: DayEvent, today: LocalDate): Boolean = signedDays(event, today) < 0

    fun isUpcoming(event: DayEvent, today: LocalDate): Boolean = signedDays(event, today) >= 0

    /**
     * 计算下一次本地提醒时间。每年重复事件向后寻找下一次有效提醒；
     * 一次性事件的提醒时刻已经过去时返回 null。
     */
    fun nextReminderDateTime(
        event: DayEvent,
        now: LocalDateTime,
        reminderHour: Int,
        reminderMinute: Int,
    ): LocalDateTime? {
        val daysBefore = event.reminderDaysBefore ?: return null
        val reminderTime = LocalTime.of(reminderHour, reminderMinute)

        if (event.repeatMode == RepeatMode.NONE) {
            val candidate = event.date.minusDays(daysBefore.toLong()).atTime(reminderTime)
            return candidate.takeIf { it.isAfter(now) }
        }

        var occurrenceYear = max(now.year, event.date.year)
        repeat(4) {
            val occurrence = annualDate(event.date, occurrenceYear)
            val candidate = occurrence.minusDays(daysBefore.toLong()).atTime(reminderTime)
            if (candidate.isAfter(now)) return candidate
            occurrenceYear += 1
        }

        return null
    }
}

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
     * Returns the date represented by an annual event in [year].
     * February 29 falls back to February 28 in non-leap years.
     */
    fun annualDate(original: LocalDate, year: Int): LocalDate = try {
        LocalDate.of(year, original.month, original.dayOfMonth)
    } catch (_: DateTimeException) {
        LocalDate.of(year, 2, 28)
    }

    /**
     * For one-off events this is the original date. For annual events this is
     * the first occurrence on or after [today], never earlier than the original year.
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

    /**
     * Positive: days remaining. Zero: today. Negative: days elapsed.
     */
    fun signedDays(event: DayEvent, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, effectiveDate(event, today))

    fun isPast(event: DayEvent, today: LocalDate): Boolean = signedDays(event, today) < 0

    fun isUpcoming(event: DayEvent, today: LocalDate): Boolean = signedDays(event, today) >= 0

    /**
     * Computes the next local alarm time. Annual events roll forward until the
     * reminder is strictly after [now]. One-off reminders in the past return null.
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

package io.github.thedayapp.domain

import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.RepeatMode
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayMathTest {
    @Test
    fun signedDaysUsesPositiveForFutureAndNegativeForPast() {
        val today = LocalDate.of(2026, 7, 26)
        assertEquals(4L, DayMath.signedDays(DayEvent(title = "A", date = today.plusDays(4)), today))
        assertEquals(-2L, DayMath.signedDays(DayEvent(title = "B", date = today.minusDays(2)), today))
    }

    @Test
    fun annualLeapDayFallsBackToFebruary28() {
        val event = DayEvent(
            title = "Leap",
            date = LocalDate.of(2024, 2, 29),
            repeatMode = RepeatMode.YEARLY,
        )
        assertEquals(LocalDate.of(2025, 2, 28), DayMath.effectiveDate(event, LocalDate.of(2025, 2, 1)))
    }

    @Test
    fun pastOneOffReminderIsNotScheduled() {
        val event = DayEvent(
            title = "Past",
            date = LocalDate.of(2026, 1, 1),
            reminderDaysBefore = 0,
        )
        assertNull(
            DayMath.nextReminderDateTime(
                event,
                LocalDateTime.of(2026, 7, 26, 12, 0),
                9,
                0,
            ),
        )
    }
}

package io.github.thedayapp.domain

import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.SortDirection
import io.github.thedayapp.data.SortMode
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class EventOrderingTest {
    @Test
    fun heroPrefersPinnedEvent() {
        val today = LocalDate.of(2026, 7, 26)
        val nearest = DayEvent(id = "near", title = "Near", date = today.plusDays(1))
        val pinned = DayEvent(
            id = "pinned",
            title = "Pinned",
            date = today.plusDays(30),
            isPinned = true,
        )

        assertEquals(pinned, EventOrdering.heroEvent(listOf(nearest, pinned), today))
    }

    @Test
    fun pinnedStaysFirstInAscendingOrder() {
        val today = LocalDate.of(2026, 7, 26)
        val pinned = DayEvent(
            id = "pinned",
            title = "Pinned",
            date = today.plusDays(30),
            isPinned = true,
            createdAtEpochMillis = 1000,
        )
        val nearest = DayEvent(
            id = "near",
            title = "Near",
            date = today.plusDays(1),
            createdAtEpochMillis = 2000,
        )

        val sorted = EventOrdering.sort(listOf(nearest, pinned), SortMode.DATE, SortDirection.ASCENDING, today)
        assertEquals(listOf(pinned, nearest), sorted)
    }

    @Test
    fun pinnedStaysFirstInDescendingOrder() {
        val today = LocalDate.of(2026, 7, 26)
        val pinned = DayEvent(
            id = "pinned",
            title = "Pinned",
            date = today.plusDays(30),
            isPinned = true,
            createdAtEpochMillis = 1000,
        )
        val farthest = DayEvent(
            id = "far",
            title = "Far",
            date = today.plusDays(100),
            createdAtEpochMillis = 2000,
        )

        val sorted = EventOrdering.sort(listOf(farthest, pinned), SortMode.DATE, SortDirection.DESCENDING, today)
        assertEquals(listOf(pinned, farthest), sorted)
    }

    @Test
    fun dateSortSupportsBothDirections() {
        val today = LocalDate.of(2026, 7, 26)
        val event1 = DayEvent(id = "1", title = "Event 1", date = today.plusDays(1))
        val event2 = DayEvent(id = "2", title = "Event 2", date = today.plusDays(10))
        val event3 = DayEvent(id = "3", title = "Event 3", date = today.plusDays(5))

        val ascending = EventOrdering.sort(listOf(event2, event3, event1), SortMode.DATE, SortDirection.ASCENDING, today)
        assertEquals(listOf(event1, event3, event2), ascending)

        val descending = EventOrdering.sort(listOf(event1, event3, event2), SortMode.DATE, SortDirection.DESCENDING, today)
        assertEquals(listOf(event2, event3, event1), descending)
    }

    @Test
    fun titleSortSupportsBothDirections() {
        val today = LocalDate.of(2026, 7, 26)
        val eventA = DayEvent(id = "a", title = "Alpha", date = today)
        val eventB = DayEvent(id = "b", title = "Beta", date = today)
        val eventC = DayEvent(id = "c", title = "Charlie", date = today)

        val ascending = EventOrdering.sort(listOf(eventC, eventB, eventA), SortMode.TITLE, SortDirection.ASCENDING, today)
        assertEquals(listOf(eventA, eventB, eventC), ascending)

        val descending = EventOrdering.sort(listOf(eventA, eventB, eventC), SortMode.TITLE, SortDirection.DESCENDING, today)
        assertEquals(listOf(eventC, eventB, eventA), descending)
    }

    @Test
    fun createdSortSupportsBothDirections() {
        val today = LocalDate.of(2026, 7, 26)
        val event1 = DayEvent(id = "1", title = "Event 1", date = today, createdAtEpochMillis = 1000)
        val event2 = DayEvent(id = "2", title = "Event 2", date = today, createdAtEpochMillis = 3000)
        val event3 = DayEvent(id = "3", title = "Event 3", date = today, createdAtEpochMillis = 2000)

        val ascending = EventOrdering.sort(listOf(event2, event3, event1), SortMode.CREATED, SortDirection.ASCENDING, today)
        assertEquals(listOf(event1, event3, event2), ascending)

        val descending = EventOrdering.sort(listOf(event1, event3, event2), SortMode.CREATED, SortDirection.DESCENDING, today)
        assertEquals(listOf(event2, event3, event1), descending)
    }

    @Test
    fun smartAscendingKeepsUpcomingBeforePastAndNearerFirst() {
        val today = LocalDate.of(2026, 7, 26)
        val past1 = DayEvent(id = "past1", title = "Past 1", date = today.minusDays(10))
        val past2 = DayEvent(id = "past2", title = "Past 2", date = today.minusDays(2))
        val future1 = DayEvent(id = "future1", title = "Future 1", date = today.plusDays(5))
        val future2 = DayEvent(id = "future2", title = "Future 2", date = today.plusDays(20))

        val sorted = EventOrdering.sort(
            listOf(past1, future2, past2, future1),
            SortMode.SMART,
            SortDirection.ASCENDING,
            today,
        )

        // Upcoming group first, then past group
        // Within each group, nearer first
        assertEquals(listOf(future1, future2, past2, past1), sorted)
    }

    @Test
    fun smartDescendingKeepsUpcomingBeforePastAndFartherFirst() {
        val today = LocalDate.of(2026, 7, 26)
        val past1 = DayEvent(id = "past1", title = "Past 1", date = today.minusDays(10))
        val past2 = DayEvent(id = "past2", title = "Past 2", date = today.minusDays(2))
        val future1 = DayEvent(id = "future1", title = "Future 1", date = today.plusDays(5))
        val future2 = DayEvent(id = "future2", title = "Future 2", date = today.plusDays(20))

        val sorted = EventOrdering.sort(
            listOf(past1, future1, past2, future2),
            SortMode.SMART,
            SortDirection.DESCENDING,
            today,
        )

        // Upcoming group still first, then past group
        // Within each group, farther first
        assertEquals(listOf(future2, future1, past1, past2), sorted)
    }

    @Test
    fun smartSortKeepsUpcomingBeforePast() {
        val today = LocalDate.of(2026, 7, 26)
        val past = DayEvent(id = "past", title = "Past", date = today.minusDays(1))
        val future = DayEvent(id = "future", title = "Future", date = today.plusDays(5))

        assertEquals(
            listOf(future, past),
            EventOrdering.sort(listOf(past, future), SortMode.SMART, SortDirection.ASCENDING, today),
        )
    }
}
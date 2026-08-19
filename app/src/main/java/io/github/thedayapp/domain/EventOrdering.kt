package io.github.thedayapp.domain

import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.SortDirection
import io.github.thedayapp.data.SortMode
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

object EventOrdering {
    fun sort(
        events: List<DayEvent>,
        mode: SortMode,
        direction: SortDirection,
        today: LocalDate,
    ): List<DayEvent> {
        val pinnedComparator = compareBy<DayEvent> { if (it.isPinned) 0 else 1 }

        val contentComparator = when (mode) {
            SortMode.SMART -> createSmartComparator(direction, today)
            SortMode.DATE -> createDateComparator(direction, today)
            SortMode.TITLE -> createTitleComparator(direction)
            SortMode.CREATED -> createCreatedComparator(direction)
        }

        val stabilityComparator = compareBy<DayEvent>(
            { it.createdAtEpochMillis },
            { it.id },
        )

        return events.sortedWith(pinnedComparator.then(contentComparator).then(stabilityComparator))
    }

    private fun createSmartComparator(direction: SortDirection, today: LocalDate): Comparator<DayEvent> {
        return Comparator { e1, e2 ->
            // 智能排序先区分未来与已过去事件，再按距今天数比较，避免两组日期交叉。
            val group1 = if (DayMath.signedDays(e1, today) >= 0) 0 else 1
            val group2 = if (DayMath.signedDays(e2, today) >= 0) 0 else 1
            val groupCompare = group1.compareTo(group2)
            if (groupCompare != 0) {
                groupCompare
            } else {
                val delta1 = DayMath.signedDays(e1, today)
                val delta2 = DayMath.signedDays(e2, today)
                val distance1 = if (delta1 >= 0) delta1 else abs(delta1)
                val distance2 = if (delta2 >= 0) delta2 else abs(delta2)
                val distanceCompare = distance1.compareTo(distance2)
                if (direction == SortDirection.DESCENDING) {
                    -distanceCompare
                } else {
                    distanceCompare
                }
            }
        }
    }

    private fun createDateComparator(direction: SortDirection, today: LocalDate): Comparator<DayEvent> {
        return Comparator { e1, e2 ->
            val date1 = DayMath.effectiveDate(e1, today)
            val date2 = DayMath.effectiveDate(e2, today)
            val comparison = date1.compareTo(date2)
            if (direction == SortDirection.DESCENDING) {
                -comparison
            } else {
                comparison
            }
        }
    }

    private fun createTitleComparator(direction: SortDirection): Comparator<DayEvent> {
        return Comparator { e1, e2 ->
            val comparison = e1.title.lowercase(Locale.getDefault()).compareTo(e2.title.lowercase(Locale.getDefault()))
            if (direction == SortDirection.DESCENDING) {
                -comparison
            } else {
                comparison
            }
        }
    }

    private fun createCreatedComparator(direction: SortDirection): Comparator<DayEvent> {
        return Comparator { e1, e2 ->
            val comparison = e1.createdAtEpochMillis.compareTo(e2.createdAtEpochMillis)
            if (direction == SortDirection.DESCENDING) {
                -comparison
            } else {
                comparison
            }
        }
    }

    fun heroEvent(events: List<DayEvent>, today: LocalDate): DayEvent? {
        if (events.isEmpty()) return null
        val pinned = events.filter { it.isPinned }
        val source = pinned.ifEmpty { events }
        return source.minWithOrNull(
            compareBy<DayEvent>(
                { if (DayMath.signedDays(it, today) >= 0) 0 else 1 },
                {
                    val delta = DayMath.signedDays(it, today)
                    if (delta >= 0) delta else abs(delta)
                },
            ),
        )
    }
}
package io.github.thedayapp.domain

import io.github.thedayapp.data.DayEvent
import java.time.LocalDate
import java.time.YearMonth

object CalendarEventDates {

    fun datesInMonth(
        events: List<DayEvent>,
        month: YearMonth,
    ): Set<LocalDate> {
        val dates = mutableSetOf<LocalDate>()

        for (event in events) {
            val date = when (event.repeatMode) {
                io.github.thedayapp.data.RepeatMode.YEARLY -> {
                    // 每年重复事件从原始年份起生效；闰日由 DayMath 统一回退到当年有效日期。
                    if (month.year < event.date.year) {
                        continue
                    }

                    val annualDate = DayMath.annualDate(event.date, month.year)
                    if (YearMonth.from(annualDate) == month) {
                        annualDate
                    } else {
                        continue
                    }
                }

                io.github.thedayapp.data.RepeatMode.NONE -> {
                    if (YearMonth.from(event.date) == month) {
                        event.date
                    } else {
                        continue
                    }
                }
            }

            dates.add(date)
        }

        return dates
    }
}
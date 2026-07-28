package io.github.thedayapp.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DateFormatting {
    fun longDate(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))

    fun compactDate(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    fun weekday(date: LocalDate, locale: Locale): String =
        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)

    fun time(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)
}

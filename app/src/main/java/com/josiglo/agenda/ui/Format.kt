package com.josiglo.agenda.ui

import android.content.Context
import com.josiglo.agenda.R
import com.josiglo.agenda.data.Repeat
import com.josiglo.agenda.data.RepeatFrom
import com.josiglo.agenda.data.RepeatUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

object Format {
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM")
    private val dayMonthFormat = DateTimeFormatter.ofPattern("d MMM")
    private val longDateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")

    fun time(millis: Long): String = time(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime())

    fun time(time: LocalTime): String = timeFormat.format(time)

    fun longDate(date: LocalDate): String = longDateFormat.format(date)

    fun shortDate(date: LocalDate): String = dateFormat.format(date)

    fun dayMonth(date: LocalDate): String = dayMonthFormat.format(date)

    /** "Today", "Tomorrow", a weekday within the next week, else a short date. */
    fun day(context: Context, date: LocalDate, today: LocalDate): String = when {
        date == today -> context.getString(R.string.today)
        date == today.plusDays(1) -> context.getString(R.string.tomorrow)
        date == today.minusDays(1) -> context.getString(R.string.yesterday)
        date.isAfter(today) && date.isBefore(today.plusDays(7)) ->
            date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        else -> shortDate(date)
    }

    fun dateTime(context: Context, millis: Long, today: LocalDate): String {
        val at = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        return context.getString(R.string.date_time, day(context, at.toLocalDate(), today), time(at.toLocalTime()))
    }

    fun repeat(context: Context, repeat: Repeat): String {
        val n = repeat.every
        val units = when (repeat.unit) {
            RepeatUnit.DAY -> context.resources.getQuantityString(R.plurals.days, n, n)
            RepeatUnit.WEEK -> context.resources.getQuantityString(R.plurals.weeks, n, n)
            RepeatUnit.MONTH -> context.resources.getQuantityString(R.plurals.months, n, n)
            RepeatUnit.YEAR -> context.resources.getQuantityString(R.plurals.years, n, n)
        }
        return when {
            repeat.from == RepeatFrom.DONE -> context.getString(R.string.repeat_after_done, units)
            n == 1 -> context.getString(
                when (repeat.unit) {
                    RepeatUnit.DAY -> R.string.repeat_daily
                    RepeatUnit.WEEK -> R.string.repeat_weekly
                    RepeatUnit.MONTH -> R.string.repeat_monthly
                    RepeatUnit.YEAR -> R.string.repeat_yearly
                },
            )
            else -> context.getString(R.string.repeat_every, units)
        }
    }
}

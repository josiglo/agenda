package com.josiglo.agenda.time

import com.josiglo.agenda.data.Repeat
import com.josiglo.agenda.data.RepeatFrom
import com.josiglo.agenda.data.RepeatUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Days that start at [dayStart] instead of midnight. With a day start of 3:00,
 * 1:30 on Saturday still belongs to Friday.
 */
class DayClock(val zone: ZoneId, val dayStart: LocalTime) {

    fun day(millis: Long): LocalDate =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).minusSeconds(dayStart.toSecondOfDay().toLong()).toLocalDate()

    fun start(day: LocalDate): Long = day.atTime(dayStart).atZone(zone).toInstant().toEpochMilli()

    fun end(day: LocalDate): Long = start(day.plusDays(1))

    fun today(now: Long = System.currentTimeMillis()): LocalDate = day(now)
}

object Repeats {
    /**
     * The notification time after [remindAt] for an item ticked off at
     * [doneAt]. Counted from the schedule, it is the first date after
     * [doneAt]. Counted from done, it is that many units after the day of
     * [doneAt], at the time of day of [remindAt].
     */
    fun next(remindAt: Long, repeat: Repeat, doneAt: Long, zone: ZoneId): Long {
        val base = ZonedDateTime.ofInstant(Instant.ofEpochMilli(remindAt), zone)
        return when (repeat.from) {
            RepeatFrom.SCHEDULE -> {
                // Counts from the first date, so the 31st doesn't drift to the 28th after February.
                var k = 1L
                var next = plus(base, repeat, k)
                while (next.toInstant().toEpochMilli() <= doneAt) next = plus(base, repeat, ++k)
                next.toInstant().toEpochMilli()
            }
            RepeatFrom.DONE -> {
                val doneDay = Instant.ofEpochMilli(doneAt).atZone(zone).toLocalDate()
                plus(doneDay.atTime(base.toLocalTime()).atZone(zone), repeat, 1).toInstant().toEpochMilli()
            }
        }
    }

    private fun plus(start: ZonedDateTime, repeat: Repeat, times: Long): ZonedDateTime {
        val n = repeat.every * times
        return when (repeat.unit) {
            RepeatUnit.DAY -> start.plusDays(n)
            RepeatUnit.WEEK -> start.plusWeeks(n)
            RepeatUnit.MONTH -> start.plusMonths(n)
            RepeatUnit.YEAR -> start.plusYears(n)
        }
    }
}

/** Turns a date and time picked in Quick Add into a notification time. */
object EntryTime {
    /**
     * A date without a time takes [morning]. A time without a date is today,
     * or tomorrow once that time has passed. Null without either.
     */
    fun resolve(date: LocalDate?, time: LocalTime?, now: ZonedDateTime, morning: LocalTime): Long? {
        val at = if (date != null) {
            date.atTime(time ?: morning).atZone(now.zone)
        } else {
            val today = now.toLocalDate().atTime(time ?: return null).atZone(now.zone)
            if (today.isAfter(now)) today else today.plusDays(1)
        }
        return at.toInstant().toEpochMilli()
    }
}

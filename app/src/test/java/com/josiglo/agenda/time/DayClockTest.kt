package com.josiglo.agenda.time

import com.josiglo.agenda.data.Repeat
import com.josiglo.agenda.data.RepeatFrom
import com.josiglo.agenda.data.RepeatUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class DayClockTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun local(millis: Long): LocalDateTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone)

    @Test
    fun midnightDayStartUsesCalendarDays() {
        val clock = DayClock(zone, LocalTime.MIDNIGHT)
        assertEquals(LocalDate.parse("2026-10-30"), clock.day(at("2026-10-30T23:59")))
        assertEquals(LocalDate.parse("2026-10-31"), clock.day(at("2026-10-31T00:00")))
    }

    @Test
    fun lateDayStartKeepsTheNightOnThePreviousDay() {
        val clock = DayClock(zone, LocalTime.of(3, 0))
        assertEquals(LocalDate.parse("2026-10-30"), clock.day(at("2026-10-31T01:30")))
        assertEquals(LocalDate.parse("2026-10-31"), clock.day(at("2026-10-31T03:00")))
        assertEquals(at("2026-10-31T03:00"), clock.end(LocalDate.parse("2026-10-30")))
    }

    @Test
    fun repeatFromScheduleSkipsMissedDates() {
        val repeat = Repeat(1, RepeatUnit.DAY, RepeatFrom.SCHEDULE)
        val next = Repeats.next(at("2026-10-27T09:00"), repeat, at("2026-10-30T12:00"), zone)
        assertEquals(LocalDateTime.parse("2026-10-31T09:00"), local(next))
    }

    @Test
    fun repeatFromDoneCountsFromTheDoneDay() {
        val repeat = Repeat(4, RepeatUnit.DAY, RepeatFrom.DONE)
        val next = Repeats.next(at("2026-10-28T18:00"), repeat, at("2026-10-30T21:15"), zone)
        assertEquals(LocalDateTime.parse("2026-11-03T18:00"), local(next))
    }

    @Test
    fun monthlyRepeatKeepsTheEndOfTheMonth() {
        val repeat = Repeat(1, RepeatUnit.MONTH, RepeatFrom.SCHEDULE)
        val next = Repeats.next(at("2027-01-31T08:00"), repeat, at("2027-03-01T08:00"), zone)
        assertEquals(LocalDateTime.parse("2027-03-31T08:00"), local(next))
    }

    @Test
    fun repeatKeepsTheTimeOfDayAcrossDaylightSaving() {
        val repeat = Repeat(1, RepeatUnit.DAY, RepeatFrom.SCHEDULE)
        val next = Repeats.next(at("2026-10-24T09:00"), repeat, at("2026-10-24T10:00"), zone)
        assertEquals(LocalDateTime.parse("2026-10-25T09:00"), local(next))
    }

    @Test
    fun entryTimeWithoutTimeTakesTheMorning() {
        val now = LocalDateTime.parse("2026-10-30T14:00").atZone(zone)
        val at = EntryTime.resolve(LocalDate.parse("2026-11-02"), null, now, LocalTime.of(8, 0))!!
        assertEquals(LocalDateTime.parse("2026-11-02T08:00"), local(at))
    }

    @Test
    fun entryTimeWithoutDateIsTodayOrTomorrow() {
        val now = LocalDateTime.parse("2026-10-30T14:00").atZone(zone)
        val later = EntryTime.resolve(null, LocalTime.of(18, 30), now, LocalTime.of(8, 0))!!
        val passed = EntryTime.resolve(null, LocalTime.of(7, 0), now, LocalTime.of(8, 0))!!
        assertEquals(LocalDateTime.parse("2026-10-30T18:30"), local(later))
        assertEquals(LocalDateTime.parse("2026-10-31T07:00"), local(passed))
    }

    @Test
    fun entryTimeWithoutEitherIsNull() {
        val now = LocalDateTime.parse("2026-10-30T14:00").atZone(zone)
        assertNull(EntryTime.resolve(null, null, now, LocalTime.of(8, 0)))
    }
}

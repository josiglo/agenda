package com.josiglo.agenda.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class ParseTimeTest {

    @Test
    fun hoursOnly() {
        assertEquals(LocalTime.of(7, 0), parseTime("7"))
        assertEquals(LocalTime.of(19, 0), parseTime("19"))
        assertEquals(LocalTime.of(0, 0), parseTime("0"))
    }

    @Test
    fun hoursAndMinutes() {
        assertEquals(LocalTime.of(7, 30), parseTime("730"))
        assertEquals(LocalTime.of(7, 30), parseTime("0730"))
        assertEquals(LocalTime.of(23, 59), parseTime("2359"))
        assertEquals(LocalTime.of(7, 30), parseTime("7:30"))
        assertEquals(LocalTime.of(7, 30), parseTime(" 07.30 "))
    }

    @Test
    fun invalid() {
        assertNull(parseTime(""))
        assertNull(parseTime("24"))
        assertNull(parseTime("760"))
        assertNull(parseTime("2400"))
        assertNull(parseTime("12345"))
        assertNull(parseTime("7a"))
    }
}

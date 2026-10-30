package com.josiglo.agenda.time

import java.time.LocalTime

/**
 * Reads a time typed as digits: "7" is 7:00, "730" is 7:30 and "0730" or
 * "07:30" is 7:30. Returns null if it isn't a valid time.
 */
fun parseTime(text: String): LocalTime? {
    val digits = text.trim().replace(":", "").replace(".", "")
    if (digits.isEmpty() || digits.length > 4 || !digits.all { it.isDigit() }) return null
    val hour: Int
    val minute: Int
    if (digits.length <= 2) {
        hour = digits.toInt()
        minute = 0
    } else {
        hour = digits.dropLast(2).toInt()
        minute = digits.takeLast(2).toInt()
    }
    if (hour > 23 || minute > 59) return null
    return LocalTime.of(hour, minute)
}

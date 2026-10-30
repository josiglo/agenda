package com.josiglo.agenda.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class CalendarInfo(val id: Long, val name: String, val account: String, val color: Int)

data class Event(
    val id: Long,
    val title: String,
    val begin: Long,
    val allDay: Boolean,
    val color: Int,
    val calendar: String,
    val location: String?,
) {
    /** The day the event starts on. All-day events are stored as UTC midnights. */
    val day: LocalDate
        get() = Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()).toLocalDate()
}

/** Reads the phone's calendars. Everything is empty without the calendar permission. */
object Calendars {

    fun canRead(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun list(context: Context): List<CalendarInfo> {
        if (!canRead(context)) return emptyList()
        val columns = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, columns, "${CalendarContract.Calendars.VISIBLE} = 1", null,
            "${CalendarContract.Calendars.ACCOUNT_NAME}, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) add(CalendarInfo(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3)))
            }
        }.orEmpty()
    }

    /** Events of [calendars] that overlap [from] to [to], sorted by start. */
    fun events(context: Context, calendars: Set<Long>, from: Long, to: Long): List<Event> {
        if (calendars.isEmpty() || !canRead(context)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val columns = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${calendars.joinToString { "?" }})"
        return context.contentResolver.query(
            uri, columns, selection, calendars.map { it.toString() }.toTypedArray(), CalendarContract.Instances.BEGIN,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Event(
                            id = c.getLong(0),
                            title = c.getString(1).orEmpty(),
                            begin = c.getLong(2),
                            allDay = c.getInt(3) != 0,
                            color = c.getInt(4),
                            calendar = c.getString(5).orEmpty(),
                            location = c.getString(6)?.ifBlank { null },
                        )
                    )
                }
            }
        }.orEmpty()
    }

    fun eventUri(id: Long) = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
}

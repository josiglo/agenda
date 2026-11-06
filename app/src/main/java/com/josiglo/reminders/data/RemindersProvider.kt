package com.josiglo.reminders.data

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.josiglo.reminders.data.RemindersContract.Items
import com.josiglo.reminders.settings.Settings
import com.josiglo.reminders.time.EntryTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Lets other apps, such as Quick Add, read items and add new ones. See
 * [RemindersContract].
 *
 * - query: items, optionally filtered, for example `remind_at IS NULL`, or
 *   the morning and evening times from [RemindersContract.SETTINGS_URI].
 * - insert: adds a reminder. Needs [Items.TITLE]. The notification time
 *   comes from [Items.REMIND_AT], or from [Items.DATE] and [Items.TIME], or
 *   is now with [Items.NOTIFY_NOW]. Without any of them the reminder goes to
 *   the backlog. A "kind" value from older callers is ignored.
 */
class RemindersProvider : ContentProvider() {

    private val store get() = Store.get(context!!)

    override fun onCreate() = true

    override fun getType(uri: Uri): String? = when (uri) {
        RemindersContract.ITEMS_URI -> "vnd.android.cursor.dir/vnd.com.josiglo.reminders.item"
        RemindersContract.SETTINGS_URI -> "vnd.android.cursor.item/vnd.com.josiglo.reminders.settings"
        else -> null
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        if (uri == RemindersContract.SETTINGS_URI) return settings()
        require(uri == RemindersContract.ITEMS_URI) { "Unknown URI $uri" }
        val cursor = store.cursor(projection, selection, selectionArgs, sortOrder)
        cursor.setNotificationUri(context!!.contentResolver, RemindersContract.ITEMS_URI)
        return cursor
    }

    private fun settings(): Cursor {
        val preferences = Settings.get(context!!).preferences.value
        val columns = arrayOf(RemindersContract.Settings.MORNING, RemindersContract.Settings.EVENING)
        return MatrixCursor(columns, 1).apply {
            addRow(arrayOf(hhmm(preferences.morning), hhmm(preferences.evening)))
        }
    }

    private fun hhmm(time: LocalTime) = "%02d:%02d".format(time.hour, time.minute)

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        require(uri == RemindersContract.ITEMS_URI) { "Insert into ${RemindersContract.ITEMS_URI}, not $uri" }
        val title = values?.getAsString(Items.TITLE)
        require(!title.isNullOrBlank()) { "${Items.TITLE} is required" }
        val id = store.add(
            Item(
                title = title,
                note = values.getAsString(Items.NOTE),
                place = values.getAsString(Items.PLACE),
                remindAt = remindAt(values),
            ),
        )
        return ContentUris.withAppendedId(RemindersContract.ITEMS_URI, id)
    }

    private fun remindAt(values: ContentValues): Long? {
        values.getAsLong(Items.REMIND_AT)?.let { return it }
        val date = values.getAsString(Items.DATE)?.let {
            requireNotNull(runCatching { LocalDate.parse(it) }.getOrNull()) { "${Items.DATE} must look like 2026-10-30" }
        }
        val time = values.getAsString(Items.TIME)?.let {
            requireNotNull(runCatching { LocalTime.parse(it) }.getOrNull()) { "${Items.TIME} must look like 07:30" }
        }
        val morning = Settings.get(context!!).preferences.value.morning
        EntryTime.resolve(date, time, ZonedDateTime.now(), morning)?.let { return it }
        return if (flag(values, Items.NOTIFY_NOW)) System.currentTimeMillis() else null
    }

    private fun flag(values: ContentValues, key: String): Boolean = when (val v = values.get(key)) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v == "1" || v.equals("true", ignoreCase = true)
        else -> false
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException("Items can't be changed through the provider")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException("Items can't be deleted through the provider")
}

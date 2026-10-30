package com.josiglo.agenda.data

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.josiglo.agenda.data.AgendaContract.Items
import com.josiglo.agenda.settings.Settings
import com.josiglo.agenda.time.EntryTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Lets other apps, such as Quick Add, read items and add new ones. See
 * [AgendaContract].
 *
 * - query: items, optionally filtered, for example `kind = 'task'`.
 * - insert: adds an item. Needs [Items.TITLE]. [Items.KIND] defaults to
 *   inbox. The notification time comes from [Items.REMIND_AT], or from
 *   [Items.DATE] and [Items.TIME], or is now with [Items.NOTIFY_NOW]. An
 *   inbox item with a time becomes a reminder, since the inbox holds only
 *   items without one.
 */
class AgendaProvider : ContentProvider() {

    private val store get() = Store.get(context!!)

    override fun onCreate() = true

    override fun getType(uri: Uri): String? = when (uri) {
        AgendaContract.ITEMS_URI -> "vnd.android.cursor.dir/vnd.com.josiglo.agenda.item"
        else -> null
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        require(uri == AgendaContract.ITEMS_URI) { "Unknown URI $uri" }
        val cursor = store.cursor(projection, selection, selectionArgs, sortOrder)
        cursor.setNotificationUri(context!!.contentResolver, AgendaContract.ITEMS_URI)
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        require(uri == AgendaContract.ITEMS_URI) { "Insert into ${AgendaContract.ITEMS_URI}, not $uri" }
        val title = values?.getAsString(Items.TITLE)
        require(!title.isNullOrBlank()) { "${Items.TITLE} is required" }
        val kind = values.getAsString(Items.KIND)?.let {
            requireNotNull(Kind.fromKey(it)) { "${Items.KIND} must be one of ${Kind.entries.joinToString { k -> k.key }}" }
        } ?: Kind.INBOX
        val remindAt = remindAt(values)
        val id = store.add(
            Item(
                kind = if (kind == Kind.INBOX && remindAt != null) Kind.REMINDER else kind,
                title = title,
                note = values.getAsString(Items.NOTE),
                place = values.getAsString(Items.PLACE),
                remindAt = remindAt,
            ),
        )
        return ContentUris.withAppendedId(AgendaContract.ITEMS_URI, id)
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

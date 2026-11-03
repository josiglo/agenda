package com.josiglo.reminders.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteQueryBuilder
import android.os.Build
import androidx.core.database.sqlite.transaction
import com.josiglo.reminders.data.RemindersContract.Items
import com.josiglo.reminders.notify.Notifications
import com.josiglo.reminders.time.DayClock
import com.josiglo.reminders.time.Repeats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId

/**
 * All reminders. The app UI, the notifications and the content provider all
 * write through this class, so these rules hold for every caller:
 * - Ticking off a repeating item adds its next copy right away. The ticked
 *   item stays until the day ends.
 * - Items ticked off before today are deleted by [purge], and open items
 *   with a notification on a past day move to today.
 */
class Store private constructor(private val context: Context) {

    private val db = Helper(context).writableDatabase
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    fun find(id: Long): Item? = _items.value.find { it.id == id }

    /** Adds [item] and returns its ID. */
    @Synchronized
    fun add(item: Item): Long {
        val id = insertItem(clean(item).copy(id = 0, position = nextPosition(), created = System.currentTimeMillis()))
        changed()
        return id
    }

    /** Writes the editable fields of [item]. */
    @Synchronized
    fun save(item: Item) {
        val old = find(item.id) ?: return
        var new = clean(item)
        if (new.remindAt != old.remindAt) new = new.copy(alertedAt = null)
        replaceItem(new)
        changed()
    }

    /** Ticks [id] off, or opens it again when [done] is false. */
    @Synchronized
    fun setDone(id: Long, done: Boolean, now: Long = System.currentTimeMillis()) {
        val item = find(id) ?: return
        if (item.done == done) return
        db.transaction {
            if (done) {
                val nextId = if (item.repeat != null && item.remindAt != null) {
                    val next = Repeats.next(item.remindAt, item.repeat, now, ZoneId.systemDefault())
                    insertItem(item.copy(id = 0, remindAt = next, doneAt = null, alertedAt = null, nextId = null,
                        position = nextPosition(), created = now))
                } else {
                    null
                }
                replaceItem(item.copy(doneAt = now, nextId = nextId))
            } else {
                // Undo takes back the next copy, unless it was worked on already.
                item.nextId?.let { next ->
                    val copy = find(next)
                    if (copy != null && !copy.done) delete(TABLE, "${Items.ID} = ?", arrayOf(next.toString()))
                }
                replaceItem(item.copy(doneAt = null, nextId = null))
            }
        }
        changed()
    }

    /**
     * Puts the items of [order] in that order. Only the places they hold
     * among themselves change, so other items keep theirs.
     */
    @Synchronized
    fun reorder(order: List<Item>) {
        val places = order.map { it.position }.sorted()
        db.transaction {
            order.zip(places).forEach { (item, place) ->
                update(TABLE, ContentValues().apply { put(Columns.POSITION, place) },
                    "${Items.ID} = ?", arrayOf(item.id.toString()))
            }
        }
        changed()
    }

    /** Moves the notification of [id] to [at]. */
    @Synchronized
    fun reschedule(id: Long, at: Long) {
        val item = find(id) ?: return
        replaceItem(clean(item.copy(remindAt = at, alertedAt = null)))
        changed()
    }

    /** Remembers that the notification for [at] alerted, so posting it again stays silent. */
    @Synchronized
    fun markAlerted(id: Long, at: Long) {
        val item = find(id) ?: return
        if (item.alertedAt == at) return
        replaceItem(item.copy(alertedAt = at))
        _items.value = load()
    }

    /** Deletes [id] and returns it for undo. */
    @Synchronized
    fun delete(id: Long): Item? {
        val gone = find(id)
        db.delete(TABLE, "${Items.ID} = ?", arrayOf(id.toString()))
        changed()
        return gone
    }

    /** Writes [item] back exactly as it was, for undo. */
    @Synchronized
    fun restore(item: Item) {
        replaceItem(item)
        changed()
    }

    /**
     * Deletes items ticked off before today and moves open items with a
     * notification on a past day to today at the same time, so nothing stays
     * behind on a day that is over.
     */
    @Synchronized
    fun purge(clock: DayClock) {
        val today = clock.today()
        val start = clock.start(today)
        val zone = clock.zone
        val missed = _items.value.filter { !it.done && it.remindAt != null && it.remindAt < start }
        db.transaction {
            missed.forEach { item ->
                val time = Instant.ofEpochMilli(item.remindAt!!).atZone(zone).toLocalTime()
                var at = today.atTime(time).atZone(zone).toInstant().toEpochMilli()
                // With a late day start, an early time belongs to the next calendar day.
                if (at < start) at = today.plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli()
                replaceItem(item.copy(remindAt = at, alertedAt = null))
            }
            delete(TABLE, "${Items.DONE_AT} < ?", arrayOf(start.toString()))
        }
        changed()
    }

    /** Places used by any item, sorted by name. */
    fun places(): List<String> =
        _items.value.mapNotNull { it.place }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

    /** Renames [old] to [new] on every item. A blank [new] removes the place. */
    @Synchronized
    fun renamePlace(old: String, new: String?) {
        db.update(TABLE, ContentValues().apply { put(Items.PLACE, new?.trim()?.ifEmpty { null }) },
            "${Items.PLACE} = ?", arrayOf(old))
        changed()
    }

    /** Replaces all items with [items], for restoring a backup. */
    @Synchronized
    fun replaceAll(items: List<Item>) {
        db.transaction {
            delete(TABLE, null, null)
            items.forEach { replaceItem(it) }
        }
        changed()
    }

    /** Raw cursor for the content provider. */
    fun cursor(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor = SQLiteQueryBuilder().run {
        tables = TABLE
        isStrict = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            isStrictColumns = true
            isStrictGrammar = true
        }
        projectionMap = PUBLIC_COLUMNS.associateWith { it }
        query(db, projection, selection, selectionArgs, null, null, sortOrder ?: ORDER)
    }

    private fun clean(item: Item): Item {
        val title = item.title.trim()
        require(title.isNotEmpty()) { "Title is empty" }
        val base = item.copy(
            title = title,
            note = item.note?.trim()?.ifEmpty { null },
            place = item.place?.trim()?.ifEmpty { null },
        )
        return if (base.remindAt == null) base.copy(repeat = null) else base
    }

    private fun insertItem(item: Item): Long = db.insertOrThrow(TABLE, null, values(item).apply { remove(Items.ID) })

    private fun replaceItem(item: Item) {
        db.insertWithOnConflict(TABLE, null, values(item), SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun values(item: Item) = ContentValues().apply {
        put(Items.ID, item.id)
        put(Items.TITLE, item.title)
        put(Items.NOTE, item.note)
        put(Items.PLACE, item.place)
        put(Columns.POSITION, item.position)
        put(Items.REMIND_AT, item.remindAt)
        put(Columns.REPEAT_EVERY, item.repeat?.every)
        put(Columns.REPEAT_UNIT, item.repeat?.unit?.key)
        put(Columns.REPEAT_FROM, item.repeat?.from?.key)
        put(Items.DONE_AT, item.doneAt)
        put(Columns.ALERTED_AT, item.alertedAt)
        put(Columns.NEXT_ID, item.nextId)
        put(Columns.CREATED, item.created)
    }

    private fun changed() {
        _items.value = load()
        context.contentResolver.notifyChange(RemindersContract.ITEMS_URI, null)
        Notifications.sync(context)
    }

    private fun nextPosition(): Long =
        DatabaseUtils.longForQuery(db, "SELECT COALESCE(MAX(${Columns.POSITION}), -1) + 1 FROM $TABLE", null)

    private fun load(): List<Item> = db.query(TABLE, COLUMNS.toTypedArray(), null, null, null, null, ORDER).use { c ->
        buildList {
            while (c.moveToNext()) {
                val unit = RepeatUnit.fromKey(c.getString(7))
                val from = RepeatFrom.fromKey(c.getString(8))
                add(
                    Item(
                        id = c.getLong(0),
                        title = c.getString(1),
                        note = c.getString(2),
                        place = c.getString(3),
                        position = c.getLong(4),
                        remindAt = c.longOrNull(5),
                        repeat = if (unit != null && from != null && c.getInt(6) >= 1) Repeat(c.getInt(6), unit, from) else null,
                        doneAt = c.longOrNull(9),
                        alertedAt = c.longOrNull(10),
                        nextId = c.longOrNull(11),
                        created = c.getLong(12),
                    )
                )
            }
        }
    }

    private fun Cursor.longOrNull(i: Int): Long? = if (isNull(i)) null else getLong(i)

    private class Helper(context: Context) : SQLiteOpenHelper(context, "reminders.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) = createTable(db, TABLE)

        // Version 1 had inbox entries, tasks with subtasks and a top 3. All of
        // them become plain reminders, and subtasks stand on their own.
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                createTable(db, "items_v2")
                val columns = COLUMNS.joinToString()
                db.execSQL("INSERT INTO items_v2 ($columns) SELECT $columns FROM $TABLE")
                db.execSQL("DROP TABLE $TABLE")
                db.execSQL("ALTER TABLE items_v2 RENAME TO $TABLE")
            }
        }

        private fun createTable(db: SQLiteDatabase, name: String) {
            db.execSQL(
                """
                CREATE TABLE $name (
                    ${Items.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${Items.TITLE} TEXT NOT NULL,
                    ${Items.NOTE} TEXT,
                    ${Items.PLACE} TEXT,
                    ${Columns.POSITION} INTEGER NOT NULL DEFAULT 0,
                    ${Items.REMIND_AT} INTEGER,
                    ${Columns.REPEAT_EVERY} INTEGER,
                    ${Columns.REPEAT_UNIT} TEXT,
                    ${Columns.REPEAT_FROM} TEXT,
                    ${Items.DONE_AT} INTEGER,
                    ${Columns.ALERTED_AT} INTEGER,
                    ${Columns.NEXT_ID} INTEGER,
                    ${Columns.CREATED} INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    // Columns the content provider doesn't expose.
    private object Columns {
        const val POSITION = "position"
        const val REPEAT_EVERY = "repeat_every"
        const val REPEAT_UNIT = "repeat_unit"
        const val REPEAT_FROM = "repeat_from"
        const val ALERTED_AT = "alerted_at"
        const val NEXT_ID = "next_id"
        const val CREATED = "created"
    }

    companion object {
        private const val TABLE = "items"

        private val COLUMNS = listOf(
            Items.ID, Items.TITLE, Items.NOTE, Items.PLACE, Columns.POSITION, Items.REMIND_AT,
            Columns.REPEAT_EVERY, Columns.REPEAT_UNIT, Columns.REPEAT_FROM, Items.DONE_AT,
            Columns.ALERTED_AT, Columns.NEXT_ID, Columns.CREATED,
        )

        private val PUBLIC_COLUMNS = listOf(Items.ID, Items.TITLE, Items.NOTE, Items.PLACE, Items.REMIND_AT, Items.DONE_AT)

        private const val ORDER = Columns.POSITION

        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(context.applicationContext).also { instance = it }
        }
    }
}

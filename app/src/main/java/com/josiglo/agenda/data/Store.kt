package com.josiglo.agenda.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteQueryBuilder
import android.os.Build
import androidx.core.database.sqlite.transaction
import com.josiglo.agenda.data.AgendaContract.Items
import com.josiglo.agenda.notify.Reminders
import com.josiglo.agenda.time.DayClock
import com.josiglo.agenda.time.Repeats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId

/**
 * All items. The app UI, the notifications and the content provider all write
 * through this class, so these rules hold for every caller:
 * - Inbox items have no notification time and don't repeat.
 * - Ticking off a repeating item adds its next copy right away. The ticked
 *   item stays until the day ends.
 * - Items ticked off before today are deleted by [purge].
 * - A top 3 slot holds one item per day.
 */
class Store private constructor(private val context: Context) {

    private val db = Helper(context).writableDatabase
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    fun find(id: Long): Item? = _items.value.find { it.id == id }

    fun subtasks(parentId: Long): List<Item> = _items.value.filter { it.parentId == parentId }

    /** Adds [item] with new subtasks titled [subtasks] and returns its ID. */
    @Synchronized
    fun add(item: Item, subtasks: List<String> = emptyList()): Long {
        var id = 0L
        db.transaction {
            id = insertItem(clean(item).copy(id = 0, position = nextPosition(), created = System.currentTimeMillis()))
            subtasks.filter { it.isNotBlank() }.forEach { title ->
                insertItem(Item(kind = Kind.TASK, title = title.trim(), parentId = id, position = nextPosition(),
                    created = System.currentTimeMillis()))
            }
        }
        changed()
        return id
    }

    /**
     * Writes [item] and makes [subtasks] its subtasks. Subtasks with ID 0 are
     * added, and subtasks missing from the list are deleted.
     */
    @Synchronized
    fun save(item: Item, subtasks: List<Item>) {
        val old = find(item.id) ?: return
        var new = clean(item)
        if (new.remindAt != old.remindAt) new = new.copy(alertedAt = null)
        db.transaction {
            replaceItem(new)
            val keep = subtasks.filter { it.id != 0L }.map { it.id }.toSet()
            subtasks(item.id).filter { it.id !in keep }.forEach { delete(TABLE, "${Items.ID} = ?", arrayOf(it.id.toString())) }
            subtasks.forEach { sub ->
                val title = sub.title.trim()
                if (title.isEmpty()) {
                    if (sub.id != 0L) delete(TABLE, "${Items.ID} = ?", arrayOf(sub.id.toString()))
                } else if (sub.id == 0L) {
                    insertItem(sub.copy(kind = Kind.TASK, title = title, parentId = item.id, position = nextPosition(),
                        created = System.currentTimeMillis()))
                } else {
                    val stored = find(sub.id) ?: return@forEach
                    replaceItem(stored.copy(title = title, doneAt = sub.doneAt))
                }
            }
        }
        changed()
    }

    /** Ticks [id] off, or opens it again when [done] is false. */
    @Synchronized
    fun setDone(id: Long, done: Boolean, now: Long = System.currentTimeMillis()) {
        val item = find(id) ?: return
        if (item.done == done) return
        db.transaction {
            if (done) {
                var nextId: Long? = null
                if (item.repeat != null && item.remindAt != null) {
                    val next = Repeats.next(item.remindAt, item.repeat, now, ZoneId.systemDefault())
                    nextId = insertItem(item.copy(id = 0, remindAt = next, doneAt = null, topSlot = null, topDay = null,
                        alertedAt = null, nextId = null, position = nextPosition(), created = now))
                    subtasks(item.id).forEach { sub ->
                        // Subtask notifications move along with the task.
                        insertItem(sub.copy(id = 0, parentId = nextId, doneAt = null, topSlot = null, topDay = null,
                            remindAt = sub.remindAt?.let { it + next - item.remindAt }, alertedAt = null, nextId = null,
                            position = nextPosition(), created = now))
                    }
                }
                replaceItem(item.copy(doneAt = now, nextId = nextId))
            } else {
                // Undo takes back the next copy, unless it was worked on already.
                item.nextId?.let { next ->
                    val copy = find(next)
                    if (copy != null && !copy.done) deleteWithSubtasks(next)
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
        replaceItem(clean(item.copy(remindAt = at, alertedAt = null, kind = if (item.kind == Kind.INBOX) Kind.REMINDER else item.kind)))
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

    /** Deletes [id] and its subtasks and returns them for undo. */
    @Synchronized
    fun delete(id: Long): List<Item> {
        val gone = listOfNotNull(find(id)) + subtasks(id)
        db.transaction { deleteWithSubtasks(id) }
        changed()
        return gone
    }

    /** Writes [items] back exactly as they were, for undo. */
    @Synchronized
    fun restore(items: List<Item>) {
        db.transaction { items.forEach { replaceItem(it) } }
        changed()
    }

    /** Puts [id] into [slot] of the top 3 of [day], in place of what was there. */
    @Synchronized
    fun setTop(id: Long, slot: Int, day: Long) {
        require(slot in 1..3) { "slot must be 1 to 3" }
        db.transaction {
            update(TABLE, ContentValues().apply {
                putNull(Columns.TOP_SLOT)
                putNull(Columns.TOP_DAY)
            }, "(${Columns.TOP_SLOT} = ? AND ${Columns.TOP_DAY} = ?) OR ${Items.ID} = ?",
                arrayOf(slot.toString(), day.toString(), id.toString()))
            update(TABLE, ContentValues().apply {
                put(Columns.TOP_SLOT, slot)
                put(Columns.TOP_DAY, day)
            }, "${Items.ID} = ?", arrayOf(id.toString()))
        }
        changed()
    }

    @Synchronized
    fun clearTop(id: Long) {
        db.update(TABLE, ContentValues().apply {
            putNull(Columns.TOP_SLOT)
            putNull(Columns.TOP_DAY)
        }, "${Items.ID} = ?", arrayOf(id.toString()))
        changed()
    }

    /** Deletes items ticked off before today and empties top 3 slots of past days. */
    @Synchronized
    fun purge(clock: DayClock) {
        val today = clock.today()
        val start = clock.start(today).toString()
        db.transaction {
            delete(TABLE, "${Items.PARENT_ID} IN (SELECT ${Items.ID} FROM $TABLE WHERE ${Items.DONE_AT} < ?)", arrayOf(start))
            delete(TABLE, "${Items.DONE_AT} < ?", arrayOf(start))
            update(TABLE, ContentValues().apply {
                putNull(Columns.TOP_SLOT)
                putNull(Columns.TOP_DAY)
            }, "${Columns.TOP_DAY} < ?", arrayOf(today.toEpochDay().toString()))
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
        return when {
            base.parentId != null && base.kind != Kind.TASK -> clean(base.copy(kind = Kind.TASK))
            base.kind == Kind.INBOX -> base.copy(remindAt = null, repeat = null)
            base.remindAt == null -> base.copy(repeat = null)
            else -> base
        }
    }

    private fun deleteWithSubtasks(id: Long) {
        db.delete(TABLE, "${Items.ID} = ? OR ${Items.PARENT_ID} = ?", arrayOf(id.toString(), id.toString()))
    }

    private fun insertItem(item: Item): Long = db.insertOrThrow(TABLE, null, values(item).apply { remove(Items.ID) })

    private fun replaceItem(item: Item) {
        db.insertWithOnConflict(TABLE, null, values(item), SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun values(item: Item) = ContentValues().apply {
        put(Items.ID, item.id)
        put(Items.KIND, item.kind.key)
        put(Items.TITLE, item.title)
        put(Items.NOTE, item.note)
        put(Items.PLACE, item.place)
        put(Items.PARENT_ID, item.parentId)
        put(Columns.POSITION, item.position)
        put(Items.REMIND_AT, item.remindAt)
        put(Columns.REPEAT_EVERY, item.repeat?.every)
        put(Columns.REPEAT_UNIT, item.repeat?.unit?.key)
        put(Columns.REPEAT_FROM, item.repeat?.from?.key)
        put(Items.DONE_AT, item.doneAt)
        put(Columns.TOP_SLOT, item.topSlot)
        put(Columns.TOP_DAY, item.topDay)
        put(Columns.ALERTED_AT, item.alertedAt)
        put(Columns.NEXT_ID, item.nextId)
        put(Columns.CREATED, item.created)
    }

    private fun changed() {
        _items.value = load()
        context.contentResolver.notifyChange(AgendaContract.ITEMS_URI, null)
        Reminders.sync(context)
    }

    private fun nextPosition(): Long =
        DatabaseUtils.longForQuery(db, "SELECT COALESCE(MAX(${Columns.POSITION}), -1) + 1 FROM $TABLE", null)

    private fun load(): List<Item> = db.query(TABLE, COLUMNS.toTypedArray(), null, null, null, null, ORDER).use { c ->
        buildList {
            while (c.moveToNext()) {
                val unit = RepeatUnit.fromKey(c.getString(9))
                val from = RepeatFrom.fromKey(c.getString(10))
                add(
                    Item(
                        id = c.getLong(0),
                        kind = Kind.fromKey(c.getString(1)) ?: Kind.INBOX,
                        title = c.getString(2),
                        note = c.getString(3),
                        place = c.getString(4),
                        parentId = c.longOrNull(5),
                        position = c.getLong(6),
                        remindAt = c.longOrNull(7),
                        repeat = if (unit != null && from != null && c.getInt(8) >= 1) Repeat(c.getInt(8), unit, from) else null,
                        doneAt = c.longOrNull(11),
                        topSlot = c.longOrNull(12)?.toInt(),
                        topDay = c.longOrNull(13),
                        alertedAt = c.longOrNull(14),
                        nextId = c.longOrNull(15),
                        created = c.getLong(16),
                    )
                )
            }
        }
    }

    private fun Cursor.longOrNull(i: Int): Long? = if (isNull(i)) null else getLong(i)

    private class Helper(context: Context) : SQLiteOpenHelper(context, "agenda.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    ${Items.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${Items.KIND} TEXT NOT NULL,
                    ${Items.TITLE} TEXT NOT NULL,
                    ${Items.NOTE} TEXT,
                    ${Items.PLACE} TEXT,
                    ${Items.PARENT_ID} INTEGER,
                    ${Columns.POSITION} INTEGER NOT NULL DEFAULT 0,
                    ${Items.REMIND_AT} INTEGER,
                    ${Columns.REPEAT_EVERY} INTEGER,
                    ${Columns.REPEAT_UNIT} TEXT,
                    ${Columns.REPEAT_FROM} TEXT,
                    ${Items.DONE_AT} INTEGER,
                    ${Columns.TOP_SLOT} INTEGER,
                    ${Columns.TOP_DAY} INTEGER,
                    ${Columns.ALERTED_AT} INTEGER,
                    ${Columns.NEXT_ID} INTEGER,
                    ${Columns.CREATED} INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    // Columns the content provider doesn't expose.
    private object Columns {
        const val POSITION = "position"
        const val REPEAT_EVERY = "repeat_every"
        const val REPEAT_UNIT = "repeat_unit"
        const val REPEAT_FROM = "repeat_from"
        const val TOP_SLOT = "top_slot"
        const val TOP_DAY = "top_day"
        const val ALERTED_AT = "alerted_at"
        const val NEXT_ID = "next_id"
        const val CREATED = "created"
    }

    companion object {
        private const val TABLE = "items"


        private val COLUMNS = listOf(
            Items.ID, Items.KIND, Items.TITLE, Items.NOTE, Items.PLACE, Items.PARENT_ID, Columns.POSITION,
            Items.REMIND_AT, Columns.REPEAT_EVERY, Columns.REPEAT_UNIT, Columns.REPEAT_FROM, Items.DONE_AT,
            Columns.TOP_SLOT, Columns.TOP_DAY, Columns.ALERTED_AT, Columns.NEXT_ID, Columns.CREATED,
        )

        private val PUBLIC_COLUMNS = listOf(
            Items.ID, Items.KIND, Items.TITLE, Items.NOTE, Items.PLACE, Items.PARENT_ID, Items.REMIND_AT, Items.DONE_AT,
        )

        private const val ORDER = Columns.POSITION

        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(context.applicationContext).also { instance = it }
        }
    }
}

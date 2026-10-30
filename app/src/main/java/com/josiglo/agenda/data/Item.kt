package com.josiglo.agenda.data

import androidx.annotation.StringRes
import com.josiglo.agenda.R

enum class Kind(val key: String, @StringRes val label: Int) {
    INBOX("inbox", R.string.inbox),
    TASK("task", R.string.task),
    REMINDER("reminder", R.string.reminder);

    companion object {
        fun fromKey(key: String?): Kind? = entries.find { it.key == key }
    }
}

enum class RepeatUnit(val key: String) {
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year");

    companion object {
        fun fromKey(key: String?): RepeatUnit? = entries.find { it.key == key }
    }
}

/** Where the next date of a repeating item is counted from. */
enum class RepeatFrom(val key: String) {
    /** From the notification time, like every Monday at 8:00. */
    SCHEDULE("schedule"),

    /** From the day the item was ticked off, like 4 days after done. */
    DONE("done");

    companion object {
        fun fromKey(key: String?): RepeatFrom? = entries.find { it.key == key }
    }
}

data class Repeat(val every: Int, val unit: RepeatUnit, val from: RepeatFrom) {
    init {
        require(every >= 1) { "every must be at least 1" }
    }
}

/**
 * A task, reminder or inbox entry. Subtasks are items with a [parentId]. They
 * use only the title and the done time.
 */
data class Item(
    val id: Long = 0,
    val kind: Kind,
    val title: String,
    val note: String? = null,
    val place: String? = null,
    val parentId: Long? = null,
    val position: Long = 0,
    /** Epoch milliseconds of the notification, or null for items in the backlog. */
    val remindAt: Long? = null,
    val repeat: Repeat? = null,
    /** Epoch milliseconds when the item was ticked off, or null while open. */
    val doneAt: Long? = null,
    /** Slot 1 to 3 in the top 3 of [topDay], or null. */
    val topSlot: Int? = null,
    /** Epoch day of the top 3 that holds [topSlot]. */
    val topDay: Long? = null,
    /** The [remindAt] whose notification already alerted, so posting it again stays silent. */
    val alertedAt: Long? = null,
    /** The next copy a repeating item created when it was ticked off, removed again on undo. */
    val nextId: Long? = null,
    val created: Long = 0,
) {
    val done: Boolean get() = doneAt != null
    val isSubtask: Boolean get() = parentId != null
}

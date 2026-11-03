package com.josiglo.reminders.data

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

/** A reminder. Without a notification time it waits in the backlog. */
data class Item(
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    val place: String? = null,
    val position: Long = 0,
    /** Epoch milliseconds of the notification, or null for items in the backlog. */
    val remindAt: Long? = null,
    val repeat: Repeat? = null,
    /** Epoch milliseconds when the item was ticked off, or null while open. */
    val doneAt: Long? = null,
    /** The [remindAt] whose notification already alerted, so posting it again stays silent. */
    val alertedAt: Long? = null,
    /** The next copy a repeating item created when it was ticked off, removed again on undo. */
    val nextId: Long? = null,
    val created: Long = 0,
) {
    val done: Boolean get() = doneAt != null
}

package com.josiglo.agenda.data

import android.net.Uri
import androidx.core.net.toUri

/**
 * Public API of the Agenda content provider. Other apps need the READ_ITEMS
 * permission to query and WRITE_ITEMS to insert.
 */
object AgendaContract {
    const val AUTHORITY = "com.josiglo.agenda.api"
    const val PERMISSION_READ = "com.josiglo.agenda.permission.READ_ITEMS"
    const val PERMISSION_WRITE = "com.josiglo.agenda.permission.WRITE_ITEMS"

    /** All items. Append an item ID for a single item. */
    val ITEMS_URI: Uri = "content://$AUTHORITY/v0/items".toUri()

    object Items {
        const val ID = "_id"

        /** "inbox", "task" or "reminder". */
        const val KIND = "kind"
        const val TITLE = "title"

        /** The description, or null. */
        const val NOTE = "note"

        /** Free text such as "Home", or null. */
        const val PLACE = "place"

        /** The ID of the task a subtask belongs to, or null. Read only. */
        const val PARENT_ID = "parent_id"

        /** Epoch milliseconds of the notification, or null. */
        const val REMIND_AT = "remind_at"

        /** Epoch milliseconds when the item was ticked off, or null. Read only. */
        const val DONE_AT = "done_at"

        /**
         * Insert only. The notification date as yyyy-MM-dd. Without [TIME] it
         * takes the morning time from the Agenda settings.
         */
        const val DATE = "date"

        /**
         * Insert only. The notification time as HH:mm. Without [DATE] it is
         * today, or tomorrow once that time has passed.
         */
        const val TIME = "time"

        /** Insert only. 1 to show the notification right away. */
        const val NOTIFY_NOW = "notify_now"
    }
}

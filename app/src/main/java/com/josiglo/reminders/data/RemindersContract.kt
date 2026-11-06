package com.josiglo.reminders.data

import android.net.Uri
import androidx.core.net.toUri

/**
 * Public API of the Reminders content provider. Other apps need the READ_ITEMS
 * permission to query and WRITE_ITEMS to insert.
 */
object RemindersContract {
    const val AUTHORITY = "com.josiglo.reminders.api"
    const val PERMISSION_READ = "com.josiglo.reminders.permission.READ_ITEMS"
    const val PERMISSION_WRITE = "com.josiglo.reminders.permission.WRITE_ITEMS"

    /** All items. Append an item ID for a single item. */
    val ITEMS_URI: Uri = "content://$AUTHORITY/v0/items".toUri()

    object Items {
        const val ID = "_id"

        const val TITLE = "title"

        /** The description, or null. */
        const val NOTE = "note"

        /** Free text such as "Home", or null. */
        const val PLACE = "place"

        /** Epoch milliseconds of the notification, or null. */
        const val REMIND_AT = "remind_at"

        /** Epoch milliseconds when the item was ticked off, or null. Read only. */
        const val DONE_AT = "done_at"

        /**
         * Insert only. The notification date as yyyy-MM-dd. Without [TIME] it
         * takes the morning time from the Reminders settings.
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

    /** One row with the times callers offer as shortcuts. Query only. */
    val SETTINGS_URI: Uri = "content://$AUTHORITY/v0/settings".toUri()

    object Settings {
        /** The morning time as HH:mm. */
        const val MORNING = "morning"

        /** The evening time as HH:mm. */
        const val EVENING = "evening"
    }
}

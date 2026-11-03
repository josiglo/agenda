package com.josiglo.reminders.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.josiglo.reminders.data.Store
import com.josiglo.reminders.settings.Settings

/** The alarm and the notification buttons. Not exported. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ALARM = "com.josiglo.reminders.ALARM"
        const val ACTION_DONE = "com.josiglo.reminders.DONE"
        const val ACTION_DISMISSED = "com.josiglo.reminders.DISMISSED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val store = Store.get(context)
        val id = intent.getLongExtra(Notifications.EXTRA_ITEM, 0)
        when (intent.action) {
            // Purging changes the store, which syncs the notifications.
            ACTION_ALARM -> store.purge(Settings.get(context).preferences.value.clock())
            ACTION_DONE -> store.setDone(id, true)
            ACTION_DISMISSED -> Notifications.sync(context)
        }
    }
}

package com.josiglo.agenda.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.josiglo.agenda.data.Store
import com.josiglo.agenda.settings.Settings

/** The alarm and the notification buttons. Not exported. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ALARM = "com.josiglo.agenda.ALARM"
        const val ACTION_DONE = "com.josiglo.agenda.DONE"
        const val ACTION_DISMISSED = "com.josiglo.agenda.DISMISSED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val store = Store.get(context)
        val id = intent.getLongExtra(Reminders.EXTRA_ITEM, 0)
        when (intent.action) {
            // Purging changes the store, which syncs the notifications.
            ACTION_ALARM -> store.purge(Settings.get(context).preferences.value.clock())
            ACTION_DONE -> store.setDone(id, true)
            ACTION_DISMISSED -> Reminders.sync(context)
        }
    }
}

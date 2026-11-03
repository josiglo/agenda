package com.josiglo.reminders.notify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.josiglo.reminders.data.Store
import com.josiglo.reminders.ui.RemindersTheme
import com.josiglo.reminders.ui.WhenOptions

/** The Reschedule button of a notification: a few shortcuts and a date and time picker. */
class RescheduleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(Notifications.EXTRA_ITEM, 0)
        val item = Store.get(this).find(id)
        if (item == null) {
            finish()
            return
        }
        setContent {
            RemindersTheme(edgeToEdge = false) {
                WhenOptions(
                    title = item.title,
                    initial = item.remindAt ?: System.currentTimeMillis(),
                    onPicked = {
                        Store.get(this).reschedule(id, it)
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }
}

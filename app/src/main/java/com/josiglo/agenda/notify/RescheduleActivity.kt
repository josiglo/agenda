package com.josiglo.agenda.notify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.josiglo.agenda.R
import com.josiglo.agenda.data.Store
import com.josiglo.agenda.settings.Settings
import com.josiglo.agenda.ui.AgendaTheme
import com.josiglo.agenda.ui.DateTimePicker
import com.josiglo.agenda.ui.Format
import java.time.ZoneId
import java.time.ZonedDateTime

/** The Reschedule button of a notification: a few shortcuts and a date and time picker. */
class RescheduleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(Reminders.EXTRA_ITEM, 0)
        val item = Store.get(this).find(id)
        if (item == null) {
            finish()
            return
        }
        setContent {
            AgendaTheme(edgeToEdge = false) {
                RescheduleDialog(
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

@Composable
private fun RescheduleDialog(title: String, initial: Long, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        DateTimePicker(initial, onPicked, onDismiss)
        return
    }
    val preferences by Settings.get(context).preferences.collectAsStateWithLifecycle()
    val now = ZonedDateTime.now(ZoneId.systemDefault())
    val today = now.toLocalDate()
    val evening = today.atTime(preferences.evening).atZone(now.zone)
    val morning = today.plusDays(1).atTime(preferences.morning).atZone(now.zone)
    val options = buildList {
        add(resources.getString(R.string.in_one_hour) to now.plusHours(1))
        if (evening.isAfter(now.plusMinutes(30))) add(resources.getString(R.string.this_evening, Format.time(preferences.evening)) to evening)
        add(resources.getString(R.string.tomorrow_morning, Format.time(preferences.morning)) to morning)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, at) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPicked(at.toInstant().toEpochMilli()) },
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.pick_date_time)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { picking = true },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

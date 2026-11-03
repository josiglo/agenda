package com.josiglo.reminders.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.josiglo.reminders.R
import com.josiglo.reminders.data.Item
import com.josiglo.reminders.data.Store
import com.josiglo.reminders.settings.Settings
import com.josiglo.reminders.time.EntryTime
import com.josiglo.reminders.ui.DayDialog
import com.josiglo.reminders.ui.Format
import com.josiglo.reminders.ui.RemindersTheme
import com.josiglo.reminders.ui.TimeDialog
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Text shared from another app, split into the title and the description of a new reminder. */
data class SharedText(val title: String, val note: String?) {
    companion object {
        /**
         * The subject becomes the title and the text the description. Without a
         * subject, the first line is the title and the rest the description.
         */
        fun from(subject: String?, text: String?): SharedText? {
            val body = text?.trim().orEmpty()
            val head = subject?.trim().orEmpty()
            if (head.isNotEmpty()) return SharedText(head, body.ifEmpty { null })
            if (body.isEmpty()) return null
            val lines = body.lines()
            return SharedText(lines.first().trim(), lines.drop(1).joinToString("\n").trim().ifEmpty { null })
        }
    }
}

/**
 * Opens a sheet over the sharing app to save shared text as a reminder. It
 * goes to the backlog unless a date, a time or Notify now is picked.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = SharedText.from(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
        if (shared == null) {
            finish()
            return
        }
        setContent {
            RemindersTheme(edgeToEdge = false) {
                ShareSheet(shared, onDone = ::finish)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(shared: SharedText, onDone: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(shared.title) }
    var note by remember { mutableStateOf(shared.note.orEmpty()) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var notifyNow by remember { mutableStateOf(false) }
    var pickingDay by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    fun save() {
        val now = ZonedDateTime.now()
        val remindAt = EntryTime.resolve(date, time, now, Settings.get(context).preferences.value.morning)
            ?: if (notifyNow) now.toInstant().toEpochMilli() else null
        Store.get(context).add(Item(title = title, note = note, remindAt = remindAt))
        onDone()
    }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.description)) },
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = date != null,
                    onClick = { pickingDay = true },
                    label = { Text(date?.let { Format.day(context, it, today) } ?: stringResource(R.string.date)) },
                    leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(FilterChipDefaults.IconSize)) },
                )
                FilterChip(
                    selected = time != null,
                    onClick = { pickingTime = true },
                    label = { Text(time?.let { Format.time(it) } ?: stringResource(R.string.time)) },
                    leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(FilterChipDefaults.IconSize)) },
                )
                FilterChip(
                    selected = notifyNow,
                    onClick = {
                        notifyNow = !notifyNow
                        if (notifyNow) {
                            date = null
                            time = null
                        }
                    },
                    label = { Text(stringResource(R.string.notify_now)) },
                    leadingIcon = { Icon(Icons.Outlined.Notifications, null, Modifier.size(FilterChipDefaults.IconSize)) },
                )
            }
            Button(onClick = ::save, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (pickingDay) {
        DayDialog(
            initial = date ?: today,
            onPicked = {
                date = it
                notifyNow = false
                pickingDay = false
            },
            onDismiss = { pickingDay = false },
        )
    }
    if (pickingTime) {
        TimeDialog(
            initial = time,
            onPicked = {
                time = it
                notifyNow = false
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
    }
}

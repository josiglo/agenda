package com.josiglo.agenda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.text.format.DateFormat
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.josiglo.agenda.data.Repeat
import com.josiglo.agenda.data.RepeatFrom
import com.josiglo.agenda.data.RepeatUnit
import com.josiglo.agenda.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Picks a date, then a time, and returns the epoch milliseconds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePicker(initial: Long, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(initial).atZone(zone)
    var date by remember { mutableStateOf<LocalDate?>(null) }
    val picked = date
    if (picked == null) {
        // The date picker works in UTC midnights.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                }) { Text(stringResource(R.string.next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state)
        }
    } else {
        val state = rememberTimePickerState(start.hour, start.minute, is24Hour = DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    onPicked(picked.atTime(LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant().toEpochMilli())
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOfDayPicker(initial: LocalTime, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state) },
    )
}

/** Picks how often an item repeats and what the next date counts from. Null means it doesn't repeat. */
@Composable
fun RepeatPicker(initial: Repeat?, onPicked: (Repeat?) -> Unit, onDismiss: () -> Unit) {
    var every by remember { mutableStateOf((initial?.every ?: 1).toString()) }
    var unit by remember { mutableStateOf(initial?.unit ?: RepeatUnit.DAY) }
    var from by remember { mutableStateOf(initial?.from ?: RepeatFrom.SCHEDULE) }
    val count = every.toIntOrNull()?.takeIf { it in 1..999 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.repeat)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.every), modifier = Modifier.padding(end = 12.dp))
                    OutlinedTextField(
                        value = every,
                        onValueChange = { every = it.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        isError = count == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepeatUnit.entries.forEach { u ->
                        val n = count ?: 1
                        val label = when (u) {
                            RepeatUnit.DAY -> pluralStringResource(R.plurals.day_units, n)
                            RepeatUnit.WEEK -> pluralStringResource(R.plurals.week_units, n)
                            RepeatUnit.MONTH -> pluralStringResource(R.plurals.month_units, n)
                            RepeatUnit.YEAR -> pluralStringResource(R.plurals.year_units, n)
                        }
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(label) })
                    }
                }
                Text(stringResource(R.string.repeat_counted_from), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    RepeatFrom.entries.forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = from == f,
                            onClick = { from = f },
                            shape = SegmentedButtonDefaults.itemShape(i, RepeatFrom.entries.size),
                            label = {
                                Text(stringResource(if (f == RepeatFrom.SCHEDULE) R.string.from_schedule else R.string.from_done))
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPicked(Repeat(count ?: return@TextButton, unit, from)) }, enabled = count != null) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onPicked(null) }) { Text(stringResource(R.string.does_not_repeat)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

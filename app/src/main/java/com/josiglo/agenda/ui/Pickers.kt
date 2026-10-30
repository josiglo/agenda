package com.josiglo.agenda.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.josiglo.agenda.R
import com.josiglo.agenda.data.Repeat
import com.josiglo.agenda.data.RepeatFrom
import com.josiglo.agenda.data.RepeatUnit
import com.josiglo.agenda.settings.Settings
import com.josiglo.agenda.time.parseTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Shortcuts for a notification time: in one hour, this evening, tomorrow
 * morning, or a picked day and then a typed time.
 */
@Composable
fun WhenOptions(title: String, initial: Long?, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val preferences by Settings.get(context).preferences.collectAsStateWithLifecycle()
    var day by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val start = initial?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val pickedDay = day
    when {
        pickedDay != null -> TimeDialog(
            initial = start?.toLocalTime() ?: preferences.morning,
            onPicked = { onPicked(pickedDay.atTime(it).atZone(zone).toInstant().toEpochMilli()) },
            onDismiss = onDismiss,
        )
        picking -> DayDialog(
            initial = start?.toLocalDate() ?: LocalDate.now(zone),
            onPicked = { day = it },
            onDismiss = onDismiss,
        )
        else -> {
            val now = ZonedDateTime.now(zone)
            val evening = now.toLocalDate().atTime(preferences.evening).atZone(zone)
            val morning = now.toLocalDate().plusDays(1).atTime(preferences.morning).atZone(zone)
            val options = buildList {
                add(stringResource(R.string.in_one_hour) to now.plusHours(1))
                if (evening.isAfter(now.plusMinutes(30))) {
                    add(stringResource(R.string.this_evening, Format.time(preferences.evening)) to evening)
                }
                add(stringResource(R.string.tomorrow_morning, Format.time(preferences.morning)) to morning)
            }
            val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title) },
                text = {
                    Column {
                        options.forEach { (label, at) ->
                            ListItem(
                                headlineContent = { Text(label) },
                                colors = transparent,
                                modifier = Modifier.clickable { onPicked(at.toInstant().toEpochMilli()) },
                            )
                        }
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.pick_date_time)) },
                            colors = transparent,
                            modifier = Modifier.clickable { picking = true },
                        )
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDialog(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The date picker works in UTC midnights.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state)
    }
}

/** A time typed as digits: 7 is 7:00, 730 or 0730 is 7:30. */
@Composable
fun TimeDialog(initial: LocalTime?, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    // Selected, so typing replaces the old time.
    var text by remember {
        val start = initial?.let { "%d%02d".format(it.hour, it.minute) }.orEmpty()
        mutableStateOf(TextFieldValue(start, TextRange(0, start.length)))
    }
    val parsed = parseTime(text.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.time)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.time_hint)) },
                isError = text.text.isNotBlank() && parsed == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { parsed?.let(onPicked) }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onPicked) }, enabled = parsed != null) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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

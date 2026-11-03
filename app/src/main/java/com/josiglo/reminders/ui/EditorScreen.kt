package com.josiglo.reminders.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import java.time.Instant
import com.josiglo.reminders.settings.Settings
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.josiglo.reminders.R
import com.josiglo.reminders.data.Item
import com.josiglo.reminders.data.Store
import java.time.LocalDate
import java.time.ZoneId

/** Edits item [id], or a new reminder starting at [newRemindAt] when [id] is 0. */
@Composable
fun EditorScreen(
    id: Long,
    newRemindAt: Long?,
    today: LocalDate,
    onClose: () -> Unit,
    onDeleted: (Item) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val original = remember(id) { store.find(id) }

    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var note by remember { mutableStateOf(original?.note.orEmpty()) }
    var place by remember { mutableStateOf(original?.place.orEmpty()) }
    var remindAt by remember { mutableStateOf(original?.remindAt ?: newRemindAt) }
    var repeat by remember { mutableStateOf(original?.repeat) }
    var pickingWhen by remember { mutableStateOf(false) }
    var pickingDay by remember { mutableStateOf(false) }
    var pickingClock by remember { mutableStateOf(false) }
    var pickingRepeat by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (original == null) titleFocus.requestFocus() }

    fun save() {
        val current = original?.let { store.find(it.id) }
        val base = (current ?: Item(title = title)).copy(
            title = title,
            note = note,
            place = place,
            remindAt = remindAt,
            repeat = if (remindAt == null) null else repeat,
        )
        when {
            current != null -> store.save(base)
            original == null -> store.add(base)
        }
        onClose()
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .imePadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                Spacer(Modifier.weight(1f))
                if (original != null) {
                    IconButton(onClick = { store.delete(original.id)?.let(onDeleted) }) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.delete))
                    }
                }
                Button(onClick = ::save, enabled = title.isNotBlank(), modifier = Modifier.padding(start = 6.dp)) {
                    Text(stringResource(R.string.save))
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                PlainField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = stringResource(R.string.title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.focusRequester(titleFocus),
                )
                PlainField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = stringResource(R.string.description),
                    style = MaterialTheme.typography.bodyLarge,
                    singleLine = false,
                )
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                PlaceField(place, store.places()) { place = it }
                val at = remindAt
                if (at == null) {
                    FieldRow(
                        icon = Icons.Outlined.Notifications,
                        text = stringResource(R.string.add_notification),
                        muted = true,
                        onClick = { pickingWhen = true },
                        onClear = null,
                    )
                } else {
                    NotificationRow(
                        at = at,
                        today = today,
                        onDay = { pickingDay = true },
                        onTime = { pickingClock = true },
                        onClear = { remindAt = null },
                    )
                    FieldRow(
                        icon = Icons.Outlined.Repeat,
                        text = repeat?.let { Format.repeat(context, it) } ?: stringResource(R.string.does_not_repeat),
                        muted = repeat == null,
                        onClick = { pickingRepeat = true },
                        onClear = if (repeat != null) ({ repeat = null }) else null,
                    )
                }
                Spacer(Modifier.heightIn(min = 24.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }

    if (pickingWhen) {
        WhenOptions(
            title = stringResource(R.string.add_notification),
            initial = null,
            onPicked = {
                remindAt = it
                pickingWhen = false
            },
            onDismiss = { pickingWhen = false },
        )
    }
    val zone = ZoneId.systemDefault()
    remindAt?.let { Instant.ofEpochMilli(it).atZone(zone) }?.let { at ->
        if (pickingDay) {
            DayDialog(
                initial = at.toLocalDate(),
                onPicked = {
                    remindAt = it.atTime(at.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                    pickingDay = false
                },
                onDismiss = { pickingDay = false },
            )
        }
        if (pickingClock) {
            TimeDialog(
                initial = at.toLocalTime(),
                onPicked = {
                    remindAt = at.toLocalDate().atTime(it).atZone(zone).toInstant().toEpochMilli()
                    pickingClock = false
                },
                onDismiss = { pickingClock = false },
            )
        }
    }
    if (pickingRepeat) {
        RepeatPicker(
            initial = repeat,
            onPicked = {
                repeat = it
                pickingRepeat = false
            },
            onDismiss = { pickingRepeat = false },
        )
    }
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onFocus: (Boolean) -> Unit = {},
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = style) },
        textStyle = style,
        singleLine = singleLine,
        leadingIcon = leading,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp).onFocusChanged { onFocus(it.isFocused) },
    )
}

@Composable
private fun PlaceField(place: String, places: List<String>, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    PlainField(
        value = place,
        onValueChange = onChange,
        placeholder = stringResource(R.string.place),
        style = MaterialTheme.typography.bodyLarge,
        singleLine = true,
        leading = { Icon(Icons.Outlined.LocationOn, null) },
        onFocus = { focused = it },
    )
    val matches = places.filter { it.contains(place.trim(), ignoreCase = true) && !it.equals(place.trim(), ignoreCase = true) }
    if (focused && matches.isNotEmpty()) {
        Row(
            Modifier.padding(start = 64.dp, end = 16.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            matches.take(6).forEach { SuggestionChip(onClick = { onChange(it) }, label = { Text(it) }) }
        }
    }
}

@Composable
private fun FieldRow(icon: ImageVector, text: String, muted: Boolean, onClick: () -> Unit, onClear: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
        if (onClear != null) {
            IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, stringResource(R.string.clear)) }
        }
    }
}

/** The notification time as a day button and a time button, each changed on its own. */
@Composable
private fun NotificationRow(at: Long, today: LocalDate, onDay: () -> Unit, onTime: () -> Unit, onClear: () -> Unit) {
    val context = LocalContext.current
    val zoned = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Notifications, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.weight(1f).padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onDay,
                label = { Text(Format.day(context, zoned.toLocalDate(), today) + if (zoned.toLocalDate().isAfter(today.plusDays(6))) "" else ", " + Format.dayMonth(zoned.toLocalDate())) },
                leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
            AssistChip(
                onClick = onTime,
                label = { Text(Format.time(zoned.toLocalTime())) },
                leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
        IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, stringResource(R.string.clear)) }
    }
}

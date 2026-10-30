package com.josiglo.agenda.ui

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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.josiglo.agenda.R
import com.josiglo.agenda.data.Item
import com.josiglo.agenda.data.Kind
import com.josiglo.agenda.data.Store
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Edits item [id], or a new item of [newKind] when [id] is 0. A new task
 * started from an empty top 3 slot goes into [newTopSlot].
 */
@Composable
fun EditorScreen(
    id: Long,
    newKind: Kind,
    newTopSlot: Int?,
    today: LocalDate,
    onClose: () -> Unit,
    onDeleted: (List<Item>) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val original = remember(id) { store.find(id) }
    val items by store.items.collectAsStateWithLifecycle()
    val epochDay = today.toEpochDay()

    var kind by remember { mutableStateOf(original?.kind ?: newKind) }
    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var note by remember { mutableStateOf(original?.note.orEmpty()) }
    var place by remember { mutableStateOf(original?.place.orEmpty()) }
    var remindAt by remember { mutableStateOf(original?.remindAt) }
    var repeat by remember { mutableStateOf(original?.repeat) }
    val subtasks = remember { mutableStateListOf<Item>().apply { if (original != null) addAll(store.subtasks(original.id)) } }
    val wasTop = original?.topSlot != null && original.topDay == epochDay
    var inTop by remember { mutableStateOf(wasTop || newTopSlot != null) }
    var pickingTime by remember { mutableStateOf(false) }
    var pickingRepeat by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (original == null) titleFocus.requestFocus() }

    fun save() {
        val current = if (original != null) store.find(original.id) ?: return onClose() else null
        val base = (current ?: Item(kind = kind, title = title)).copy(
            kind = kind,
            title = title,
            note = note,
            place = place,
            remindAt = if (kind == Kind.INBOX) null else remindAt,
            repeat = if (kind == Kind.INBOX || remindAt == null) null else repeat,
        )
        val keepSubtasks = if (kind == Kind.TASK) subtasks.toList() else emptyList()
        val savedId = if (current == null) {
            store.add(base, keepSubtasks.map { it.title })
        } else {
            store.save(base, keepSubtasks)
            current.id
        }
        val isTop = store.find(savedId)?.let { it.topSlot != null && it.topDay == epochDay } == true
        if (kind == Kind.TASK && inTop && !isTop) {
            val slot = newTopSlot?.takeIf { current == null } ?: freeSlot(store.items.value, epochDay)
            if (slot != null) store.setTop(savedId, slot, epochDay)
        } else if ((!inTop || kind != Kind.TASK) && isTop) {
            store.clearTop(savedId)
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
                    IconButton(onClick = { onDeleted(store.delete(original.id)) }) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.delete))
                    }
                }
                Button(onClick = ::save, enabled = title.isNotBlank(), modifier = Modifier.padding(start = 6.dp)) {
                    Text(stringResource(R.string.save))
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.padding(horizontal = 24.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KindChip(Kind.INBOX, kind, Icons.Outlined.Inbox, R.string.inbox) { kind = it }
                    KindChip(Kind.TASK, kind, Icons.Outlined.TaskAlt, R.string.task) { kind = it }
                    KindChip(Kind.REMINDER, kind, Icons.Outlined.Notifications, R.string.reminder) { kind = it }
                }
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
                if (kind != Kind.INBOX) {
                    FieldRow(
                        icon = Icons.Outlined.Notifications,
                        text = remindAt?.let { Format.dateTime(context, it, today) } ?: stringResource(R.string.add_notification),
                        muted = remindAt == null,
                        onClick = { pickingTime = true },
                        onClear = if (remindAt != null) ({ remindAt = null }) else null,
                    )
                    if (remindAt != null) {
                        FieldRow(
                            icon = Icons.Outlined.Repeat,
                            text = repeat?.let { Format.repeat(context, it) } ?: stringResource(R.string.does_not_repeat),
                            muted = repeat == null,
                            onClick = { pickingRepeat = true },
                            onClear = if (repeat != null) ({ repeat = null }) else null,
                        )
                    }
                }
                if (kind == Kind.TASK) {
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                    Subtasks(subtasks)
                    TopToggle(
                        inTop = inTop,
                        full = !wasTop && freeSlot(items, epochDay) == null && newTopSlot == null,
                        onChange = { inTop = it },
                    )
                }
                Spacer(Modifier.heightIn(min = 24.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }

    if (pickingTime) {
        DateTimePicker(
            initial = remindAt ?: ZonedDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli(),
            onPicked = {
                remindAt = it
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
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

fun freeSlot(items: List<Item>, epochDay: Long): Int? =
    (1..3).firstOrNull { slot -> items.none { it.topSlot == slot && it.topDay == epochDay } }

@Composable
private fun KindChip(value: Kind, selected: Kind, icon: ImageVector, label: Int, onSelect: (Kind) -> Unit) {
    FilterChip(
        selected = value == selected,
        onClick = { onSelect(value) },
        label = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, null, Modifier.size(FilterChipDefaults.IconSize)) },
    )
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

@Composable
private fun Subtasks(subtasks: MutableList<Item>) {
    Row(Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Checklist, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.subtasks) + if (subtasks.isNotEmpty()) "  ${subtasks.count { it.done }}/${subtasks.size}" else "",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
    subtasks.forEachIndexed { i, sub ->
        Row(Modifier.padding(start = 48.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckCircle(sub.done, {
                subtasks[i] = sub.copy(doneAt = if (sub.done) null else System.currentTimeMillis())
            })
            TextField(
                value = sub.title,
                onValueChange = { subtasks[i] = sub.copy(title = it) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.subtask)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { subtasks.removeAt(i) }) { Icon(Icons.Outlined.Close, stringResource(R.string.remove)) }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { subtasks.add(Item(kind = Kind.TASK, title = "")) }
            .padding(start = 56.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.add_subtask),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp),
        )
    }
}

@Composable
private fun TopToggle(inTop: Boolean, full: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onChange(!inTop) },
        enabled = inTop || !full,
        shape = RoundedCornerShape(16.dp),
        color = if (inTop) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (inTop) Icons.Rounded.Star else Icons.Outlined.StarOutline, null)
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(
                    when {
                        inTop -> R.string.in_top_3
                        full -> R.string.top_3_full
                        else -> R.string.add_to_top_3
                    },
                ),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

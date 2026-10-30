package com.josiglo.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.josiglo.agenda.R
import com.josiglo.agenda.data.Item
import com.josiglo.agenda.data.Kind
import com.josiglo.agenda.data.Store
import java.time.LocalDate

/** Everything without a notification time: the inbox, tasks and reminders. */
/**
 * Everything without a notification time, except what is in today's top 3.
 * Place chips filter, with none picked showing everything. Long press and
 * drag reorders a section.
 */
@Composable
fun BacklogScreen(items: List<Item>, today: LocalDate, actions: Actions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    var place by rememberSaveable { mutableStateOf<String?>(null) }
    var hovered by remember { mutableStateOf<Long?>(null) }
    val epochDay = today.toEpochDay()
    val backlog = items.filter { !it.isSubtask && it.remindAt == null && !(it.topSlot != null && it.topDay == epochDay) }
    val places = backlog.mapNotNull { it.place }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    val selected = place?.takeIf { it == NO_PLACE || it in places }
    val shown = backlog.filter {
        when (selected) {
            null -> true
            NO_PLACE -> it.place == null
            else -> it.place == selected
        }
    }.sortedBy { it.done }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            PageHeader(stringResource(R.string.backlog), stringResource(R.string.backlog_subtitle)) {
                IconButton(onClick = { actions.push(Screen.Search) }) { Icon(Icons.Outlined.Search, stringResource(R.string.search)) }
                IconButton(onClick = { actions.push(Screen.Settings) }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
            }
        }
        if (places.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (listOf(NO_PLACE) + places).forEach { p ->
                        FilterChip(
                            selected = selected == p,
                            onClick = { place = if (selected == p) null else p },
                            label = { Text(if (p == NO_PLACE) stringResource(R.string.no_place) else p) },
                        )
                    }
                }
            }
        }
        listOf(
            R.string.inbox to Kind.INBOX,
            R.string.tasks to Kind.TASK,
            R.string.reminders to Kind.REMINDER,
        ).forEach { (label, kind) ->
            val section = shown.filter { it.kind == kind }
            if (section.isEmpty()) return@forEach
            item(key = kind.key) {
                SectionLabel(stringResource(label), section.count { !it.done })
                Group {
                    section.forEachIndexed { i, item ->
                        if (i > 0) GroupDivider()
                        val rowModifier = Modifier
                            .background(if (hovered == item.id) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .itemDropTarget(onHover = { hovered = if (it) item.id else hovered.takeIf { h -> h != item.id } }) { dragged ->
                                val from = section.indexOfFirst { it.id == dragged }
                                if (from < 0 || dragged == item.id) return@itemDropTarget
                                store.reorder(section.toMutableList().apply { add(i, removeAt(from)) })
                            }
                        if (kind == Kind.INBOX) {
                            InboxRow(item, rowModifier, onOpen = { actions.open(item) }, onMove = { store.save(item.copy(kind = it), store.subtasks(item.id)) },
                                onDelete = { store.delete(item.id) })
                        } else {
                            ItemRow(
                                title = item.title,
                                meta = itemMeta(item, items.filter { it.parentId == item.id }),
                                onClick = { actions.open(item) },
                                done = item.done,
                                leading = { CheckCircle(item.done, { actions.toggle(item) }) },
                                modifier = rowModifier,
                                dragId = item.id,
                            )
                        }
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.backlog_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }
        }
    }
}

// Stands for the "No place" chip. Places are user text, so it can't be one.
private const val NO_PLACE = "\u0000"

@Composable
private fun InboxRow(item: Item, modifier: Modifier, onOpen: () -> Unit, onMove: (Kind) -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ItemRow(
        title = item.title,
        meta = itemMeta(item, emptyList()),
        onClick = onOpen,
        leading = { Icon(Icons.Outlined.Inbox, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = modifier,
        dragId = item.id,
        trailing = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.make_task)) }, onClick = {
                        menu = false
                        onMove(Kind.TASK)
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.make_reminder)) }, onClick = {
                        menu = false
                        onMove(Kind.REMINDER)
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                        menu = false
                        onDelete()
                    })
                }
            }
        },
    )
}

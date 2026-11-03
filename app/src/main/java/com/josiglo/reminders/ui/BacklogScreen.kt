package com.josiglo.reminders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import com.josiglo.reminders.R
import com.josiglo.reminders.data.Item
import com.josiglo.reminders.data.Store

/** Everything without a notification time: the inbox, tasks and reminders. */
/**
 * Reminders without a notification time, then the ones ticked off today. One
 * place chip is always picked: "No place" or a place. Long press and drag
 * reorders the open reminders.
 */
@Composable
fun BacklogScreen(items: List<Item>, actions: Actions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    var place by rememberSaveable { mutableStateOf(NO_PLACE) }
    var hovered by remember { mutableStateOf<Long?>(null) }
    val backlog = items.filter { it.remindAt == null }
    val places = backlog.mapNotNull { it.place }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    val selected = place.takeIf { it in places } ?: NO_PLACE
    val shown = backlog.filter { if (selected == NO_PLACE) it.place == null else it.place == selected }
    val open = shown.filter { !it.done }
    val done = shown.filter { it.done }

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
                            onClick = { place = p },
                            label = { Text(if (p == NO_PLACE) stringResource(R.string.no_place) else p) },
                        )
                    }
                }
            }
        }
        if (open.isNotEmpty()) {
            item(key = "open") {
                SectionLabel(stringResource(R.string.reminders), open.size)
                Group {
                    open.forEachIndexed { i, item ->
                        if (i > 0) GroupDivider()
                        ItemRow(
                            title = item.title,
                            meta = itemMeta(item),
                            onClick = { actions.open(item) },
                            leading = { CheckCircle(false, { actions.toggle(item) }) },
                            modifier = Modifier
                                .background(if (hovered == item.id) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .itemDropTarget(onHover = { hovered = if (it) item.id else hovered.takeIf { h -> h != item.id } }) { dragged ->
                                    val from = open.indexOfFirst { it.id == dragged }
                                    if (from < 0 || dragged == item.id) return@itemDropTarget
                                    store.reorder(open.toMutableList().apply { add(i, removeAt(from)) })
                                },
                            dragId = item.id,
                        )
                    }
                }
            }
        }
        if (done.isNotEmpty()) {
            item(key = "done") {
                SectionLabel(stringResource(R.string.done_today), done.size)
                Group {
                    done.forEachIndexed { i, item ->
                        if (i > 0) GroupDivider()
                        ItemRow(
                            title = item.title,
                            meta = itemMeta(item),
                            onClick = { actions.open(item) },
                            done = true,
                            leading = { CheckCircle(true, { actions.toggle(item) }) },
                        )
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

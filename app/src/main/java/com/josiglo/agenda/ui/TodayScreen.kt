package com.josiglo.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.josiglo.agenda.R
import com.josiglo.agenda.calendar.Event
import com.josiglo.agenda.data.Item
import com.josiglo.agenda.data.Kind
import com.josiglo.agenda.data.Store
import com.josiglo.agenda.time.DayClock
import java.time.LocalDate

/** A line on Today or Upcoming: an item or a calendar event, at [time]. */
sealed interface Entry {
    val time: Long

    data class OfItem(val item: Item) : Entry {
        override val time get() = item.remindAt ?: 0
    }

    data class OfEvent(val event: Event) : Entry {
        override val time get() = event.begin
    }
}

/** Items with a notification today, open ones from earlier days and the ones ticked off today. */
fun todayItems(items: List<Item>, clock: DayClock, today: LocalDate): List<Item> {
    val start = clock.start(today)
    val end = clock.end(today)
    return items.filter { item ->
        val at = item.remindAt ?: return@filter false
        !item.isSubtask && item.kind != Kind.INBOX &&
            (at in start until end || (at < start && (item.doneAt ?: Long.MAX_VALUE) >= start))
    }
}

@Composable
fun TodayScreen(
    items: List<Item>,
    events: List<Event>,
    clock: DayClock,
    today: LocalDate,
    actions: Actions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    var choosing by remember { mutableStateOf<Int?>(null) }
    val epochDay = today.toEpochDay()
    val top = (1..3).associateWith { slot -> items.find { it.topSlot == slot && it.topDay == epochDay } }
    val topIds = top.values.mapNotNull { it?.id }.toSet()
    val start = clock.start(today)
    val end = clock.end(today)
    val schedule = (
        todayItems(items, clock, today).filter { it.id !in topIds }.map { Entry.OfItem(it) } +
            events.filter { it.day == today || (!it.allDay && it.begin in start until end) }.map { Entry.OfEvent(it) }
        ).sortedBy { it.time }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 88.dp)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            PageHeader(stringResource(R.string.today), Format.longDate(today)) {
                IconButton(onClick = { actions.push(Screen.Search) }) { Icon(Icons.Outlined.Search, stringResource(R.string.search)) }
                IconButton(onClick = { actions.push(Screen.Settings) }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
            }
        }
        item { SectionLabel(stringResource(R.string.top_3)) }
        items(3, key = { "slot$it" }) { index ->
            val slot = index + 1
            val item = top[slot]
            Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                if (item == null) {
                    EmptySlot(slot) { choosing = slot }
                } else {
                    TopSlot(
                        slot = slot,
                        item = item,
                        parent = item.parentId?.let { id -> items.find { it.id == id } },
                        onToggle = { actions.toggle(item) },
                        onOpen = { actions.open(item) },
                        onChoose = { choosing = slot },
                        onRemove = { store.clearTop(item.id) },
                    )
                }
            }
        }
        if (schedule.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.schedule)) }
            item {
                Group {
                    schedule.forEachIndexed { i, entry ->
                        if (i > 0) GroupDivider()
                        when (entry) {
                            is Entry.OfEvent -> EventRow(entry.event, onClick = { actions.openEvent(entry.event) })
                            is Entry.OfItem -> {
                                val item = entry.item
                                val overdue = item.remindAt!! < start
                                ItemRow(
                                    title = item.title,
                                    meta = itemMeta(item, items.filter { it.parentId == item.id }),
                                    onClick = { actions.open(item) },
                                    time = if (overdue) Format.day(context, clock.day(item.remindAt), today) else Format.time(item.remindAt),
                                    timeColor = if (overdue && !item.done) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    done = item.done,
                                    leading = { CheckCircle(item.done, { actions.toggle(item) }) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    choosing?.let { slot ->
        TopPicker(
            items = items,
            excluded = topIds,
            onPick = {
                store.setTop(it.id, slot, epochDay)
                choosing = null
            },
            onNew = {
                choosing = null
                actions.push(Screen.Edit(0, Kind.TASK, topSlot = slot))
            },
            onDismiss = { choosing = null },
        )
    }
}

@Composable
private fun EmptySlot(slot: Int, onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(20.dp))
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).border(1.5.dp, outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(slot.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        Text(
            stringResource(R.string.choose_task),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        )
        Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TopSlot(
    slot: Int,
    item: Item,
    parent: Item?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (item.done) colors.surfaceContainer else colors.primaryContainer
    val content = if (item.done) colors.onSurfaceVariant else colors.onPrimaryContainer
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .clickable(onClick = onOpen)
            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = stringResource(if (item.done) R.string.mark_open else R.string.mark_done)
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                if (item.done) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = colors.onPrimary)
                } else {
                    Text(slot.toString(), color = colors.onPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 6.dp), verticalArrangement = Arrangement.Center) {
            if (parent != null) {
                Text(parent.title, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.75f))
            }
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (item.done) FontWeight.Normal else FontWeight.Medium,
                color = content,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more), tint = content) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.choose_other_task)) }, onClick = {
                    menu = false
                    onChoose()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.remove_from_top)) }, onClick = {
                    menu = false
                    onRemove()
                })
            }
        }
    }
}

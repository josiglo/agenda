package com.josiglo.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.josiglo.agenda.R
import com.josiglo.agenda.data.Item
import com.josiglo.agenda.data.Kind
import com.josiglo.agenda.data.Store
import com.josiglo.agenda.time.DayClock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/**
 * This week from Monday to Sunday, with past days dimmed. A button at the
 * end adds the next week. Items can be dragged to today or a later day after
 * a long press and keep their time. Calendar events stay where they are.
 */
@Composable
fun UpcomingScreen(
    items: List<Item>,
    calendars: Set<Long>,
    clock: DayClock,
    today: LocalDate,
    actions: Actions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    var weeks by rememberSaveable { mutableIntStateOf(1) }
    var hoveredDay by remember { mutableStateOf<LocalDate?>(null) }
    var rescheduling by remember { mutableStateOf<Item?>(null) }
    val list = rememberLazyListState()
    val todayLabel = stringResource(R.string.today)
    val locale = LocalConfiguration.current.locales[0]
    val first = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val last = first.plusDays(7L * weeks - 1)
    val events = rememberEvents(calendars, clock.start(first), clock.end(last))
    val days = (0 until 7L * weeks).map { first.plusDays(it) }
    val itemsByDay = items
        .filter { it.kind != Kind.INBOX && it.remindAt != null && clock.day(it.remindAt) in first..last }
        .groupBy { clock.day(it.remindAt!!) }
    val eventsByDay = events.filter { it.day in first..last }.groupBy { it.day }

    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                PageHeader(stringResource(R.string.upcoming), stringResource(R.string.upcoming_subtitle)) {
                    IconButton(onClick = { actions.push(Screen.Search) }) { Icon(Icons.Outlined.Search, stringResource(R.string.search)) }
                    IconButton(onClick = { actions.push(Screen.Settings) }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
                }
            }
            days.forEach { day ->
                item(key = day.toEpochDay()) {
                    val entries = (
                        itemsByDay[day].orEmpty().map { Entry.OfItem(it) } + eventsByDay[day].orEmpty().map { Entry.OfEvent(it) }
                        ).sortedBy { if (it is Entry.OfEvent && it.event.allDay) Long.MIN_VALUE else it.time }
                    val past = day.isBefore(today)
                    val drop = Modifier.itemDropTarget(onHover = { hoveredDay = if (it && !past) day else hoveredDay.takeIf { d -> d != day } }) { id ->
                        if (past) return@itemDropTarget
                        val item = store.find(id) ?: return@itemDropTarget
                        val zone = ZoneId.systemDefault()
                        val time = Instant.ofEpochMilli(item.remindAt ?: return@itemDropTarget).atZone(zone).toLocalTime()
                        store.reschedule(id, day.atTime(time).atZone(zone).toInstant().toEpochMilli())
                    }
                    Column(drop.alpha(if (past) 0.45f else 1f)) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)) {
                                    append(if (day == today) todayLabel else day.dayOfWeek.getDisplayName(TextStyle.FULL, locale))
                                }
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" · " + Format.dayMonth(day)) }
                            },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                        )
                        val hovered = hoveredDay == day
                        if (entries.isEmpty()) {
                            Box(
                                Modifier
                                    .padding(horizontal = 12.dp)
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (hovered) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    stringResource(R.string.nothing_planned),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }
                        } else {
                            Group {
                                entries.forEachIndexed { i, entry ->
                                    if (i > 0) GroupDivider()
                                    when (entry) {
                                        is Entry.OfEvent -> EventRow(entry.event, onClick = { actions.openEvent(entry.event) })
                                        is Entry.OfItem -> {
                                            val item = entry.item
                                            ItemRow(
                                                title = item.title,
                                                meta = itemMeta(item, items.filter { it.parentId == item.id }, parentMeta(items, item)),
                                                onClick = { actions.open(item) },
                                                onTimeClick = { rescheduling = item },
                                                time = Format.time(entry.time),
                                                done = item.done,
                                                leading = { CheckCircle(item.done, { actions.toggle(item) }) },
                                                modifier = Modifier
                                                    .background(if (hovered) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                                                dragId = item.id,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = { weeks++ }) {
                        Icon(Icons.Outlined.KeyboardArrowDown, null)
                        Text(stringResource(R.string.show_next_week), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        DragScrollEdges(list)
    }

    rescheduling?.let { item ->
        WhenOptions(
            title = item.title,
            initial = item.remindAt,
            onPicked = {
                store.reschedule(item.id, it)
                rescheduling = null
            },
            onDismiss = { rescheduling = null },
        )
    }
}

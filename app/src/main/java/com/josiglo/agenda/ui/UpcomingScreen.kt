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
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.josiglo.agenda.R
import com.josiglo.agenda.data.Item
import com.josiglo.agenda.data.Kind
import com.josiglo.agenda.data.Store
import com.josiglo.agenda.time.DayClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The next week, day by day. Pulling up past the end adds another week.
 * Items can be dragged to another day after a long press. They keep their
 * time. Calendar events stay where they are.
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
    val first = today.plusDays(1)
    val last = today.plusDays(7L * weeks)
    val events = rememberEvents(calendars, clock.start(first), clock.end(last))
    val days = (0 until 7L * weeks).map { first.plusDays(it) }
    val itemsByDay = items
        .filter { it.kind != Kind.INBOX && !it.done && it.remindAt != null && clock.day(it.remindAt) in first..last }
        .groupBy { clock.day(it.remindAt!!) }
    val eventsByDay = events.filter { it.day in first..last && (it.allDay || it.begin >= clock.start(first)) }.groupBy { it.day }

    // How far the list has been pulled up past its end, in pixels.
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }
    var pulled by remember { mutableFloatStateOf(0f) }
    val pull = remember(threshold) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0) {
                    pulled -= available.y
                    if (pulled >= threshold) {
                        pulled = 0f
                        weeks++
                    }
                } else if (available.y > 0 || consumed.y > 0) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize().nestedScroll(pull), state = list, contentPadding = PaddingValues(bottom = 88.dp)) {
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
                    val drop = Modifier.itemDropTarget(onHover = { hoveredDay = if (it) day else hoveredDay.takeIf { d -> d != day } }) { id ->
                        val item = store.find(id) ?: return@itemDropTarget
                        val zone = ZoneId.systemDefault()
                        val time = Instant.ofEpochMilli(item.remindAt ?: return@itemDropTarget).atZone(zone).toLocalTime()
                        store.reschedule(id, day.atTime(time).atZone(zone).toInstant().toEpochMilli())
                    }
                    Column(drop) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(Format.day(context, day, today)) }
                                if (day.isBefore(today.plusDays(7))) {
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" · " + Format.dayMonth(day)) }
                                }
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
                Column(
                    Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp).alpha(0.5f + 0.5f * (pulled / threshold).coerceIn(0f, 1f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.KeyboardDoubleArrowUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.pull_for_next_week),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

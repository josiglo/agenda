package com.josiglo.agenda.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.josiglo.agenda.R
import com.josiglo.agenda.calendar.Event
import com.josiglo.agenda.data.Item
import com.josiglo.agenda.data.Store
import com.josiglo.agenda.time.DayClock
import java.time.LocalDate

/** A line on Today or Week: an item or a calendar event, at [time]. */
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
        (at in start until end || (at < start && (item.doneAt ?: Long.MAX_VALUE) >= start))
    }
}

/** Today's reminders and calendar events, sorted by time. */
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
    var rescheduling by remember { mutableStateOf<Item?>(null) }
    val start = clock.start(today)
    val end = clock.end(today)
    val schedule = (
        todayItems(items, clock, today).map { Entry.OfItem(it) } +
            events.filter { it.day == today || (!it.allDay && it.begin in start until end) }.map { Entry.OfEvent(it) }
        ).sortedBy { if (it is Entry.OfEvent && it.event.allDay) Long.MIN_VALUE else it.time }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            PageHeader(stringResource(R.string.today), Format.longDate(today)) {
                IconButton(onClick = { actions.push(Screen.Search) }) { Icon(Icons.Outlined.Search, stringResource(R.string.search)) }
                IconButton(onClick = { actions.push(Screen.Settings) }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
            }
        }
        if (schedule.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.today_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }
        } else {
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
                                    meta = itemMeta(item),
                                    onClick = { actions.open(item) },
                                    onTimeClick = { rescheduling = item },
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

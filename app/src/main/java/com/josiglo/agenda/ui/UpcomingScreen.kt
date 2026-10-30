package com.josiglo.agenda.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.josiglo.agenda.R
import com.josiglo.agenda.calendar.Event
import com.josiglo.agenda.data.Item
import com.josiglo.agenda.data.Kind
import com.josiglo.agenda.time.DayClock
import java.time.LocalDate

@Composable
fun UpcomingScreen(
    items: List<Item>,
    events: List<Event>,
    clock: DayClock,
    today: LocalDate,
    actions: Actions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val after = clock.end(today)
    val entries = (
        items.filter { !it.isSubtask && it.kind != Kind.INBOX && !it.done && (it.remindAt ?: 0) >= after }
            .map { Entry.OfItem(it) } +
            events.filter { it.day.isAfter(today) && (it.allDay || it.begin >= after) }.map { Entry.OfEvent(it) }
        ).sortedBy { it.time }
    val days = entries.groupBy { entry ->
        when (entry) {
            is Entry.OfItem -> clock.day(entry.time)
            is Entry.OfEvent -> entry.event.day
        }
    }.toSortedMap()

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 88.dp)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            PageHeader(stringResource(R.string.upcoming), stringResource(R.string.upcoming_subtitle)) {
                IconButton(onClick = { actions.push(Screen.Search) }) { Icon(Icons.Outlined.Search, stringResource(R.string.search)) }
                IconButton(onClick = { actions.push(Screen.Settings) }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
            }
        }
        if (days.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.nothing_upcoming),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }
        }
        days.forEach { (day, dayEntries) ->
            item(key = day.toEpochDay()) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(Format.day(context, day, today)) }
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" · " + Format.dayMonth(day)) }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                )
                Group {
                    dayEntries.forEachIndexed { i, entry ->
                        if (i > 0) GroupDivider()
                        when (entry) {
                            is Entry.OfEvent -> EventRow(entry.event, onClick = { actions.openEvent(entry.event) })
                            is Entry.OfItem -> {
                                val item = entry.item
                                ItemRow(
                                    title = item.title,
                                    meta = itemMeta(item, items.filter { it.parentId == item.id }),
                                    onClick = { actions.open(item) },
                                    time = Format.time(entry.time),
                                    leading = { CheckCircle(item.done, { actions.toggle(item) }) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

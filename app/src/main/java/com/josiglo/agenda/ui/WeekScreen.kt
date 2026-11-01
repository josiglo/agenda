package com.josiglo.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.josiglo.agenda.R
import com.josiglo.agenda.data.Item
import com.josiglo.agenda.data.Kind
import com.josiglo.agenda.data.Store
import com.josiglo.agenda.settings.Settings
import com.josiglo.agenda.time.DayClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private const val PAST_WEEKS = 52
private const val FUTURE_WEEKS = 260

/**
 * One week from Monday to Sunday per page, swiped sideways, with past days
 * dimmed. Every day from today on has a button to add an item on that day.
 * Items can be dragged to today or a later day after a long press and keep
 * their time. Holding a drag at the left or right edge turns the page.
 * Calendar events stay where they are.
 */
@Composable
fun WeekScreen(
    items: List<Item>,
    calendars: Set<Long>,
    clock: DayClock,
    today: LocalDate,
    actions: Actions,
    modifier: Modifier = Modifier,
) {
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val pager = rememberPagerState(initialPage = PAST_WEEKS) { PAST_WEEKS + FUTURE_WEEKS }
    val scope = rememberCoroutineScope()
    val shown = thisMonday.plusWeeks((pager.currentPage - PAST_WEEKS).toLong())

    Column(modifier) {
        PageHeader(stringResource(R.string.week), Format.weekRange(shown)) {
            if (pager.currentPage != PAST_WEEKS) {
                IconButton(onClick = { scope.launch { pager.animateScrollToPage(PAST_WEEKS) } }) {
                    Icon(Icons.Outlined.Today, stringResource(R.string.this_week))
                }
            }
            IconButton(onClick = { actions.push(Screen.Search) }) { Icon(Icons.Outlined.Search, stringResource(R.string.search)) }
            IconButton(onClick = { actions.push(Screen.Settings) }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
        }
        Box(Modifier.weight(1f)) {
            // Neighbors stay composed, so they take drops after a page turn mid-drag.
            HorizontalPager(pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
                WeekPage(
                    monday = thisMonday.plusWeeks((page - PAST_WEEKS).toLong()),
                    items = items,
                    calendars = calendars,
                    clock = clock,
                    today = today,
                    actions = actions,
                )
            }
            DragPageEdges(pager)
        }
    }
}

@Composable
private fun WeekPage(
    monday: LocalDate,
    items: List<Item>,
    calendars: Set<Long>,
    clock: DayClock,
    today: LocalDate,
    actions: Actions,
) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val preferences by Settings.get(context).preferences.collectAsStateWithLifecycle()
    var hoveredDay by remember { mutableStateOf<LocalDate?>(null) }
    var rescheduling by remember { mutableStateOf<Item?>(null) }
    val list = rememberLazyListState()
    val todayLabel = stringResource(R.string.today)
    val locale = LocalConfiguration.current.locales[0]
    val sunday = monday.plusDays(6)
    val events = rememberEvents(calendars, clock.start(monday), clock.end(sunday))
    val itemsByDay = items
        .filter { it.kind != Kind.INBOX && it.remindAt != null && clock.day(it.remindAt) in monday..sunday }
        .groupBy { clock.day(it.remindAt!!) }
    val eventsByDay = events.filter { it.day in monday..sunday }.groupBy { it.day }
    val newKind = preferences.kindOrder.first { it != Kind.INBOX }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 88.dp)) {
            (0 until 7L).map { monday.plusDays(it) }.forEach { day ->
                item(key = day.toEpochDay()) {
                    val entries = (
                        itemsByDay[day].orEmpty().map { Entry.OfItem(it) } + eventsByDay[day].orEmpty().map { Entry.OfEvent(it) }
                        ).sortedBy { if (it is Entry.OfEvent && it.event.allDay) Long.MIN_VALUE else it.time }
                    val past = day.isBefore(today)
                    val hovered = hoveredDay == day
                    val drop = Modifier.itemDropTarget(onHover = { hoveredDay = if (it && !past) day else hoveredDay.takeIf { d -> d != day } }) { id ->
                        if (past) return@itemDropTarget
                        val item = store.find(id) ?: return@itemDropTarget
                        val zone = ZoneId.systemDefault()
                        val time = Instant.ofEpochMilli(item.remindAt ?: return@itemDropTarget).atZone(zone).toLocalTime()
                        store.reschedule(id, day.atTime(time).atZone(zone).toInstant().toEpochMilli())
                    }
                    Column(drop.alpha(if (past) 0.45f else 1f)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .background(if (hovered && entries.isEmpty()) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .padding(start = 20.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)) {
                                        append(if (day == today) todayLabel else day.dayOfWeek.getDisplayName(TextStyle.FULL, locale))
                                    }
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" · " + Format.dayMonth(day)) }
                                },
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            if (!past) {
                                IconButton(onClick = {
                                    actions.push(Screen.Edit(0, newKind, remindAt = defaultTime(day, today, preferences.morning)))
                                }) {
                                    Icon(Icons.Outlined.Add, stringResource(R.string.add_on_day), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (entries.isNotEmpty()) {
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

/** The next full hour for today, the morning time for later days. */
private fun defaultTime(day: LocalDate, today: LocalDate, morning: LocalTime): Long {
    val zone = ZoneId.systemDefault()
    val now = ZonedDateTime.now(zone)
    val at = if (day == today) now.truncatedTo(ChronoUnit.HOURS).plusHours(1) else day.atTime(morning).atZone(zone)
    return at.toInstant().toEpochMilli()
}

/** Strips at the left and right edge that turn the page while a drag rests on them. */
@Composable
private fun BoxScope.DragPageEdges(pager: PagerState) {
    var direction by remember { mutableIntStateOf(0) }
    LaunchedEffect(direction) {
        if (direction == 0) return@LaunchedEffect
        delay(600)
        pager.animateScrollToPage((pager.currentPage + direction).coerceIn(0, pager.pageCount - 1))
        direction = 0
    }
    listOf(Alignment.CenterStart to -1, Alignment.CenterEnd to 1).forEach { (alignment, dir) ->
        Box(
            Modifier
                .align(alignment)
                .width(28.dp)
                .fillMaxHeight()
                .itemDropTarget(onHover = { direction = if (it) dir else 0 }, onDrop = {}),
        )
    }
}

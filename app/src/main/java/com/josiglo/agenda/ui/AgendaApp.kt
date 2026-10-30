package com.josiglo.agenda.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.josiglo.agenda.R
import com.josiglo.agenda.calendar.Calendars
import com.josiglo.agenda.calendar.Event
import com.josiglo.agenda.data.Item
import com.josiglo.agenda.data.Kind
import com.josiglo.agenda.data.Store
import com.josiglo.agenda.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    /** The editor for item [id], or for a new item of [kind] when [id] is 0. */
    data class Edit(val id: Long, val kind: Kind = Kind.TASK, val topSlot: Int? = null) : Screen
    data object Settings : Screen
    data object Places : Screen
    data object Search : Screen
}

enum class Tab(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    TODAY(R.string.today, Icons.Outlined.Today, Icons.Filled.Today),
    UPCOMING(R.string.upcoming, Icons.Outlined.DateRange, Icons.Filled.DateRange),
    BACKLOG(R.string.backlog, Icons.Outlined.Inbox, Icons.Filled.Inbox),
}

/** What the pages need to show items and react to taps. */
class Actions(
    val open: (Item) -> Unit,
    val toggle: (Item) -> Unit,
    val openEvent: (Event) -> Unit,
    val push: (Screen) -> Unit,
)

@Composable
fun AgendaApp(openItem: Long?, onOpenHandled: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val store = remember { Store.get(context) }
    val settings = remember { Settings.get(context) }
    val items by store.items.collectAsStateWithLifecycle()
    val preferences by settings.preferences.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var stack by remember { mutableStateOf(listOf<Screen>()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    val clock = preferences.clock()
    val today = clock.today(now)

    fun push(screen: Screen) {
        stack = stack + screen
    }

    fun pop() {
        stack = stack.dropLast(1)
    }

    fun open(item: Item) = push(Screen.Edit(item.parentId ?: item.id))

    LaunchedEffect(openItem) {
        if (openItem != null) {
            store.find(openItem)?.let { stack = listOf(Screen.Edit(it.parentId ?: it.id)) }
            onOpenHandled()
        }
    }

    val actions = remember {
        Actions(
            open = ::open,
            toggle = { store.setDone(it.id, !it.done) },
            openEvent = { event ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Calendars.eventUri(event.id)))
                }
            },
            push = ::push,
        )
    }

    // Events from today to two weeks ahead, read again when the app comes back.
    var resumed by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumed++
        onPauseOrDispose {}
    }
    val events by produceState(emptyList<Event>(), preferences.calendars, today, resumed) {
        value = withContext(Dispatchers.IO) {
            Calendars.events(context, preferences.calendars, clock.start(today), clock.end(today.plusDays(UPCOMING_DAYS)))
        }
    }

    when (val screen = stack.lastOrNull()) {
        is Screen.Edit -> EditorScreen(
            id = screen.id,
            newKind = screen.kind,
            newTopSlot = screen.topSlot,
            today = today,
            onClose = ::pop,
            onDeleted = { gone ->
                pop()
                scope.launch {
                    val result = snackbar.showSnackbar(resources.getString(R.string.deleted), resources.getString(R.string.undo), withDismissAction = true)
                    if (result == SnackbarResult.ActionPerformed) store.restore(gone)
                }
            },
        )
        Screen.Settings -> SettingsScreen(onBack = ::pop, onPlaces = { push(Screen.Places) })
        Screen.Places -> PlacesScreen(onBack = ::pop)
        Screen.Search -> SearchScreen(items, today, actions, onBack = ::pop)
        null -> Unit
    }
    if (stack.isNotEmpty()) {
        BackHandler { pop() }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(if (tab == t) t.selectedIcon else t.icon, null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                push(Screen.Edit(0, if (tab == Tab.BACKLOG) Kind.INBOX else Kind.TASK))
            }) {
                Icon(Icons.Rounded.Add, stringResource(R.string.add))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.TODAY -> TodayScreen(items, events, clock, today, actions, modifier)
            Tab.UPCOMING -> UpcomingScreen(items, events, clock, today, actions, modifier)
            Tab.BACKLOG -> BacklogScreen(items, actions, modifier)
        }
    }
}

const val UPCOMING_DAYS = 14L

/** The current time, updated at every full minute. */
@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000 - value % 60_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

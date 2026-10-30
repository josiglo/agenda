package com.josiglo.agenda.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.josiglo.agenda.R
import com.josiglo.agenda.data.Item
import java.time.LocalDate

@Composable
fun SearchScreen(items: List<Item>, today: LocalDate, actions: Actions, onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val q = query.trim()
    val results = if (q.isEmpty()) emptyList() else items.filter { item ->
        listOfNotNull(item.title, item.note, item.place).any { it.contains(q, ignoreCase = true) }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                if (results.isNotEmpty()) {
                    item {
                        Group {
                            results.forEachIndexed { i, item ->
                                if (i > 0) GroupDivider()
                                val parent = item.parentId?.let { id -> items.find { it.id == id } }
                                ItemRow(
                                    title = item.title,
                                    meta = itemMeta(
                                        item,
                                        items.filter { it.parentId == item.id },
                                        extra = listOfNotNull(
                                            parent?.let { Icons.Outlined.Checklist to it.title },
                                            item.remindAt?.let { Icons.Outlined.Notifications to Format.dateTime(context, it, today) },
                                        ),
                                    ),
                                    onClick = { actions.open(item) },
                                    done = item.done,
                                    leading = { CheckCircle(item.done, { actions.toggle(item) }) },
                                )
                            }
                        }
                    }
                } else if (q.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_results),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        }
    }
}

package com.josiglo.agenda.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.josiglo.agenda.R
import com.josiglo.agenda.data.Item
import com.josiglo.agenda.data.Kind

/** Open tasks and their open subtasks to put into a top 3 slot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopPicker(items: List<Item>, excluded: Set<Long>, onPick: (Item) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    val tasks = items.filter { it.kind == Kind.TASK && !it.isSubtask && !it.done }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.choose_task),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.new_task)) },
                    leadingContent = { Icon(Icons.Outlined.Add, null) },
                    colors = transparent,
                    modifier = Modifier.clickable(onClick = onNew),
                )
            }
            tasks.forEach { task ->
                val subtasks = items.filter { it.parentId == task.id && !it.done && it.id !in excluded }
                if (task.id !in excluded) {
                    item(key = task.id) {
                        ListItem(
                            headlineContent = { Text(task.title) },
                            supportingContent = task.place?.let { { Text(it) } },
                            leadingContent = { Icon(Icons.Outlined.Circle, null, tint = MaterialTheme.colorScheme.outline) },
                            colors = transparent,
                            modifier = Modifier.clickable { onPick(task) },
                        )
                    }
                }
                items(subtasks, key = { it.id }) { sub ->
                    ListItem(
                        headlineContent = { Text(sub.title) },
                        supportingContent = { Text(task.title) },
                        leadingContent = { Icon(Icons.Outlined.Circle, null, tint = MaterialTheme.colorScheme.outlineVariant) },
                        colors = transparent,
                        modifier = Modifier.clickable { onPick(sub) }.padding(start = 32.dp),
                    )
                }
            }
        }
    }
}

package com.josiglo.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.josiglo.agenda.R
import com.josiglo.agenda.calendar.Event
import com.josiglo.agenda.data.Item
import androidx.compose.ui.res.stringResource

@Composable
fun PageHeader(title: String, subtitle: String?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

@Composable
fun SectionLabel(text: String, count: Int? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (count != null) {
            Text(count.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Rows on one rounded surface, split by thin lines in the page color. */
@Composable
fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
fun GroupDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.surface)
}

@Composable
fun CheckCircle(done: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (done) R.string.mark_open else R.string.mark_done)
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClickLabel = label, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            Box(Modifier.size(22.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
        }
    }
}

@Composable
fun Meta(parts: List<Pair<ImageVector, String>>, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    if (parts.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 2.dp)) {
        parts.forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(16.dp), tint = color)
                Spacer(Modifier.width(3.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun itemMeta(item: Item, subtasks: List<Item>, extra: List<Pair<ImageVector, String>> = emptyList()): List<Pair<ImageVector, String>> {
    val context = LocalContext.current
    val note = stringResource(R.string.note)
    return buildList {
        addAll(extra)
        item.repeat?.let { add(Icons.Outlined.Repeat to Format.repeat(context, it)) }
        item.place?.let { add(Icons.Outlined.LocationOn to it) }
        if (subtasks.isNotEmpty()) add(Icons.Outlined.Checklist to "${subtasks.count { it.done }}/${subtasks.size}")
        if (item.note != null) add(Icons.AutoMirrored.Outlined.Notes to note)
    }
}

/** The parent task of a subtask, shown under its title. */
fun parentMeta(items: List<Item>, item: Item): List<Pair<ImageVector, String>> =
    item.parentId?.let { id -> items.find { it.id == id } }?.let { listOf(Icons.Outlined.Checklist to it.title) }.orEmpty()

/**
 * One row: an optional time column, a leading slot that is always 40 dp wide
 * so titles line up, then the title with its details.
 */
@Composable
fun ItemRow(
    title: String,
    meta: List<Pair<ImageVector, String>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    time: String? = null,
    timeColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    done: Boolean = false,
    leading: @Composable () -> Unit,
    onTimeClick: (() -> Unit)? = null,
    dragId: Long? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            // Inside the click handling, so a long press starts the drag before the click sees it.
            .let { if (dragId != null) it.itemDragSource(dragId) else it }
            .padding(start = if (time != null) 16.dp else 6.dp, end = if (trailing != null) 4.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (time != null) {
            Text(
                time,
                style = MaterialTheme.typography.bodyMedium,
                color = timeColor,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .width(64.dp)
                    .let { if (onTimeClick != null) it.clip(RoundedCornerShape(8.dp)).clickable(onClick = onTimeClick) else it }
                    .padding(vertical = 8.dp),
            )
        }
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) { leading() }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            Meta(meta)
        }
        trailing?.invoke()
    }
}

@Composable
fun EventRow(event: Event, onClick: () -> Unit) {
    ItemRow(
        title = event.title.ifEmpty { stringResource(R.string.no_title) },
        meta = listOfNotNull(
            Icons.Outlined.Event to event.calendar,
            event.location?.let { Icons.Outlined.LocationOn to it },
        ),
        onClick = onClick,
        time = if (event.allDay) stringResource(R.string.all_day) else Format.time(event.begin),
        leading = {
            Box(Modifier.width(4.dp).heightIn(min = 36.dp).clip(RoundedCornerShape(2.dp)).background(Color(event.color)))
        },
    )
}

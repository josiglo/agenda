package com.josiglo.agenda.ui

import android.content.ClipData
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.delay

/**
 * Opens on tap and starts dragging item [id] after a long press. Both go
 * through one gesture detector, because a separate click handler and drag
 * detector take each other's taps. Drags stay inside the app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.itemDragSource(id: Long, onClick: () -> Unit): Modifier {
    val click by rememberUpdatedState(onClick)
    val shadow = rememberGraphicsLayer()
    val interactions = remember { MutableInteractionSource() }
    val box = MaterialTheme.colorScheme.surfaceContainerHigh
    val corner = 20.dp
    return this
        .indication(interactions, ripple())
        .drawWithContent {
            shadow.record { this@drawWithContent.drawContent() }
            drawLayer(shadow)
        }
        .dragAndDropSource(
            // The row itself has no background, so the shadow gets the rounded box of its group.
            drawDragDecoration = {
                drawRoundRect(box, cornerRadius = CornerRadius(corner.toPx()))
                drawLayer(shadow)
            },
            block = {
                detectTapGestures(
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        interactions.emit(press)
                        val released = tryAwaitRelease()
                        interactions.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                    },
                    onTap = { click() },
                    onLongPress = {
                        startTransfer(
                            DragAndDropTransferData(
                                ClipData.newPlainText(DRAG_LABEL, id.toString()),
                                localState = id,
                                flags = View.DRAG_FLAG_OPAQUE,
                            ),
                        )
                    },
                )
            },
        )
}

/**
 * Takes items dropped here. [onHover] tells whether a drag is over this
 * target. The target object stays the same across recompositions, since a
 * new one would miss the drag that is already running.
 */
@Composable
fun Modifier.itemDropTarget(onHover: (Boolean) -> Unit = {}, onDrop: (Long) -> Unit): Modifier {
    val hover by rememberUpdatedState(onHover)
    val drop by rememberUpdatedState(onDrop)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = hover(true)

            override fun onExited(event: DragAndDropEvent) = hover(false)

            override fun onEnded(event: DragAndDropEvent) = hover(false)

            override fun onDrop(event: DragAndDropEvent): Boolean {
                hover(false)
                drop(draggedId(event) ?: return false)
                return true
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = acceptsItems, target = target)
}

private val acceptsItems: (DragAndDropEvent) -> Boolean = { draggedId(it) != null }

private fun draggedId(event: DragAndDropEvent): Long? = event.toAndroidDragEvent().localState as? Long

/**
 * Strips along the top and bottom of a list that scroll [list] while a drag
 * hovers over them, so items can be dropped on parts that are off screen.
 * Place it in a Box over the list.
 */
@Composable
fun BoxScope.DragScrollEdges(list: LazyListState) {
    var direction by remember { mutableIntStateOf(0) }
    LaunchedEffect(direction) {
        while (direction != 0) {
            list.scrollBy(direction * 18f)
            delay(16)
        }
    }
    listOf(Alignment.TopCenter to -1, Alignment.BottomCenter to 1).forEach { (alignment, dir) ->
        Box(
            Modifier
                .align(alignment)
                .fillMaxWidth()
                .height(56.dp)
                .itemDropTarget(onHover = { direction = if (it) dir else 0 }, onDrop = {}),
        )
    }
}

private const val DRAG_LABEL = "agenda item"

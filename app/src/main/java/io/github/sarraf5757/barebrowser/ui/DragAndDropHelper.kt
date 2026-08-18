package io.github.sarraf5757.barebrowser.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun rememberDragDropState(
    gridState: LazyGridState,
    onMove: (Int, Int) -> Unit
): DragDropState {
    val state = remember { DragDropState(gridState, onMove) }
    return state
}

class DragDropState(
    val gridState: LazyGridState,
    val onMove: (Int, Int) -> Unit
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var currentPosition: Offset? by mutableStateOf(null)

    fun onDragStart(offset: Offset) {
        gridState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                offset.x.toInt() in item.offset.x..(item.offset.x + item.size.width) &&
                offset.y.toInt() in item.offset.y..(item.offset.y + item.size.height)
            }?.also {
                draggingItemIndex = it.index
                currentPosition = offset
            }
    }

    fun onDragInterrupted() {
        draggingItemIndex = null
        currentPosition = null
    }

    fun onDrag(dragAmount: Offset) {
        val draggingIndex = draggingItemIndex ?: return
        val currentPos = currentPosition ?: return
        
        val newPos = currentPos + dragAmount
        currentPosition = newPos
        
        val targetItem = gridState.layoutInfo.visibleItemsInfo.find { item ->
            item.index != draggingIndex &&
            newPos.x.toInt() in item.offset.x..(item.offset.x + item.size.width) &&
            newPos.y.toInt() in item.offset.y..(item.offset.y + item.size.height)
        }
        
        if (targetItem != null) {
            onMove(draggingIndex, targetItem.index)
            draggingItemIndex = targetItem.index
        }
    }
}

fun Modifier.dragContainer(dragDropState: DragDropState): Modifier {
    return this.pointerInput(dragDropState) {
        detectDragGesturesAfterLongPress(
            onDrag = { change, dragAmount ->
                change.consume()
                dragDropState.onDrag(dragAmount)
            },
            onDragStart = { offset -> dragDropState.onDragStart(offset) },
            onDragEnd = { dragDropState.onDragInterrupted() },
            onDragCancel = { dragDropState.onDragInterrupted() }
        )
    }
}

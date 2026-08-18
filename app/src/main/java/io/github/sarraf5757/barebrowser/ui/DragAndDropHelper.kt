package io.github.sarraf5757.barebrowser.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

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

    internal var draggingItemOffset by mutableStateOf(Offset.Zero)
        private set

    fun onDragStart(index: Int) {
        draggingItemIndex = index
        draggingItemOffset = Offset.Zero
    }

    fun onDragInterrupted() {
        draggingItemIndex = null
        draggingItemOffset = Offset.Zero
    }

    fun onDrag(dragAmount: Offset) {
        val draggingIndex = draggingItemIndex ?: return
        
        draggingItemOffset += dragAmount
        
        val draggingItem = gridState.layoutInfo.visibleItemsInfo.find { it.index == draggingIndex } ?: return
        val currentCenter = Offset(
            draggingItem.offset.x + draggingItemOffset.x + draggingItem.size.width / 2f,
            draggingItem.offset.y + draggingItemOffset.y + draggingItem.size.height / 2f
        )
        
        val targetItem = gridState.layoutInfo.visibleItemsInfo.find { item ->
            item.index != draggingIndex &&
            currentCenter.x.toInt() in item.offset.x..(item.offset.x + item.size.width) &&
            currentCenter.y.toInt() in item.offset.y..(item.offset.y + item.size.height)
        }
        
        if (targetItem != null) {
            onMove(draggingIndex, targetItem.index)
            draggingItemIndex = targetItem.index
        }
    }
}

fun Modifier.dragItem(
    dragDropState: DragDropState,
    index: Int,
    view: android.view.View
): Modifier {
    return this
        .pointerInput(dragDropState, index) {
            detectDragGesturesAfterLongPress(
                onDragStart = { 
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    dragDropState.onDragStart(index) 
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragDropState.onDrag(dragAmount)
                },
                onDragEnd = { dragDropState.onDragInterrupted() },
                onDragCancel = { dragDropState.onDragInterrupted() }
            )
        }
        .graphicsLayer {
            if (dragDropState.draggingItemIndex == index) {
                translationX = dragDropState.draggingItemOffset.x
                translationY = dragDropState.draggingItemOffset.y
                alpha = 0.8f
                scaleX = 1.05f
                scaleY = 1.05f
            }
        }
        .zIndex(if (dragDropState.draggingItemIndex == index) 1f else 0f)
}

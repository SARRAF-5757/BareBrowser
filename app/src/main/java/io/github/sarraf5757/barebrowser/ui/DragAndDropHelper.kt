package io.github.sarraf5757.barebrowser.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
    var draggingItemInitialIndex by mutableStateOf<Int?>(null)
        private set

    var draggingItemCurrentIndex by mutableStateOf<Int?>(null)
        private set

    internal var draggingItemOffset by mutableStateOf(Offset.Zero)
        private set

    fun onDragStart(index: Int) {
        draggingItemInitialIndex = index
        draggingItemCurrentIndex = index
        draggingItemOffset = Offset.Zero
    }

    fun onDragInterrupted() {
        if (draggingItemInitialIndex != null && draggingItemCurrentIndex != null && draggingItemInitialIndex != draggingItemCurrentIndex) {
            onMove(draggingItemInitialIndex!!, draggingItemCurrentIndex!!)
        }
        draggingItemInitialIndex = null
        draggingItemCurrentIndex = null
        draggingItemOffset = Offset.Zero
    }

    fun onDrag(dragAmount: Offset) {
        val initialIndex = draggingItemInitialIndex ?: return
        val currentIndex = draggingItemCurrentIndex ?: return
        
        draggingItemOffset += dragAmount
        
        val initialItem = gridState.layoutInfo.visibleItemsInfo.find { it.index == initialIndex } ?: return
        val currentCenter = Offset(
            initialItem.offset.x + draggingItemOffset.x + initialItem.size.width / 2f,
            initialItem.offset.y + draggingItemOffset.y + initialItem.size.height / 2f
        )
        
        val targetItem = gridState.layoutInfo.visibleItemsInfo.find { item ->
            currentCenter.x.toInt() in item.offset.x..(item.offset.x + item.size.width) &&
            currentCenter.y.toInt() in item.offset.y..(item.offset.y + item.size.height)
        }
        
        if (targetItem != null && targetItem.index != currentIndex) {
            draggingItemCurrentIndex = targetItem.index
        }
    }
}

fun Modifier.dragItem(
    dragDropState: DragDropState,
    index: Int,
    view: android.view.View
): Modifier = composed {
    val isDragging = dragDropState.draggingItemInitialIndex == index
    
    val targetOffset = remember(dragDropState.draggingItemInitialIndex, dragDropState.draggingItemCurrentIndex, index) {
        if (!isDragging && dragDropState.draggingItemInitialIndex != null && dragDropState.draggingItemCurrentIndex != null) {
            val start = dragDropState.draggingItemInitialIndex!!
            val current = dragDropState.draggingItemCurrentIndex!!
            
            if (index in (start + 1)..current) {
                val myItem = dragDropState.gridState.layoutInfo.visibleItemsInfo.find { it.index == index }
                val prevItem = dragDropState.gridState.layoutInfo.visibleItemsInfo.find { it.index == index - 1 }
                if (myItem != null && prevItem != null) {
                    Offset((prevItem.offset.x - myItem.offset.x).toFloat(), (prevItem.offset.y - myItem.offset.y).toFloat())
                } else Offset.Zero
            } else if (index in current until start) {
                val myItem = dragDropState.gridState.layoutInfo.visibleItemsInfo.find { it.index == index }
                val nextItem = dragDropState.gridState.layoutInfo.visibleItemsInfo.find { it.index == index + 1 }
                if (myItem != null && nextItem != null) {
                    Offset((nextItem.offset.x - myItem.offset.x).toFloat(), (nextItem.offset.y - myItem.offset.y).toFloat())
                } else Offset.Zero
            } else {
                Offset.Zero
            }
        } else {
            Offset.Zero
        }
    }
    
    val visualOffset by animateOffsetAsState(targetValue = targetOffset, label = "visualOffset")

    this
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
            if (isDragging) {
                translationX = dragDropState.draggingItemOffset.x
                translationY = dragDropState.draggingItemOffset.y
                alpha = 0.8f
                scaleX = 1.05f
                scaleY = 1.05f
            } else {
                translationX = visualOffset.x
                translationY = visualOffset.y
            }
        }
        .zIndex(if (isDragging) 1f else 0f)
}

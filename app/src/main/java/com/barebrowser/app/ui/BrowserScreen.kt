package com.barebrowser.app.ui

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.barebrowser.app.data.Tab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(viewModel: BrowserViewModel) {
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val isTabGridVisible by viewModel.isTabGridVisible.collectAsState()
    val haptic = LocalHapticFeedback.current

    BackHandler(enabled = isTabGridVisible || activeTabId != null) {
        if (isTabGridVisible) {
            viewModel.toggleTabGrid()
        } else {
            val activeWebView = activeTabId?.let { viewModel.webViewManager.getOrCreateWebView(it) }
            if (activeWebView?.canGoBack() == true) {
                activeWebView.goBack()
            } else {
                // Let system handle back (close app) - this requires more coordination if we want to background
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        activeTabId?.let { id ->
            val tab = tabs.find { it.id == id }
            if (tab != null) {
                WebViewContainer(
                    tabId = id,
                    url = tab.url,
                    webViewManager = viewModel.webViewManager,
                    onRefresh = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                )
            }
        }

        BottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            onAddTab = { viewModel.addTab() },
            onSwipeUp = { viewModel.toggleTabGrid() },
            onNavigate = { viewModel.navigate(it) }
        )

        AnimatedVisibility(
            visible = isTabGridVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            TabGridOverlay(
                tabs = tabs,
                activeTabId = activeTabId,
                onTabClick = { viewModel.setActiveTab(it.id) },
                onCloseTab = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.closeTab(it) 
                },
                onTogglePin = { viewModel.togglePin(it) },
                onAddTab = { viewModel.addTab() }
            )
        }
    }
}

@Composable
fun WebViewContainer(
    tabId: Long,
    url: String,
    webViewManager: WebViewManager,
    onRefresh: () -> Unit
) {
    AndroidView(
        factory = { context ->
            SwipeRefreshLayout(context).apply {
                val webView = webViewManager.getOrCreateWebView(tabId, url)
                addView(webView)
                setOnRefreshListener {
                    webView.reload()
                    onRefresh()
                    isRefreshing = false
                }
            }
        },
        update = { swipeRefresh ->
            val webView = swipeRefresh.getChildAt(0) as WebView
            if (webView.url != url && url != "about:blank") {
                webView.loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun BottomBar(
    modifier: Modifier = Modifier,
    onAddTab: () -> Unit,
    onSwipeUp: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    if (dragAmount.y < -50) {
                        onSwipeUp()
                        change.consume()
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Add Tab Button Container
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onAddTab
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Tab",
                    modifier = Modifier.padding(12.dp)
                )
            }

            // Search Bar Container
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search or type URL") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = RoundedCornerShape(28.dp),
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { onNavigate(text); text = "" }) {
                                Icon(Icons.Default.Search, contentDescription = "Go")
                            }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabGridOverlay(
    tabs: List<Tab>,
    activeTabId: Long?,
    onTabClick: (Tab) -> Unit,
    onCloseTab: (Tab) -> Unit,
    onTogglePin: (Tab) -> Unit,
    onAddTab: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Column {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(tabs, key = { it.id }) { tab ->
                    TabCard(
                        tab = tab,
                        isActive = tab.id == activeTabId,
                        onClick = { onTabClick(tab) },
                        onClose = { onCloseTab(tab) },
                        onTogglePin = { onTogglePin(tab) }
                    )
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                FloatingActionButton(onClick = onAddTab) {
                    Icon(Icons.Default.Add, contentDescription = "Add Tab")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabCard(
    tab: Tab,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onTogglePin: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                if (!tab.isPinned) {
                    onClose()
                    true
                } else false
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) Color.Red else Color.Transparent
            Box(Modifier.fillMaxSize().background(color))
        },
        enableDismissFromStartToEnd = !tab.isPinned,
        enableDismissFromEndToStart = !tab.isPinned
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f),
            border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tab.title,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pin",
                            tint = if (tab.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
                // Preview Image Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (tab.previewPath != null) {
                        // In a real app, load from file. Here we just show a placeholder.
                        Text("Preview", modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}

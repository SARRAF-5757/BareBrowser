package io.github.sarraf5757.barebrowser.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sarraf5757.barebrowser.BrowserViewModel
import io.github.sarraf5757.barebrowser.Tab

/**
 * Main screen for the browser, coordinating the WebView, the URL bar, and the Tab View.
 */
@Composable
fun BrowserScreen(viewModel: BrowserViewModel) {
    // Collecting state from ViewModel
    val tabs by viewModel.tabs.collectAsState()
    val currentTabId by viewModel.currentTabId.collectAsState()
    
    val currentTab = tabs.find { it.id == currentTabId }
    var isTabViewVisible by remember { mutableStateOf(false) }
    var themeColor by remember(currentTabId) { mutableStateOf<Color?>(null) }

    // Intercept back button to close tab view if it's open
    BackHandler(enabled = isTabViewVisible) {
        isTabViewVisible = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. WebView Layer
        if (currentTab != null) {
            WebViewContainer(
                tabs = tabs,
                currentTabId = currentTabId,
                isTabViewVisible = isTabViewVisible,
                onUrlUpdate = { tabId, newUrl -> viewModel.updateTabUrl(tabId, newUrl) },
                onTitleUpdate = { tabId, newTitle -> viewModel.updateTabTitle(tabId, newTitle) },
                onThemeColorUpdate = { color -> themeColor = color },
                onThumbnailCaptured = { tabId, thumb -> viewModel.updateThumbnail(tabId, thumb) },
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            )
            
            // Status bar background color layer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(themeColor ?: MaterialTheme.colorScheme.background)
                    .align(Alignment.TopCenter)
            )
            
            // Overlay a themed background when the page is blank
            if (currentTab.url == "about:blank") {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Placeholder for a logo or welcome message
                }
            }
        }
        
        // 2. Floating URL Bar Layer (Bottom)
        if (!isTabViewVisible && currentTab != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding() // Pushes bar up when keyboard is visible
                    .navigationBarsPadding() // Avoids overlap with system nav bar
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                UrlBar(
                    currentUrl = if (currentTab.url == "about:blank") "" else currentTab.url,
                    onSearch = { query -> viewModel.handleSearchOrUrl(currentTab.id, query) },
                    onNewTab = { viewModel.createNewTab() },
                    onSwipeUp = { isTabViewVisible = true }
                )
            }
        }

        // 3. Tab Grid Overlay Layer
        AnimatedVisibility(
            visible = isTabViewVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            TabView(
                tabs = tabs,
                currentTabId = currentTabId,
                onTabSelected = { tabId ->
                    viewModel.selectTab(tabId)
                    isTabViewVisible = false
                },
                onTabClosed = { tabId -> viewModel.closeTab(tabId) },
                onTabPinned = { tabId -> viewModel.togglePin(tabId) },
                onTabMoved = { from, to -> viewModel.moveTab(from, to) },
                onNewTab = {
                    viewModel.createNewTab()
                    isTabViewVisible = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContainer(
    tabs: List<io.github.sarraf5757.barebrowser.Tab>,
    currentTabId: String?,
    isTabViewVisible: Boolean,
    onUrlUpdate: (String, String) -> Unit,
    onTitleUpdate: (String, String) -> Unit,
    onThemeColorUpdate: (Color?) -> Unit,
    onThumbnailCaptured: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()
    val haptic = LocalHapticFeedback.current
    var hasVibrated by remember { mutableStateOf(false) }

    val currentOnUrlUpdate by rememberUpdatedState(onUrlUpdate)
    val currentOnTitleUpdate by rememberUpdatedState(onTitleUpdate)
    val currentOnThemeColorUpdate by rememberUpdatedState(onThemeColorUpdate)
    val currentOnPageFinished by rememberUpdatedState {
        isRefreshing = false
    }
    
    // We hold a reference to the FrameLayout to query the active WebView
    var frameLayoutRef by remember { mutableStateOf<android.widget.FrameLayout?>(null) }

    val currentOnRefresh by rememberUpdatedState {
        isRefreshing = true
        if (currentTabId != null) {
            val activeWebView = frameLayoutRef?.findViewWithTag<NestedScrollWebView>(currentTabId)
            activeWebView?.reload()
        }
    }

    LaunchedEffect(pullRefreshState.distanceFraction) {
        if (pullRefreshState.distanceFraction >= 1f) {
            if (!hasVibrated) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                hasVibrated = true
            }
        } else {
            if (hasVibrated && pullRefreshState.distanceFraction > 0f) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                hasVibrated = false
            } else if (pullRefreshState.distanceFraction == 0f) {
                hasVibrated = false
            }
        }
    }
    
    LaunchedEffect(isTabViewVisible) {
        if (isTabViewVisible && currentTabId != null) {
            val activeWebView = frameLayoutRef?.findViewWithTag<NestedScrollWebView>(currentTabId)
            val thumb = captureWebViewToThumbnail(activeWebView)
            if (thumb != null) {
                onThumbnailCaptured(currentTabId, thumb)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            currentOnRefresh()
        },
        state = pullRefreshState,
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                android.widget.FrameLayout(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    frameLayoutRef = this
                }
            },
            update = { frameLayout ->
                val existingTags = mutableSetOf<String>()
                for (i in 0 until frameLayout.childCount) {
                    val child = frameLayout.getChildAt(i)
                    existingTags.add(child.tag as String)
                }

                // Create new WebViews for any tabs that don't have one
                for (tab in tabs) {
                    if (!existingTags.contains(tab.id)) {
                        val webView = NestedScrollWebView(frameLayout.context).apply {
                            tag = tab.id
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            
                            // Enable Cookies
                            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    if (url != null) {
                                        currentOnUrlUpdate(tab.id, url)
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Flush cookies
                                    android.webkit.CookieManager.getInstance().flush()
                                    if (tab.id == currentTabId) {
                                        currentOnPageFinished()
                                    }
                                    val jsToInject = """
                                        (function() {
                                            var meta = document.querySelector('meta[name="theme-color"]');
                                            if (meta) return meta.content;
                                            var bgColor = window.getComputedStyle(document.body).backgroundColor;
                                            if (bgColor === 'rgba(0, 0, 0, 0)' || bgColor === 'transparent') {
                                                return window.getComputedStyle(document.documentElement).backgroundColor;
                                            }
                                            return bgColor;
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(jsToInject) { result ->
                                        if (tab.id == currentTabId) {
                                            currentOnThemeColorUpdate(parseColorString(result))
                                        }
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    if (title != null) {
                                        currentOnTitleUpdate(tab.id, title)
                                    }
                                }
                            }
                            loadUrl(tab.url)
                        }
                        frameLayout.addView(webView)
                    }
                }

                // Remove WebViews for tabs that were closed
                val currentTabIds = tabs.map { it.id }.toSet()
                for (i in frameLayout.childCount - 1 downTo 0) {
                    val child = frameLayout.getChildAt(i)
                    val tabId = child.tag as String
                    if (!currentTabIds.contains(tabId)) {
                        (child as? WebView)?.let {
                            it.stopLoading()
                            it.destroy()
                        }
                        frameLayout.removeViewAt(i)
                    }
                }

                // Update visibility and URL loading if URL changed externally
                for (i in 0 until frameLayout.childCount) {
                    val child = frameLayout.getChildAt(i) as NestedScrollWebView
                    val tabId = child.tag as String
                    val tab = tabs.find { it.id == tabId }
                    
                    if (tab != null) {
                        // Check if URL changed externally (e.g. from URL bar search)
                        if (child.url != tab.url && child.url != "${tab.url}/") {
                            child.loadUrl(tab.url)
                        }
                        
                        if (tabId == currentTabId) {
                            child.visibility = android.view.View.VISIBLE
                        } else {
                            child.visibility = android.view.View.GONE
                        }
                    }
                }
            },
            onRelease = { frameLayout ->
                for (i in 0 until frameLayout.childCount) {
                    val child = frameLayout.getChildAt(i)
                    (child as? WebView)?.let {
                        it.stopLoading()
                        it.destroy()
                    }
                }
                frameLayout.removeAllViews()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun UrlBar(
    currentUrl: String,
    onSearch: (String) -> Unit,
    onNewTab: () -> Unit,
    onSwipeUp: () -> Unit
) {
    var textInput by remember(currentUrl) { mutableStateOf(currentUrl) }
    val focusManager = LocalFocusManager.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp)
    ) {
        // Add Tab Button - Material You secondary style
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            onClick = onNewTab,
            modifier = Modifier.fillMaxHeight().aspectRatio(1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Search Bar - Material You variant style
        Surface(
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -20f) {
                            onSwipeUp() // Swipe up to see all tabs
                        }
                    }
                },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp
        ) {
            TextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.fillMaxSize(),
                placeholder = { Text("Search or type URL") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSearch(textInput)
                        focusManager.clearFocus()
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabView(
    tabs: List<Tab>,
    currentTabId: String?,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onTabPinned: (String) -> Unit,
    onTabMoved: (Int, Int) -> Unit,
    onNewTab: () -> Unit
) {
    val gridState = rememberLazyGridState()
    val dragDropState = rememberDragDropState(gridState = gridState, onMove = onTabMoved)
    val view = androidx.compose.ui.platform.LocalView.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom 
        ) {
            // Tab Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f, fill = false) 
            ) {
                itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                    TabCard(
                        tab = tab,
                        isSelected = tab.id == currentTabId,
                        onClick = { onTabSelected(tab.id) },
                        onClose = { onTabClosed(tab.id) },
                        onPin = { onTabPinned(tab.id) },
                        modifier = Modifier.dragItem(dragDropState, index, view)
                    )
                }
            }
            
            // Bottom bar with Add Tab button in Tab View
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    FloatingActionButton(
                        onClick = onNewTab,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Tab")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabCard(
    tab: Tab,
    isSelected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onPin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue != SwipeToDismissBoxValue.Settled && !tab.isPinned) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(dismissState, isDragging) {
        snapshotFlow { 
            !isDragging && 
            dismissState.currentValue == dismissState.targetValue && 
            dismissState.currentValue != SwipeToDismissBoxValue.Settled 
        }.collect { isFullyDismissed ->
            if (isFullyDismissed && !tab.isPinned) {
                onClose()
            }
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !tab.isPinned,
        enableDismissFromEndToStart = !tab.isPinned,
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isDragging = true
                do {
                    val event = awaitPointerEvent()
                } while (event.changes.any { it.pressed })
                isDragging = false
            }
        },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                 else MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (!tab.title.isNullOrBlank()) tab.title else tab.url,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onPin,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (tab.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin Tab",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Content area (placeholder for preview)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    val imageBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = tab.thumbnailBase64) {
                        value = decodeBase64ToImageBitmap(tab.thumbnailBase64)
                    }
                    val bitmap = imageBitmap
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap,
                            contentDescription = "Tab Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = if (!tab.title.isNullOrBlank()) tab.title else tab.url,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 3,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

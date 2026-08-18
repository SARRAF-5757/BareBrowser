package io.github.sarraf5757.barebrowser.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
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
                url = currentTab.url,
                isTabViewVisible = isTabViewVisible,
                onUrlUpdate = { newUrl -> viewModel.updateTabUrl(currentTab.id, newUrl) },
                onThemeColorUpdate = { color -> themeColor = color },
                onThumbnailCaptured = { thumb -> viewModel.updateThumbnail(currentTab.id, thumb) },
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContainer(
    url: String,
    isTabViewVisible: Boolean,
    onUrlUpdate: (String) -> Unit,
    onThemeColorUpdate: (Color?) -> Unit,
    onThumbnailCaptured: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    
    val currentOnUrlUpdate by rememberUpdatedState(onUrlUpdate)
    val currentOnThemeColorUpdate by rememberUpdatedState(onThemeColorUpdate)
    
    // Update WebView URL whenever the state URL changes
    LaunchedEffect(url) {
        if (webViewInstance?.url != url && webViewInstance?.url != "$url/") {
            webViewInstance?.loadUrl(url)
        }
    }
    
    LaunchedEffect(isTabViewVisible) {
        if (isTabViewVisible) {
            val thumb = captureWebViewToThumbnail(webViewInstance)
            if (thumb != null) {
                onThumbnailCaptured(thumb)
            }
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Set transparent so the Material You themed background shows through
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (url != null) {
                            currentOnUrlUpdate(url)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
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
                            currentOnThemeColorUpdate(parseColorString(result))
                        }
                    }
                }
                webChromeClient = WebChromeClient()
                loadUrl(url)
                webViewInstance = this
            }
        },
        update = { /* Updates are handled via LaunchedEffect for cleaner logic */ },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
        modifier = modifier
    )
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
            .padding(horizontal = 16.dp)
    ) {
        // Add Tab Button - Material You secondary style
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            onClick = onNewTab,
            modifier = Modifier.size(56.dp)
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
                modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.align(Alignment.CenterStart)
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
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue != SwipeToDismissBoxValue.Settled && !tab.isPinned) {
                onClose()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !tab.isPinned,
        enableDismissFromEndToStart = !tab.isPinned,
        modifier = modifier,
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
                        text = tab.url,
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
                    val imageBitmap = remember(tab.thumbnailBase64) { decodeBase64ToImageBitmap(tab.thumbnailBase64) }
                    if (imageBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = imageBitmap,
                            contentDescription = "Tab Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = tab.url,
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

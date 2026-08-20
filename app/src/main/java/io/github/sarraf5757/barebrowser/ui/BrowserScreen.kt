package io.github.sarraf5757.barebrowser.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sarraf5757.barebrowser.R
import io.github.sarraf5757.barebrowser.BrowserViewModel
import io.github.sarraf5757.barebrowser.Tab
import kotlin.time.Duration.Companion.milliseconds


// ====================================================
// 1. MAIN BROWSER SCREEN (LAYOUT & DIALOGS)
// ====================================================
@Composable
fun BrowserScreen(viewModel: BrowserViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var frameLayoutRef by remember { mutableStateOf<android.widget.FrameLayout?>(null) }
    // Collecting state from ViewModel
    val tabs by viewModel.tabs.collectAsState()
    val currentTabId by viewModel.currentTabId.collectAsState()
    
    val currentTab = tabs.find { it.id == currentTabId }
    var isTabViewVisible by remember { mutableStateOf(false) }
    var themeColor by remember(currentTabId) { mutableStateOf<Color?>(null) }
    var canGoForward by remember(currentTabId) { mutableStateOf(false) }
    var showLastPageToast by remember { mutableStateOf(false) }
    
    // State for handling form resubmission dialogs
    var resubmissionMessages by remember { mutableStateOf<Pair<android.os.Message, android.os.Message>?>(null) }
    
    LaunchedEffect(showLastPageToast) {
        if (showLastPageToast) {
            kotlinx.coroutines.delay(2500.milliseconds)
            showLastPageToast = false
        }
    }

    // Intercept back button to close tab view if it's open
    BackHandler(enabled = isTabViewVisible) {
        isTabViewVisible = false
    }
    
    // Intercept back button for WebView navigation
    BackHandler(enabled = !isTabViewVisible && currentTab != null) {
        val activeWebView = frameLayoutRef?.findViewWithTag<WebView>(currentTabId)
        
        var canActuallyGoBack = false
        if (activeWebView != null) {
            val backForwardList = activeWebView.copyBackForwardList()
            val currentIndex = backForwardList.currentIndex
            if (currentIndex > 0) {
                val previousUrl = backForwardList.getItemAtIndex(currentIndex - 1).url
                canActuallyGoBack = previousUrl != "about:blank"
            }
        }
        
        if (activeWebView != null && canActuallyGoBack) {
            activeWebView.goBack()
            showLastPageToast = false
        } else {
            // If it's a blank tab, exit the app
            if (currentTab?.url == "about:blank" || currentTab?.url?.isEmpty() == true) {
                (context as? android.app.Activity)?.finish()
            } else {
                // We are on an actual webpage result that cannot go back further (or only to about:blank)
                if (showLastPageToast) {
                    if (currentTabId != null) {
                        viewModel.closeTab(currentTabId!!)
                    }
                    showLastPageToast = false
                } else {
                    showLastPageToast = true
                }
            }
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        // --- A. WebView Layer (The actual webpage) ---
        if (currentTab != null) {
            WebViewContainer(
                tabs = tabs,
                currentTabId = currentTabId,
                isTabViewVisible = isTabViewVisible,
                onUrlUpdate = { tabId, newUrl -> viewModel.updateTabUrl(tabId, newUrl) },
                onTitleUpdate = { tabId, newTitle -> viewModel.updateTabTitle(tabId, newTitle) },
                onThemeColorUpdate = { color -> themeColor = color },
                onCanGoForwardUpdate = { canGoForward = it },
                onThumbnailCaptured = { tabId, thumb -> viewModel.updateThumbnail(tabId, thumb) },
                onFormResubmissionRequest = { dont, resend -> resubmissionMessages = Pair(dont, resend) },
                frameLayoutRef = frameLayoutRef,
                onFrameLayoutCreated = { frameLayoutRef = it },
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


        // --- B. Floating URL Bar Layer (Bottom Search Bar) ---
        if (!isTabViewVisible && currentTab != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding() // Pushes bar up when keyboard is visible
                    .navigationBarsPadding() // Avoids overlap with system nav bar
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedVisibility(
                        visible = showLastPageToast,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { 50 })
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface
                        ) {
                            Text(
                                text = "This is the last page",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    UrlBar(
                        currentUrl = if (currentTab.url == "about:blank") "" else currentTab.url,
                        canGoForward = canGoForward,
                        onSearch = { query -> viewModel.handleSearchOrUrl(currentTab.id, query) },
                        onNewTab = { viewModel.createNewTab() },
                        onSwipeUp = { isTabViewVisible = true },
                        onReload = {
                            val activeWebView = frameLayoutRef?.findViewWithTag<WebView>(currentTab.id)
                            activeWebView?.reload()
                        },
                        onForward = {
                            val activeWebView = frameLayoutRef?.findViewWithTag<WebView>(currentTab.id)
                            activeWebView?.goForward()
                        }
                    )
                }
            }
        }


        // --- C. Tab Grid Overlay Layer (Displays all open tabs) ---
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

    // --- D. Form Resubmission Dialog ---
    if (resubmissionMessages != null) {
        AlertDialog(
            onDismissRequest = { 
                resubmissionMessages?.first?.sendToTarget() // dontResend
                resubmissionMessages = null 
            },
            title = { Text("Confirm Form Resubmission") },
            text = { Text("The page you're looking for used information that you entered. Returning to that page might cause any action you took to be repeated. Do you want to continue?") },
            confirmButton = {
                TextButton(onClick = {
                    resubmissionMessages?.second?.sendToTarget() // resend
                    resubmissionMessages = null
                }) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    resubmissionMessages?.first?.sendToTarget() // dontResend
                    resubmissionMessages = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}


// ====================================================
// 2. WEBVIEW CONTAINER (NATIVE ANDROID VIEW INTEROP)
// ====================================================
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContainer(
    tabs: List<Tab>,
    currentTabId: String?,
    isTabViewVisible: Boolean,
    onUrlUpdate: (String, String) -> Unit,
    onTitleUpdate: (String, String) -> Unit,
    onThemeColorUpdate: (Color?) -> Unit,
    onCanGoForwardUpdate: (Boolean) -> Unit,
    onThumbnailCaptured: (String, String) -> Unit,
    onFormResubmissionRequest: (android.os.Message, android.os.Message) -> Unit,
    frameLayoutRef: android.widget.FrameLayout?,
    onFrameLayoutCreated: (android.widget.FrameLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    // --- State Hoisting & Activity Result Launchers ---
    val currentOnUrlUpdate by rememberUpdatedState(onUrlUpdate)
    val currentOnTitleUpdate by rememberUpdatedState(onTitleUpdate)
    val upgradedUrls = remember { mutableSetOf<String>() }
    val currentOnThemeColorUpdate by rememberUpdatedState(onThemeColorUpdate)
    val currentOnCanGoForwardUpdate by rememberUpdatedState(onCanGoForwardUpdate)
    val currentOnFormResubmission by rememberUpdatedState(onFormResubmissionRequest)
    var fileChooserCallback by remember { mutableStateOf<android.webkit.ValueCallback<Array<android.net.Uri>>?>(null) }
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val uris = if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                Array(count) { i -> data.clipData!!.getItemAt(i).uri }
            } else if (data?.data != null) {
                arrayOf(data.data!!)
            } else {
                null
            }
            fileChooserCallback?.onReceiveValue(uris)
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }
    
    // --- Effect: Capture Thumbnail when opening Tab View ---
    LaunchedEffect(isTabViewVisible) {
        if (isTabViewVisible && currentTabId != null) {
            val activeWebView = frameLayoutRef?.findViewWithTag<WebView>(currentTabId)
            if (activeWebView != null) {
                val thumb = captureWebViewToThumbnail(activeWebView)
                onThumbnailCaptured(currentTabId, thumb)
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            // --- Native View Initialization ---
            factory = { context ->
                android.widget.FrameLayout(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    onFrameLayoutCreated(this)
                }
            },
            update = { frameLayout ->
                val existingTags = mutableSetOf<String>()
                for (i in 0 until frameLayout.childCount) {
                    val child = frameLayout.getChildAt(i)
                    existingTags.add(child.tag as String)
                }

                // --- WebView Creation & Settings ---
                // Create new WebViews for any tabs that don't have one
                for (tab in tabs) {
                    if (!existingTags.contains(tab.id)) {
                        val webView = WebView(frameLayout.context).apply {
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
                            
                            setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, contentLength ->
                                try {
                                    val request = android.app.DownloadManager.Request(android.net.Uri.parse(downloadUrl)).apply {
                                        setMimeType(mimeType)
                                        val cookie = android.webkit.CookieManager.getInstance().getCookie(downloadUrl)
                                        addRequestHeader("Cookie", cookie)
                                        addRequestHeader("User-Agent", userAgent)
                                        setDescription("Downloading file...")
                                        
                                        val filename = android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                                        setTitle(filename)
                                        
                                        setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
                                    }
                                    val downloadManager = frameLayout.context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                    downloadManager.enqueue(request)
                                    android.widget.Toast.makeText(frameLayout.context, "Download started", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.util.Log.e("BrowserScreen", "Download failed", e)
                                    android.widget.Toast.makeText(frameLayout.context, "Failed to start download", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            
                            // --- WebViewClient: Handles Navigation, Errors, and AdBlocking ---
                            webViewClient = object : WebViewClient() {
                                override fun onSafeBrowsingHit(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    threatType: Int,
                                    callback: android.webkit.SafeBrowsingResponse?
                                ) {
                                    callback?.showInterstitial(true)
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                    val url = request?.url ?: return false
                                    val urlString = url.toString()
                                    
                                    // Handle non-web protocols (intent://, mailto:, etc.)
                                    if (urlString.startsWith("intent://") || 
                                        urlString.startsWith("mailto:") || 
                                        urlString.startsWith("tel:") || 
                                        urlString.startsWith("sms:")) {
                                        try {
                                            val intent = android.content.Intent.parseUri(urlString, android.content.Intent.URI_INTENT_SCHEME)
                                            intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                                            view?.context?.startActivity(intent)
                                            return true
                                        } catch (_: Exception) {
                                            // Fallback if app not found or parsing fails
                                        }
                                    }

                                    // HTTPS Everywhere logic
                                    if (url.scheme == "http" && request.isForMainFrame) {
                                        if (!upgradedUrls.contains(urlString)) {
                                            val httpsUrl = urlString.replaceFirst("http://", "https://")
                                            upgradedUrls.add(urlString)
                                            view?.loadUrl(httpsUrl)
                                            return true
                                        }
                                    }

                                    return false
                                }

                                override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                    val failedUrl = error?.url ?: return
                                    val httpUrl = failedUrl.replaceFirst("https://", "http://")
                                    if (upgradedUrls.contains(httpUrl)) {
                                        // upgraded HTTP URL to HTTPS failed SSL. Fallback to HTTP
                                        handler?.cancel()
                                        view?.loadUrl(httpUrl)
                                    } else {
                                        super.onReceivedSslError(view, handler, error)
                                    }
                                }

                                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                    if (request?.isForMainFrame == true) {
                                        val failedUrl = request.url?.toString() ?: ""
                                        val httpUrl = failedUrl.replaceFirst("https://", "http://")
                                        if (upgradedUrls.contains(httpUrl)) {
                                            // Upgrade to HTTPS connection refused. Fallback to HTTP.
                                            view?.loadUrl(httpUrl)
                                            return
                                        }
                                    }
                                    super.onReceivedError(view, request, error)
                                }

                                override fun onFormResubmission(view: WebView?, dontResend: android.os.Message?, resend: android.os.Message?) {
                                    if (dontResend != null && resend != null) {
                                        currentOnFormResubmission(dontResend, resend)
                                    } else {
                                        super.onFormResubmission(view, dontResend, resend)
                                    }
                                }

                                override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                                    if (AdBlocker.shouldBlock(request)) {
                                        return AdBlocker.createEmptyResponse()
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    if (url != null) {
                                        currentOnUrlUpdate(tab.id, url)
                                    }
                                    if (tab.id == currentTabId) {
                                        currentOnCanGoForwardUpdate(view?.canGoForward() == true)
                                    }
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    android.webkit.CookieManager.getInstance().flush()
                                    
                                    // Inject CSS to hide ad elements
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            var style = document.createElement('style');
                                            style.innerHTML = '.ad, .ads, .advert, .banner, .ad-banner, .ad_banner, [id^="ad-"], [class*="ad-"], [class*="banner"], iframe[src*="ads"], .ad-container, .ad-wrapper, .ad-slot, .adbox, .ad-box { display: none !important; }';
                                            document.head.appendChild(style);
                                        })();
                                        """.trimIndent()
                                    ) {}

                                    if (tab.id == currentTabId) {
                                        currentOnCanGoForwardUpdate(view?.canGoForward() == true)
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
                            // --- WebChromeClient: Handles Titles and File Choosers ---
                            webChromeClient = object : WebChromeClient() {
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    if (title != null) {
                                        currentOnTitleUpdate(tab.id, title)
                                    }
                                }
                                
                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    fileChooserCallback = filePathCallback
                                    val intent = fileChooserParams?.createIntent() ?: android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                        type = "*/*"
                                    }
                                    try {
                                        filePickerLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        fileChooserCallback?.onReceiveValue(null)
                                        fileChooserCallback = null
                                        return false
                                    }
                                    return true
                                }
                            }
                            loadUrl(tab.url)
                        }
                        frameLayout.addView(webView)
                    }
                }

                // --- WebView Cleanup ---
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

                // --- WebView State Synchronization (Visibility & URL Loading) ---
                // Update visibility and URL loading if navigation was triggered by user
                for (i in 0 until frameLayout.childCount) {
                    val child = frameLayout.getChildAt(i) as WebView
                    val tabId = child.tag as String
                    val tab = tabs.find { it.id == tabId }
                    
                    if (tab != null) {
                        // Check if a new navigation was explicitly triggered by the user
                        // or if this is a new WebView that hasn't loaded its initial URL yet.
                        val lastProcessedTrigger = child.getTag(R.id.nav_trigger) as? Int
                        
                        if (lastProcessedTrigger == null || tab.navigationTrigger > lastProcessedTrigger) {
                            child.loadUrl(tab.url)
                            child.setTag(R.id.nav_trigger, tab.navigationTrigger)
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


// ====================================================
// 3. URL BAR COMPONENT
// ====================================================
@Composable
fun UrlBar(
    currentUrl: String,
    canGoForward: Boolean,
    onSearch: (String) -> Unit,
    onNewTab: () -> Unit,
    onSwipeUp: () -> Unit,
    onReload: () -> Unit,
    onForward: () -> Unit
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
        // Add Tab Button
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
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

        // Search Bar
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
                onValueChange = { newValue: String -> textInput = newValue },
                modifier = Modifier.fillMaxSize(),
                placeholder = { Text("Search or type URL") },
                singleLine = true,
                leadingIcon = { 
                    val haptic = LocalHapticFeedback.current
                    val isEditing = textInput != currentUrl
                    val showReload = !isEditing && currentUrl != "about:blank" && currentUrl.isNotEmpty()
                    
                    if (showReload) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onReload()
                            },
                            modifier = Modifier.padding(start = 4.dp).size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reload",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null) 
                    }
                },
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
                trailingIcon = if (canGoForward && textInput == currentUrl) {
                    {
                        val haptic = LocalHapticFeedback.current
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onForward()
                            },
                            modifier = Modifier.padding(end = 4.dp).size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Forward",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else null,
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}


// ====================================================
// 4. TAB VIEW GRID COMPONENT
// ====================================================
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
                itemsIndexed(items = tabs, key = { _: Int, tab: Tab -> tab.id }) { index: Int, tab: Tab ->
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


// ====================================================
// 5. INDIVIDUAL TAB CARD COMPONENT
// ====================================================
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
        },
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
        enableDismissFromStartToEnd = !tab.isPinned,
        enableDismissFromEndToStart = !tab.isPinned
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
            val displayTitle = if (tab.url == "about:blank") "New tab" else if (!tab.title.isNullOrBlank()) tab.title else tab.url

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayTitle,
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
                        if (tab.thumbnailBase64 != null) {
                            value = decodeBase64ToImageBitmap(tab.thumbnailBase64)
                        }
                    }
                    val bitmap = imageBitmap

                    if (bitmap != null && tab.url != "about:blank") {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap,
                            contentDescription = "Tab Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else if (tab.url != "about:blank") {
                        Text(
                            text = displayTitle,
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

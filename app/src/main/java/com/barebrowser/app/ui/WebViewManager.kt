package com.barebrowser.app.ui

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.ConcurrentHashMap

class WebViewManager(private val context: Context) {
    private val webViewCache = ConcurrentHashMap<Long, WebView>()

    fun getOrCreateWebView(tabId: Long, initialUrl: String? = null): WebView {
        return webViewCache.getOrPut(tabId) {
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                webViewClient = WebViewClient()
                initialUrl?.let { loadUrl(it) }
            }
        }
    }

    fun removeWebView(tabId: Long) {
        webViewCache.remove(tabId)?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
    }

    fun clear() {
        webViewCache.forEach { (_, webView) ->
            webView.destroy()
        }
        webViewCache.clear()
    }
}

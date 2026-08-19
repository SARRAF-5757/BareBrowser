package io.github.sarraf5757.barebrowser.ui

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlocker {
    // A simple, hardcoded list of common ad/tracking domains.
    private val AD_HOSTS = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "pubmatic.com",
        "taboola.com",
        "amazon-adsystem.com",
        "criteo.com",
        "outbrain.com",
        "adform.net",
        "rubiconproject.com",
        "openx.net",
        "adnxs.com",
        "adsrvr.org",
        "ads.twitter.com",
        "pixel.facebook.com"
    )

    fun shouldBlock(request: WebResourceRequest?): Boolean {
        val host = request?.url?.host ?: return false
        return AD_HOSTS.any { host.contains(it) }
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }
}

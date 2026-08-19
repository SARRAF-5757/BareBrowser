package io.github.sarraf5757.barebrowser.ui

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.util.HashSet

object AdBlocker {
    private val domainsLock = Any()
    private val blockedDomains = HashSet<String>()
    private var isInitialized = false
    
    // StevenBlack's hosts list
    private const val LIST_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    private const val FILE_NAME = "adblock_hosts.txt"
    private const val UPDATE_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days

    private val blockedPaths = arrayOf(
        "/ads/", "/ad/", "/banner/", "/banners/", "/tracking/", 
        "ad_server", "ad-server", "ad_banner", "ad-banner", 
        "ad_image", "ad-image", "banner_ad", "banner-ad",
        "/pixel?", "/tracking_pixel"
    )

    fun initialize(context: Context) {
        if (isInitialized) {
            return
        }
        isInitialized = true

        val file = File(context.filesDir, FILE_NAME)

        // Launch a background thread (similar to std::thread)
        val backgroundThread = Thread(Runnable {
            // Load existing list if available
            if (file.exists()) {
                loadListFromFile(file)
            }

            // Check if we need to update (doesn't exist or older than 7 days)
            var needsUpdate = false
            if (!file.exists()) {
                needsUpdate = true
            } else {
                val currentTime = System.currentTimeMillis()
                val lastModified = file.lastModified()
                if ((currentTime - lastModified) > UPDATE_INTERVAL_MS) {
                    needsUpdate = true
                }
            }
            
            if (needsUpdate) {
                downloadAndUpdateList(file)
            }
        })
        backgroundThread.start()
    }

    private fun loadListFromFile(file: File) {
        val newDomains = HashSet<String>()
        val reader = BufferedReader(FileReader(file))
        var line: String? = reader.readLine()
        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.startsWith("0.0.0.0") && trimmed != "0.0.0.0 0.0.0.0") {
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    newDomains.add(parts[1])
                }
            }
            line = reader.readLine()
        }
        reader.close()

        synchronized(domainsLock) {
            blockedDomains.clear()
            blockedDomains.addAll(newDomains)
        }
        Log.d("AdBlocker", "Loaded " + newDomains.size + " blocked domains from cache.")
    }

    private fun downloadAndUpdateList(file: File) {
        Log.d("AdBlocker", "Downloading updated adblock list...")
        try {
            val connection = URL(LIST_URL).openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val input = connection.getInputStream()
            val output = FileOutputStream(file)

            val buffer = ByteArray(4096)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                output.write(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
            
            input.close()
            output.close()
            
            Log.d("AdBlocker", "Download complete. Reloading domains.")
            loadListFromFile(file)
        } catch (e: Exception) {
            Log.e("AdBlocker", "Failed to download adblock list. Offline or timeout.", e)
        }
    }

    fun shouldBlock(request: WebResourceRequest?): Boolean {
        if (request == null) {
            return false
        }
        val url = request.url
        if (url == null) {
            return false
        }

        val tempHost = url.host
        if (tempHost == null) {
            return false
        }
        var host = tempHost.lowercase()
        
        val tempPath = url.path
        val path: String = if (tempPath == null) {
            ""
        } else {
            tempPath.lowercase()
        }

        
        // Path-based blocking (first-party ads and trackers)
        for (i in blockedPaths.indices) {
            val blockedPath = blockedPaths[i]
            if (path.contains(blockedPath)) {
                return true
            }
        }

        // Domain-based blocking (third-party ad networks)
        synchronized(domainsLock) {
            if (blockedDomains.isEmpty()) {
                return false
            }
                
            var currentHost = host
            while (currentHost.contains(".")) {
                if (blockedDomains.contains(currentHost)) {
                    return true
                }
                // Strip the first subdomain (e.g., ads.google.com -> google.com)
                val firstDot = currentHost.indexOf('.')
                if (firstDot == -1 || firstDot == currentHost.length - 1) {
                    break
                }
                currentHost = currentHost.substring(firstDot + 1)
            }
        }
        
        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }
}

package io.github.sarraf5757.barebrowser

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.net.URLEncoder
import java.util.UUID

/**
 * Data model for a Browser Tab.
 * Simplified and consolidated into the same file as ViewModel for clarity in this small project.
 */
@Serializable
data class Tab(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "about:blank",
    val title: String? = null,
    val isPinned: Boolean = false,
    val lastAccessed: Long = System.currentTimeMillis(),
    val thumbnailBase64: String? = null
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("bare_browser_prefs", Context.MODE_PRIVATE)
    
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()
    
    private val _currentTabId = MutableStateFlow<String?>(null)
    val currentTabId: StateFlow<String?> = _currentTabId.asStateFlow()
    
    init {
        loadTabs()
    }
    
    private fun loadTabs() {
        val tabsJson = prefs.getString("tabs", "[]") ?: "[]"
        val loadedTabs = try {
            Json.decodeFromString<List<Tab>>(tabsJson)
        } catch (e: Exception) {
            emptyList()
        }
        
        val savedCurrentId = prefs.getString("currentTabId", null)
        
        if (loadedTabs.isEmpty()) {
            val initialTab = Tab()
            _tabs.value = listOf(initialTab)
            _currentTabId.value = initialTab.id
        } else {
            val activeTab = loadedTabs.find { it.id == savedCurrentId } ?: loadedTabs.firstOrNull()
            if (activeTab != null && activeTab.url != "about:blank" && activeTab.url.isNotBlank()) {
                val newBlankTab = Tab(url = "about:blank")
                _tabs.value = loadedTabs + newBlankTab
                _currentTabId.value = newBlankTab.id
            } else {
                _tabs.value = loadedTabs
                _currentTabId.value = activeTab?.id
            }
        }
    }
    
    private fun saveTabs() {
        prefs.edit().apply {
            putString("tabs", Json.encodeToString(_tabs.value))
            putString("currentTabId", _currentTabId.value)
            apply()
        }
    }
    
    fun createNewTab() {
        val newTab = Tab()
        _tabs.value = _tabs.value + newTab
        _currentTabId.value = newTab.id
        saveTabs()
    }
    
    fun closeTab(tabId: String) {
        val currentTabs = _tabs.value
        val tabToClose = currentTabs.find { it.id == tabId }
        
        // Safety check: Don't close if it's pinned
        if (tabToClose?.isPinned == true) return 
        
        val remainingTabs = currentTabs.filter { it.id != tabId }
        
        if (remainingTabs.isEmpty()) {
            // Always keep at least one tab open
            val fallbackTab = Tab()
            _tabs.value = listOf(fallbackTab)
            _currentTabId.value = fallbackTab.id
        } else {
            _tabs.value = remainingTabs
            // If the closed tab was the active one, switch to another
            if (_currentTabId.value == tabId) {
                _currentTabId.value = remainingTabs.last().id
            }
        }
        saveTabs()
    }
    
    fun selectTab(tabId: String) {
        val currentTabs = _tabs.value
        val foundTab = currentTabs.find { it.id == tabId }
        
        if (foundTab != null) {
            val updatedTabs = currentTabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(lastAccessed = System.currentTimeMillis())
                } else {
                    tab
                }
            }
            _tabs.value = updatedTabs
            _currentTabId.value = tabId
            saveTabs()
        }
    }
    
    fun togglePin(tabId: String) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) {
                tab.copy(isPinned = !tab.isPinned)
            } else {
                tab
            }
        }
        saveTabs()
    }
    
    fun updateTabUrl(tabId: String, newUrl: String) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) {
                tab.copy(url = newUrl)
            } else {
                tab
            }
        }
        saveTabs()
    }
    
    fun updateTabTitle(tabId: String, newTitle: String) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) {
                tab.copy(title = newTitle)
            } else {
                tab
            }
        }
        saveTabs()
    }
    
    fun updateThumbnail(tabId: String, base64: String) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) {
                tab.copy(thumbnailBase64 = base64)
            } else {
                tab
            }
        }
        saveTabs()
    }
    
    fun moveTab(fromIndex: Int, toIndex: Int) {
        val currentTabs = _tabs.value.toMutableList()
        if (fromIndex in currentTabs.indices && toIndex in currentTabs.indices) {
            val tab = currentTabs.removeAt(fromIndex)
            currentTabs.add(toIndex, tab)
            _tabs.value = currentTabs
            saveTabs()
        }
    }
    
    fun handleSearchOrUrl(tabId: String, query: String) {
        val trimmedInput = query.trim()
        if (trimmedInput.isEmpty()) return
        
        // Simplified URL detection: checks for a dot and no spaces, or starts with http
        val isUrl = trimmedInput.matches(Regex("^(https?://)?[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$"))
        
        val finalUrl = if (isUrl) {
            if (!trimmedInput.startsWith("http://") && !trimmedInput.startsWith("https://")) {
                "https://$trimmedInput"
            } else {
                trimmedInput
            }
        } else {
            // Google search fallback
            "https://www.google.com/search?q=${URLEncoder.encode(trimmedInput, "UTF-8")}"
        }
        
        updateTabUrl(tabId, finalUrl)
    }
    fun openUrlInNewTab(url: String) {
        val currentId = _currentTabId.value
        val currentTab = _tabs.value.find { it.id == currentId }
        
        // If the current tab is blank, just reuse it
        if (currentTab != null && currentTab.url == "about:blank") {
            updateTabUrl(currentTab.id, url)
        } else {
            val newTab = Tab(url = url)
            _tabs.value = _tabs.value + newTab
            _currentTabId.value = newTab.id
            saveTabs()
        }
    }
}
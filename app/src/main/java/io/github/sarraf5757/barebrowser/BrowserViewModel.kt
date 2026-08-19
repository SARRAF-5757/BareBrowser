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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.viewModelScope
import java.net.URLEncoder
import java.util.UUID

/**
 * Data structure of a tab
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

/**
 * ViewModel managing the state of the browser: the list of tabs, the active tab, and persistence (SharedPreferences)
 */
class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("bare_browser_prefs", Context.MODE_PRIVATE)
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())    // Internal state flow for tabs list
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()
    private val _currentTabId = MutableStateFlow<String?>(null)     // Internal state flow for the currently active tab ID
    val currentTabId: StateFlow<String?> = _currentTabId.asStateFlow()
    
    init {
        loadTabs()  // Initialize state from persistent storage upon creation
    }
    
    /**
     * Load tabs from SharedPreferences
     */
    private fun loadTabs() {
        val tabsJson = prefs.getString("tabs", "[]")!!
        val loadedTabs = Json.decodeFromString<List<Tab>>(tabsJson)
        val savedCurrentId = prefs.getString("currentTabId", null)
        
        if (loadedTabs.isEmpty()) {
            val initialTab = Tab()
            _tabs.value = listOf(initialTab)
            _currentTabId.value = initialTab.id
            return
        }

        var activeTab: Tab? = null
        for (tab in loadedTabs) {
            if (tab.id == savedCurrentId) {
                activeTab = tab
                break
            }
        }

        // If a non-blank tab was restored as activeTab, inject a fresh blank tab and set it as active
        if (activeTab != null && activeTab.url != "about:blank" && activeTab.url.isNotBlank()) {
            val newBlankTab = Tab(url = "about:blank")
            val updatedTabs = mutableListOf<Tab>()
            updatedTabs.addAll(loadedTabs)
            updatedTabs.add(newBlankTab)

            _tabs.value = updatedTabs
            _currentTabId.value = newBlankTab.id
        } else {
            _tabs.value = loadedTabs
            if (activeTab != null) {
                _currentTabId.value = activeTab.id
            }
        }
    }
    
    /**
     * Helper - Persist the current state to SharedPreferences
     */
    private fun saveTabs() {
        val currentTabs = _tabs.value
        val currentId = _currentTabId.value
        
        // Offload JSON serialization to IO thread (to prevent UI stutters)
        viewModelScope.launch(Dispatchers.IO) {
            val json = Json.encodeToString(currentTabs)
            prefs.edit().apply {
                putString("tabs", json)
                putString("currentTabId", currentId)
                apply()
            }
        }
    }
    
    /**
     * Create a new blank tab and sets it as the active tab
     */
    fun createNewTab() {
        val newTab = Tab()
        _tabs.value += newTab
        _currentTabId.value = newTab.id
        saveTabs()
    }
    
    /**
     * Close the tab (unpinned) with the specified ID & ensure at least one blank tab is always available
     */
    fun closeTab(tabId: String) {
        val currentTabs = _tabs.value
        val remainingTabs = currentTabs.filter { it.id != tabId }
        
        if (remainingTabs.isEmpty()) {
            val fallbackTab = Tab()
            _tabs.value = listOf(fallbackTab)
            _currentTabId.value = fallbackTab.id
        } else {
            _tabs.value = remainingTabs
            if (_currentTabId.value == tabId) {
                _currentTabId.value = remainingTabs.last().id
            }
        }
        saveTabs()
    }
    
    /**
     * Switch the active tab to the specified ID and updates its last accessed timestamp
     */
    fun selectTab(tabId: String) {
        val currentTabs = _tabs.value
        var tabExists = false
        
        for (tab in currentTabs) {
            if (tab.id == tabId) {
                tabExists = true
                break
            }
        }
        
        if (tabExists) {
            val updatedTabs = mutableListOf<Tab>()
            for (tab in currentTabs) {
                if (tab.id == tabId) {
                    updatedTabs.add(tab.copy(lastAccessed = System.currentTimeMillis()))
                } else {
                    updatedTabs.add(tab)
                }
            }
            
            _tabs.value = updatedTabs
            _currentTabId.value = tabId
            saveTabs()
        }
    }

    /**
     * Tab Setter - Set the pinned bool in a tab
     */
    fun togglePin(tabId: String) {
        val currentTabs = _tabs.value
        val updatedTabs = mutableListOf<Tab>()

        for (tab in currentTabs) {
            if (tab.id == tabId) {
                updatedTabs.add(tab.copy(isPinned = !tab.isPinned))
            } else {
                updatedTabs.add(tab)
            }
        }
        
        _tabs.value = updatedTabs
        saveTabs()
    }

    /**
     * Tab Setter - Change the url of an existing tab
     */
    fun updateTabUrl(tabId: String, newUrl: String) {
        val currentTabs = _tabs.value
        val updatedTabs = mutableListOf<Tab>()
        
        for (tab in currentTabs) {
            if (tab.id == tabId) {
                updatedTabs.add(tab.copy(url = newUrl))
            } else {
                updatedTabs.add(tab)
            }
        }
        
        _tabs.value = updatedTabs
        saveTabs()
    }

    /**
     * Tab Setter - Change the title of the tab with the string
     */
    fun updateTabTitle(tabId: String, newTitle: String) {
        val currentTabs = _tabs.value
        val updatedTabs = mutableListOf<Tab>()
        
        for (tab in currentTabs) {
            if (tab.id == tabId) {
                updatedTabs.add(tab.copy(title = newTitle))
            } else {
                updatedTabs.add(tab)
            }
        }
        
        _tabs.value = updatedTabs
        saveTabs()
    }

    /**
     * Tab Setter - Set the thumbnail image
     */
    fun updateThumbnail(tabId: String, base64: String) {
        val currentTabs = _tabs.value
        val updatedTabs = mutableListOf<Tab>()
        
        for (tab in currentTabs) {
            if (tab.id == tabId) {
                updatedTabs.add(tab.copy(thumbnailBase64 = base64))
            } else {
                updatedTabs.add(tab)
            }
        }
        
        _tabs.value = updatedTabs
        saveTabs()
    }
    
    /**
     * Reorder _tabs by moving a tab from one index to another
     */
    fun moveTab(fromIndex: Int, toIndex: Int) {
        val currentTabs = _tabs.value.toMutableList()
        if (fromIndex in currentTabs.indices && toIndex in currentTabs.indices) {
            val tab = currentTabs.removeAt(fromIndex)
            currentTabs.add(toIndex, tab)
            _tabs.value = currentTabs
            saveTabs()
        }
    }
    
    /**
     * Parse user input
     * if URL, format it as such
     * otherwise, treat it as a Google search query
     */
    fun handleSearchOrUrl(tabId: String, query: String) {
        val trimmedInput = query.trim()
        if (trimmedInput.isEmpty()) return
        
        // Check for a dot and no spaces, or starts with http/https
        val isUrl = trimmedInput.matches(Regex("^(https?://)?[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$"))
        
        val finalUrl = if (isUrl) {
            if (!trimmedInput.startsWith("http://") && !trimmedInput.startsWith("https://")) {
                "https://$trimmedInput"
            } else {
                trimmedInput
            }
        } else {
            "https://www.google.com/search?q=${URLEncoder.encode(trimmedInput, "UTF-8")}"
        }
        
        updateTabUrl(tabId, finalUrl)
    }
    
    /**
     * Open a URL in a new tab
     * if the current active tab is blank, reuse that tab instead
     */
    fun openUrlInNewTab(url: String) {
        val currentId = _currentTabId.value
        val currentTab = _tabs.value.find { it.id == currentId }
        
        if (currentTab != null && currentTab.url == "about:blank") {
            updateTabUrl(currentTab.id, url)
        } else {
            val newTab = Tab(url = url)
            _tabs.value += newTab
            _currentTabId.value = newTab.id
            saveTabs()
        }
    }
}

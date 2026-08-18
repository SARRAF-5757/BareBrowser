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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.viewModelScope
import java.net.URLEncoder
import java.util.UUID

/**
 * Data model representing a single Browser Tab.
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
 * ViewModel managing the state of the browser, including the list of tabs, the active tab,
 * and persistence using SharedPreferences.
 */
class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("bare_browser_prefs", Context.MODE_PRIVATE)
    
    // Internal state flow for tabs list
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()
    
    // Internal state flow for the currently active tab ID
    private val _currentTabId = MutableStateFlow<String?>(null)
    val currentTabId: StateFlow<String?> = _currentTabId.asStateFlow()
    
    init {
        // Initialize state from persistent storage upon creation
        loadTabs()
    }
    
    /**
     * Loads tabs from SharedPreferences. If restoring from a previous session where a website was open,
     * it automatically injects a fresh blank tab and sets it as active.
     */
    private fun loadTabs() {
        var tabsJson = prefs.getString("tabs", "[]")
        if (tabsJson == null) {
            tabsJson = "[]"
        }
        
        var loadedTabs: List<Tab>
        try {
            loadedTabs = Json.decodeFromString<List<Tab>>(tabsJson)
        } catch (e: Exception) {
            loadedTabs = emptyList()
        }
        
        val savedCurrentId = prefs.getString("currentTabId", null)
        
        if (loadedTabs.isEmpty()) {
            // First time launch or corrupted data: start fresh
            val initialTab = Tab()
            _tabs.value = listOf(initialTab)
            _currentTabId.value = initialTab.id
        } else {
            // Find the active tab by ID, or fallback to the first tab
            var activeTab: Tab? = null
            for (tab in loadedTabs) {
                if (tab.id == savedCurrentId) {
                    activeTab = tab
                    break
                }
            }
            if (activeTab == null && loadedTabs.isNotEmpty()) {
                activeTab = loadedTabs[0]
            }
            
            // If the last active tab was not blank, push a new blank tab for the cold start
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
                } else {
                    _currentTabId.value = null
                }
            }
        }
    }
    
    /**
     * Persists the current state to SharedPreferences.
     */
    private fun saveTabs() {
        val currentTabs = _tabs.value
        val currentId = _currentTabId.value
        
        // Offload JSON serialization to IO thread to prevent UI micro-stutters
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
     * Creates a new blank tab and sets it as the active tab.
     */
    fun createNewTab() {
        val newTab = Tab()
        _tabs.value = _tabs.value + newTab
        _currentTabId.value = newTab.id
        saveTabs()
    }
    
    /**
     * Closes the tab with the specified ID. Prevents pinned tabs from being closed,
     * and ensures at least one blank tab is always available.
     */
    fun closeTab(tabId: String) {
        val currentTabs = _tabs.value
        val tabToClose = currentTabs.find { it.id == tabId }
        
        // Pinned tabs cannot be closed directly
        if (tabToClose?.isPinned == true) return 
        
        val remainingTabs = currentTabs.filter { it.id != tabId }
        
        if (remainingTabs.isEmpty()) {
            val fallbackTab = Tab()
            _tabs.value = listOf(fallbackTab)
            _currentTabId.value = fallbackTab.id
        } else {
            _tabs.value = remainingTabs
            // Shift focus if the active tab was closed
            if (_currentTabId.value == tabId) {
                _currentTabId.value = remainingTabs.last().id
            }
        }
        saveTabs()
    }
    
    /**
     * Switches the active tab to the specified ID and updates its last accessed timestamp.
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
     * Reorders tabs by moving a tab from one index to another.
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
     * Parses the user's input. If it resembles a URL, it formats it correctly.
     * Otherwise, it treats it as a Google search query.
     */
    fun handleSearchOrUrl(tabId: String, query: String) {
        val trimmedInput = query.trim()
        if (trimmedInput.isEmpty()) return
        
        // Checks for a dot and no spaces, or starts with http/https
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
     * Opens a URL in a new tab. If the current active tab is completely blank,
     * it reuses that tab instead to prevent empty tab accumulation.
     */
    fun openUrlInNewTab(url: String) {
        val currentId = _currentTabId.value
        val currentTab = _tabs.value.find { it.id == currentId }
        
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

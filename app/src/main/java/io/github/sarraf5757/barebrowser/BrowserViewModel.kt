package io.github.sarraf5757.barebrowser

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.util.UUID

// Singleton instance of DataStore
private val Context.dataStore by preferencesDataStore(name = "bare_browser_prefs")

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
 * ViewModel managing the state of the browser: the list of tabs, the active tab, and persistence
 */
class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = application.dataStore
    // Keys used to identify specific pieces of data in the file
    private val tabsKey = stringPreferencesKey("tabs")
    private val currentTabIdKey = stringPreferencesKey("currentTabId")

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _currentTabId = MutableStateFlow<String?>(null)
    val currentTabId: StateFlow<String?> = _currentTabId.asStateFlow()
    
    init {
        loadTabs()  // Initialize state from persistent storage upon creation
    }
    
    /**
     * Load tabs from DataStore
     */
    private fun loadTabs() {
        // Launch a coroutine to read from disk without blocking the UI
        viewModelScope.launch {
            // Read the current snapshot of data
            val prefs = dataStore.data.first()
            val tabsJson = prefs[tabsKey] ?: "[]"
            val loadedTabs = Json.decodeFromString<List<Tab>>(tabsJson)
            val savedCurrentId = prefs[currentTabIdKey]
            
            // If no tabs were found, create an initial blank tab
            if (loadedTabs.isEmpty()) {
                val initialTab = Tab()
                _tabs.value = listOf(initialTab)
                _currentTabId.value = initialTab.id
            } else {
                // Restore the saved list and the active tab ID
                _tabs.value = loadedTabs
                _currentTabId.value = savedCurrentId ?: loadedTabs.last().id    // default to the last tab if the saved ID is missing/invalid
            }
        }
    }
    
    /**
     * Helper - Persist the current state to DataStore
     */
    private fun saveTabs() {
        val currentTabs = _tabs.value
        val currentId = _currentTabId.value
        
        // Use the IO thread to write to the file to keep the UI smooth
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[tabsKey] = Json.encodeToString(currentTabs)  // serialize the list of objects into a JSON string
                // Save the current tab ID
                if (currentId != null) {
                    prefs[currentTabIdKey] = currentId
                }
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
            // Always keep at least one blank tab open
            val fallbackTab = Tab()
            _tabs.value = listOf(fallbackTab)
            _currentTabId.value = fallbackTab.id
        } else {
            _tabs.value = remainingTabs
            // If we closed the active tab, move focus to the new last tab
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
            // Create a new list with the updated timestamp to trigger UI refresh
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
        val input = query.trim()
        if (input.isEmpty()) return
        
        // Simple regex check: does it look like a domain name?
        val isUrl = input.matches(Regex("^(https?://)?[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$"))
        
        val finalUrl = if (isUrl) {
            // Add https prefix if the user didn't type it
            if (!input.startsWith("http")) "https://$input" else input
        } else {
            // Otherwise, perform a Google search
            "https://www.google.com/search?q=${URLEncoder.encode(input, "UTF-8")}"
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
            // Reuse the existing blank tab
            updateTabUrl(currentTab.id, url)
        } else {
            // Open in a completely new tab
            val newTab = Tab(url = url)
            _tabs.value += newTab
            _currentTabId.value = newTab.id
            saveTabs()
        }
    }
}

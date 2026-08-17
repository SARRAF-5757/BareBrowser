package com.example.barebrowser

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.net.URLEncoder

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
        
        val currId = prefs.getString("currentTabId", null)
        
        if (loadedTabs.isEmpty()) {
            val initialTab = Tab()
            _tabs.value = listOf(initialTab)
            _currentTabId.value = initialTab.id
        } else {
            _tabs.value = loadedTabs
            _currentTabId.value = currId ?: loadedTabs.firstOrNull()?.id
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
        if (tabToClose?.isPinned == true) return // Cannot close pinned tabs
        
        val newTabs = currentTabs.filter { it.id != tabId }
        
        if (newTabs.isEmpty()) {
            val fallbackTab = Tab()
            _tabs.value = listOf(fallbackTab)
            _currentTabId.value = fallbackTab.id
        } else {
            _tabs.value = newTabs
            if (_currentTabId.value == tabId) {
                _currentTabId.value = newTabs.last().id
            }
        }
        saveTabs()
    }
    
    fun selectTab(tabId: String) {
        val currentTabs = _tabs.value
        val tab = currentTabs.find { it.id == tabId }
        if (tab != null) {
            val updatedTabs = currentTabs.map {
                if (it.id == tabId) it.copy(lastAccessed = System.currentTimeMillis())
                else it
            }
            _tabs.value = updatedTabs
            _currentTabId.value = tabId
            saveTabs()
        }
    }
    
    fun togglePin(tabId: String) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(isPinned = !it.isPinned)
            else it
        }
        saveTabs()
    }
    
    fun updateTabUrl(tabId: String, newUrl: String) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(url = newUrl)
            else it
        }
        saveTabs()
    }
    
    fun handleSearchOrUrl(tabId: String, query: String) {
        val trimmed = query.trim()
        val url = if (trimmed.matches(Regex("^(https?://)?[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$"))) {
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                "https://$trimmed"
            } else {
                trimmed
            }
        } else {
            "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
        }
        updateTabUrl(tabId, url)
    }
}

package com.barebrowser.app.ui

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.barebrowser.app.data.AppDatabase
import com.barebrowser.app.data.Tab
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val tabDao = db.tabDao()
    val webViewManager = WebViewManager(application)

    private val _tabs = tabDao.getAllTabs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs

    private val _activeTabId = MutableStateFlow<Long?>(null)
    val activeTabId: StateFlow<Long?> = _activeTabId

    private val _isTabGridVisible = MutableStateFlow(false)
    val isTabGridVisible: StateFlow<Boolean> = _isTabGridVisible

    init {
        viewModelScope.launch {
            _tabs.collect { tabs ->
                if (_activeTabId.value == null && tabs.isNotEmpty()) {
                    _activeTabId.value = tabs.first().id
                }
            }
        }
    }

    fun setActiveTab(id: Long) {
        _activeTabId.value = id
        _isTabGridVisible.value = false
        updateTabLastAccessed(id)
    }

    fun addTab(url: String = "about:blank") {
        viewModelScope.launch {
            val newTab = Tab(url = formatUrl(url), title = "New Tab")
            val id = tabDao.insertTab(newTab)
            _activeTabId.value = id
            _isTabGridVisible.value = false
        }
    }

    fun closeTab(tab: Tab) {
        viewModelScope.launch {
            tabDao.deleteTab(tab)
            webViewManager.removeWebView(tab.id)
            if (_activeTabId.value == tab.id) {
                _activeTabId.value = _tabs.value.firstOrNull { it.id != tab.id }?.id
            }
        }
    }

    fun togglePin(tab: Tab) {
        viewModelScope.launch {
            tabDao.updateTab(tab.copy(isPinned = !tab.isPinned))
        }
    }

    fun toggleTabGrid() {
        _isTabGridVisible.value = !_isTabGridVisible.value
    }

    fun navigate(url: String) {
        val formattedUrl = formatUrl(url)
        val currentId = _activeTabId.value ?: return
        
        viewModelScope.launch {
            val currentTab = _tabs.value.find { it.id == currentId } ?: return@launch
            tabDao.updateTab(currentTab.copy(url = formattedUrl))
            // WebView loading is handled by the composable observing the URL or direct command
        }
    }

    private fun updateTabLastAccessed(id: Long) {
        viewModelScope.launch {
            val tab = _tabs.value.find { it.id == id } ?: return@launch
            tabDao.updateTab(tab.copy(lastAccessed = System.currentTimeMillis()))
        }
    }

    private fun formatUrl(input: String): String {
        if (input.isBlank()) return "about:blank"
        return if (Patterns.WEB_URL.matcher(input).matches()) {
            if (input.startsWith("http://") || input.startsWith("https://")) input
            else "https://$input"
        } else {
            "https://www.google.com/search?q=$input"
        }
    }

    override fun onCleared() {
        super.onCleared()
        webViewManager.clear()
    }
}

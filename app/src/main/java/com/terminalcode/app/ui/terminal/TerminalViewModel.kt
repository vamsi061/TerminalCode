package com.terminalcode.app.ui.terminal

import android.app.Application
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.terminalcode.app.terminal.TerminalSession
import com.terminalcode.app.terminal.TerminalWebViewBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for managing terminal sessions.
 *
 * Handles creating multiple terminal tabs, starting/stopping sessions,
 * and routing output to the appropriate WebView.
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    data class TerminalTab(
        val id: String = UUID.randomUUID().toString(),
        val title: String = "Terminal",
        val session: TerminalSession,
        val bridge: TerminalWebViewBridge? = null,
        var webView: WebView? = null
    )

    private val _tabs = MutableStateFlow<List<TerminalTab>>(emptyList())
    val tabs: StateFlow<List<TerminalTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    val activeTab: StateFlow<TerminalTab?> = combine(
        _tabs, _activeTabId
    ) { tabs, activeId ->
        tabs.find { it.id == activeId }
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    init {
        // Create initial terminal tab
        createNewTab()
    }

    /**
     * Creates a new terminal session tab with its own shell process.
     */
    fun createNewTab(): TerminalTab {
        val session = TerminalSession()
        val bridge = TerminalWebViewBridge(session)
        val tab = TerminalTab(
            session = session,
            bridge = bridge
        )

        _tabs.value = _tabs.value + tab
        _activeTabId.value = tab.id

        // Start the session and bridge output to WebView
        viewModelScope.launch {
            session.start()

            // Observe output and push to WebView
            session.output.collect { output ->
                val currentTab = _tabs.value.find { it.id == tab.id } ?: return@collect
                currentTab.webView?.let { webView ->
                    if (output.isNotEmpty()) {
                        webView.post {
                            webView.evaluateJavascript(
                                bridge.getBatchScript(output),
                                null
                            )
                        }
                    }
                }
            }
        }

        return tab
    }

    /**
     * Switches the active terminal tab.
     */
    fun switchTab(tabId: String) {
        _activeTabId.value = tabId
    }

    /**
     * Closes a terminal tab and its session.
     */
    fun closeTab(tabId: String) {
        val tab = _tabs.value.find { it.id == tabId } ?: return
        tab.session.stop()
        tab.webView?.destroy()

        _tabs.value = _tabs.value.filter { it.id != tabId }

        // Switch to another tab if available
        if (_activeTabId.value == tabId) {
            _activeTabId.value = _tabs.value.lastOrNull()?.id
        }

        // Create new tab if all closed
        if (_tabs.value.isEmpty()) {
            createNewTab()
        }
    }

    /**
     * Registers a WebView with a tab for output rendering.
     */
    fun registerWebView(tabId: String, webView: WebView) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) {
                tab.copy(webView = webView)
            } else tab
        }
    }

    /**
     * Writes input to the active terminal session.
     */
    fun writeToActiveSession(input: String) {
        val tab = _tabs.value.find { it.id == _activeTabId.value } ?: return
        tab.session.writeInput(input)
    }

    /**
     * Stops all terminal sessions and cleans up.
     */
    override fun onCleared() {
        super.onCleared()
        _tabs.value.forEach { tab ->
            tab.session.stop()
            tab.webView?.destroy()
        }
    }
}

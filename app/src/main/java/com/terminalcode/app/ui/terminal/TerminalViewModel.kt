package com.terminalcode.app.ui.terminal

import android.app.Application
import android.util.Log
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
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "TerminalViewModel"
    }

    data class TerminalTab(
        val id: String = UUID.randomUUID().toString(),
        val title: String = "Terminal",
        val session: TerminalSession,
        val bridge: TerminalWebViewBridge? = null,
        var webView: WebView? = null,
        var lastSentLength: Int = 0
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
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    init {
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

        // Start session and route output to WebView
        viewModelScope.launch {
            session.start()

            // Observe output - send only NEW data each time
            session.output.collect { fullOutput ->
                val currentTab = _tabs.value.find { it.id == tab.id } ?: return@collect
                val wv = currentTab.webView
                val sent = currentTab.lastSentLength

                // Get only the new data since last send
                if (fullOutput.length > sent) {
                    val newData = fullOutput.substring(sent)

                    if (newData.isNotEmpty()) {
                        if (wv != null) {
                            // Only advance when actually sent to WebView
                            _tabs.value = _tabs.value.map {
                                if (it.id == tab.id) it.copy(lastSentLength = fullOutput.length)
                                else it
                            }
                            wv.post {
                                wv.evaluateJavascript(
                                    bridge.getBatchScript(newData),
                                    null
                                )
                            }
                        }
                        // If wv is null, lastSentLength stays unchanged
                        // Data will be re-sent on next collect when WebView registers
                    }
                }
            }
        }

        return tab
    }

    /**
     * Registers a WebView with a tab. Flushes any buffered output
     * that arrived before the WebView was ready.
     */
    fun registerWebView(tabId: String, webView: WebView) {
        val oldTab = _tabs.value.find { it.id == tabId } ?: return
        val totalOutput = oldTab.session.output.value
        val alreadySent = oldTab.lastSentLength
        val unsentData = if (totalOutput.length > alreadySent)
            totalOutput.substring(alreadySent) else ""

        // Single atomic update: set WebView + advance lastSentLength
        _tabs.value = _tabs.value.map { t ->
            if (t.id == tabId) t.copy(
                webView = webView,
                lastSentLength = totalOutput.length
            ) else t
        }

        if (unsentData.isNotEmpty()) {
            webView.post {
                webView.evaluateJavascript(
                    oldTab.bridge?.getBatchScript(unsentData) ?: "",
                    null
                )
            }
        }
    }

    fun switchTab(tabId: String) {
        _activeTabId.value = tabId
    }

    fun closeTab(tabId: String) {
        val tab = _tabs.value.find { it.id == tabId } ?: return
        tab.session.stop()
        tab.webView?.destroy()

        _tabs.value = _tabs.value.filter { it.id != tabId }

        if (_activeTabId.value == tabId) {
            _activeTabId.value = _tabs.value.lastOrNull()?.id
        }

        if (_tabs.value.isEmpty()) {
            createNewTab()
        }
    }

    fun writeToActiveSession(input: String) {
        val tab = _tabs.value.find { it.id == _activeTabId.value } ?: return
        tab.session.writeInput(input)
    }

    override fun onCleared() {
        super.onCleared()
        _tabs.value.forEach { tab ->
            tab.session.stop()
            tab.webView?.destroy()
        }
    }
}

package com.terminalcode.app.terminal

import android.util.Log
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JavaScript interface bridge between xterm.js running in the WebView
 * and the Android terminal session.
 *
 * This class is injected into the WebView via addJavascriptInterface()
 * and provides methods that the xterm.js JavaScript code can call.
 */
class TerminalWebViewBridge(
    private val session: TerminalSession
) {
    companion object {
        const val INTERFACE_NAME = "TerminalBridge"
        private const val TAG = "TerminalWebViewBridge"
    }

    private val _terminalReady = MutableStateFlow(false)
    val terminalReady: StateFlow<Boolean> = _terminalReady.asStateFlow()

    private val _terminalSize = MutableStateFlow(Pair(80, 24))
    val terminalSize: StateFlow<Pair<Int, Int>> = _terminalSize.asStateFlow()

    private val _title = MutableStateFlow("Terminal")
    val title: StateFlow<String> = _title.asStateFlow()

    /**
     * Called from JavaScript when the terminal is ready.
     * Provides initial terminal dimensions.
     */
    @JavascriptInterface
    fun onReady(cols: Int, rows: Int) {
        _terminalReady.value = true
        _terminalSize.value = Pair(cols, rows)
        session.resize(cols, rows)
        Log.d(TAG, "Terminal ready: ${cols}x$rows")
    }

    /**
     * Called from JavaScript when the user types in the terminal.
     * Forwards the keystrokes to the terminal session.
     */
    @JavascriptInterface
    fun onData(data: String) {
        session.writeInput(data)
    }

    /**
     * Called from JavaScript when the terminal is resized.
     * Updates the PTY window size.
     */
    @JavascriptInterface
    fun onResize(cols: Int, rows: Int) {
        _terminalSize.value = Pair(cols, rows)
        session.resize(cols, rows)
    }

    /**
     * Called from JavaScript when the terminal title changes
     * (e.g., via escape sequences like ESC]0;titleBEL)
     */
    @JavascriptInterface
    fun onTitle(title: String) {
        _title.value = title
    }

    /**
     * Gets JavaScript code to write data to the xterm.js terminal.
     */
    fun getWriteScript(data: String): String {
        val escaped = data
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "writeToTerminal('$escaped');"
    }

    /**
     * Gets JavaScript code to write a batch of data.
     * More efficient for large outputs.
     */
    fun getBatchScript(data: String): String {
        val escaped = data
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "writeBatch('$escaped');"
    }

    /**
     * Gets JavaScript code to clear the terminal.
     */
    fun getClearScript(): String {
        return "clearTerminal();"
    }

    /**
     * Gets JavaScript code to resize the terminal to fit its container.
     */
    fun getFitScript(): String {
        return "fitTerminal();"
    }

    /**
     * Gets JavaScript code to focus the terminal.
     */
    fun getFocusScript(): String {
        return "focusTerminal();"
    }
}

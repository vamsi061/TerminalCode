package com.terminalcode.app.editor

import android.util.Log
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JavaScript interface bridge between Monaco Editor running in the WebView
 * and the Android app.
 *
 * This class handles communication between the native Android file system
 * and the Monaco Editor web UI for code editing.
 */
class MonacoEditorBridge {

    companion object {
        const val INTERFACE_NAME = "AndroidBridge"
        private const val TAG = "MonacoEditorBridge"
    }

    private val _editorReady = MutableStateFlow(false)
    val editorReady: StateFlow<Boolean> = _editorReady.asStateFlow()

    private val _currentFile = MutableStateFlow<FileInfo?>(null)
    val currentFile: StateFlow<FileInfo?> = _currentFile.asStateFlow()

    private val _isModified = MutableStateFlow(false)
    val isModified: StateFlow<Boolean> = _isModified.asStateFlow()

    private val _cursorPosition = MutableStateFlow(Pair(1, 1))
    val cursorPosition: StateFlow<Pair<Int, Int>> = _cursorPosition.asStateFlow()

    data class FileInfo(
        val path: String,
        val name: String,
        val language: String
    )

    /**
     * Called from JavaScript when the Monaco Editor is initialized and ready.
     */
    @JavascriptInterface
    fun onReady() {
        _editorReady.value = true
        Log.d(TAG, "Monaco Editor ready")
    }

    /**
     * Called from JavaScript when a file is opened in the editor.
     */
    @JavascriptInterface
    fun onFileOpened(path: String, name: String, language: String) {
        _currentFile.value = FileInfo(path, name, language)
        _isModified.value = false
        Log.d(TAG, "File opened: $name ($language)")
    }

    /**
     * Called from JavaScript when the file content is modified.
     */
    @JavascriptInterface
    fun onModified(modified: Boolean) {
        _isModified.value = modified
    }

    /**
     * Called from JavaScript when the cursor position changes.
     */
    @JavascriptInterface
    fun onCursorPosition(line: Int, column: Int) {
        _cursorPosition.value = Pair(line, column)
    }

    /**
     * Returns JavaScript code to open a file in the editor.
     */
    fun getOpenFileScript(filePath: String, content: String): String {
        val escapedContent = content
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "openFile('$filePath', '$escapedContent');"
    }

    /**
     * Returns JavaScript code to get the current editor content.
     */
    fun getGetContentScript(): String {
        return "getContent();"
    }

    /**
     * Returns JavaScript code to set editor content.
     */
    fun getSetContentScript(content: String): String {
        val escapedContent = content
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "setContent('$escapedContent');"
    }

    /**
     * Returns JavaScript code to mark the file as saved.
     */
    fun getMarkSavedScript(): String {
        return "markAsSaved();"
    }

    /**
     * Returns JavaScript code to set the editor theme.
     */
    fun getSetThemeScript(isDark: Boolean): String {
        return "setTheme('${if (isDark) "dark" else "light"}');"
    }

    /**
     * Returns JavaScript code to toggle word wrap.
     */
    fun getToggleWordWrapScript(): String {
        return "toggleWordWrap();"
    }

    /**
     * Returns JavaScript code to format the document.
     */
    fun getFormatScript(): String {
        return "formatDocument();"
    }

    /**
     * Returns JavaScript code to undo.
     */
    fun getUndoScript(): String {
        return "undo();"
    }

    /**
     * Returns JavaScript code to redo.
     */
    fun getRedoScript(): String {
        return "redo();"
    }
}

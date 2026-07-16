package com.terminalcode.app.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.terminalcode.app.editor.MonacoEditorBridge
import com.terminalcode.app.files.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for managing code editor state, tabs, and file operations.
 *
 * Handles opening, editing, and saving files across multiple editor tabs.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    data class EditorTab(
        val id: String = UUID.randomUUID().toString(),
        val filePath: String = "",
        val fileName: String = "Untitled",
        val language: String = "plaintext",
        val uri: Uri? = null,
        val content: String = "",
        val isModified: Boolean = false,
        val isLoading: Boolean = false
    )

    private val fileRepo = FileRepository(application)

    private val _tabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val tabs: StateFlow<List<EditorTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    val activeTab: StateFlow<EditorTab?>
        get() = MutableStateFlow(
            _tabs.value.find { it.id == _activeTabId.value }
        ).asStateFlow()

    private val _editorBridge = MutableStateFlow(MonacoEditorBridge())
    val editorBridge: StateFlow<MonacoEditorBridge> = _editorBridge.asStateFlow()

    private val _showNewFileDialog = MutableStateFlow(false)
    val showNewFileDialog: StateFlow<Boolean> = _showNewFileDialog.asStateFlow()

    init {
        // Create an initial untitled tab
        createNewTab()
    }

    /**
     * Creates a new empty editor tab.
     */
    fun createNewTab(): EditorTab {
        val tab = EditorTab()
        _tabs.value = _tabs.value + tab
        _activeTabId.value = tab.id
        return tab
    }

    /**
     * Opens a file in a new editor tab, reading its content from disk.
     */
    fun openFile(uri: Uri, fileName: String, directoryUri: Uri? = null) {
        viewModelScope.launch {
            val tab = EditorTab(
                filePath = buildString {
                    directoryUri?.let { append(it.lastPathSegment).append("/") }
                    append(fileName)
                },
                fileName = fileName,
                language = fileRepo.getMimeType(fileName),
                uri = uri,
                isLoading = true
            )

            _tabs.value = _tabs.value + tab
            _activeTabId.value = tab.id

            val result = fileRepo.readFile(uri)
            result.onSuccess { content ->
                _tabs.value = _tabs.value.map {
                    if (it.id == tab.id) it.copy(content = content, isLoading = false)
                    else it
                }
            }.onFailure { error ->
                _tabs.value = _tabs.value.map {
                    if (it.id == tab.id) it.copy(
                        content = "// Error loading file: ${error.message}",
                        isLoading = false
                    )
                    else it
                }
            }
        }
    }

    /**
     * Saves the content of the active editor tab to its file.
     */
    fun saveActiveFile(content: String) {
        val tab = _tabs.value.find { it.id == _activeTabId.value } ?: return
        val uri = tab.uri ?: return

        viewModelScope.launch {
            val result = fileRepo.writeFile(uri, content)
            result.onSuccess {
                _tabs.value = _tabs.value.map {
                    if (it.id == tab.id) it.copy(isModified = false)
                    else it
                }
            }
        }
    }

    /**
     * Creates a new file in the specified directory.
     */
    fun createNewFile(directoryUri: Uri, fileName: String) {
        viewModelScope.launch {
            val result = fileRepo.createFile(directoryUri, fileName)
            result.onSuccess { fileItem ->
                openFile(fileItem.uri, fileItem.name, directoryUri)
            }
        }
    }

    /**
     * Switches the active editor tab.
     */
    fun switchTab(tabId: String) {
        _activeTabId.value = tabId
    }

    /**
     * Closes an editor tab.
     */
    fun closeTab(tabId: String) {
        _tabs.value = _tabs.value.filter { it.id != tabId }

        if (_activeTabId.value == tabId) {
            _activeTabId.value = _tabs.value.lastOrNull()?.id
        }

        if (_tabs.value.isEmpty()) {
            createNewTab()
        }
    }

    /**
     * Updates the content of a tab (called from WebView bridge).
     */
    fun updateTabContent(tabId: String, content: String) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(content = content, isModified = true)
            else it
        }
    }

    fun showNewFileDialog() {
        _showNewFileDialog.value = true
    }

    fun hideNewFileDialog() {
        _showNewFileDialog.value = false
    }
}

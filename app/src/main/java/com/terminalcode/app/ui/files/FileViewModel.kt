package com.terminalcode.app.ui.files

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.terminalcode.app.files.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Stack

/**
 * ViewModel for the file browser.
 *
 * Manages directory navigation, file listing, and CRUD operations
 * using Android's Storage Access Framework.
 */
class FileViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepo = FileRepository(application)

    data class FileBrowserState(
        val currentDirectoryUri: Uri? = null,
        val currentPath: String = "Root",
        val files: List<FileRepository.FileItem> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val hasDirectoryAccess: Boolean = false
    )

    private val _browserState = MutableStateFlow(FileBrowserState())
    val browserState: StateFlow<FileBrowserState> = _browserState.asStateFlow()

    private val directoryStack = Stack<Pair<Uri, String>>()

    /**
     * Sets the root directory for the file browser.
     * Called when the user picks a directory via SAF.
     */
    fun setRootDirectory(uri: Uri) {
        directoryStack.clear()
        _browserState.value = _browserState.value.copy(
            hasDirectoryAccess = true
        )
        navigateToDirectory(uri)
    }

    /**
     * Navigates into a subdirectory.
     */
    fun navigateToDirectory(uri: Uri, path: String? = null) {
        viewModelScope.launch {
            _browserState.value = _browserState.value.copy(isLoading = true, error = null)

            // Save current to stack if we're navigating deeper
            val currentUri = _browserState.value.currentDirectoryUri
            if (currentUri != null && currentUri != uri) {
                directoryStack.push(Pair(currentUri, _browserState.value.currentPath))
            }

            val result = fileRepo.listFiles(uri)
            result.onSuccess { files ->
                _browserState.value = FileBrowserState(
                    currentDirectoryUri = uri,
                    currentPath = path ?: uri.lastPathSegment ?: "Directory",
                    files = files,
                    isLoading = false,
                    error = null,
                    hasDirectoryAccess = true
                )
            }.onFailure { error ->
                _browserState.value = _browserState.value.copy(
                    isLoading = false,
                    error = error.message
                )
            }
        }
    }

    /**
     * Navigates back to the previous directory.
     */
    fun navigateBack(): Boolean {
        if (directoryStack.isEmpty()) return false

        val (uri, path) = directoryStack.pop()
        _browserState.value = _browserState.value.copy(
            currentDirectoryUri = uri,
            currentPath = path
        )

        // Reload the directory
        viewModelScope.launch {
            _browserState.value = _browserState.value.copy(isLoading = true)
            val result = fileRepo.listFiles(uri)
            result.onSuccess { files ->
                _browserState.value = _browserState.value.copy(
                    files = files,
                    isLoading = false
                )
            }.onFailure { error ->
                _browserState.value = _browserState.value.copy(
                    isLoading = false,
                    error = error.message
                )
            }
        }
        return true
    }

    /**
     * Creates a new file in the current directory.
     */
    fun createFile(fileName: String) {
        val currentUri = _browserState.value.currentDirectoryUri ?: return

        viewModelScope.launch {
            val result = fileRepo.createFile(currentUri, fileName)
            result.onSuccess {
                refreshCurrentDirectory()
            }.onFailure { error ->
                _browserState.value = _browserState.value.copy(error = error.message)
            }
        }
    }

    /**
     * Creates a new directory in the current directory.
     */
    fun createDirectory(dirName: String) {
        val currentUri = _browserState.value.currentDirectoryUri ?: return

        viewModelScope.launch {
            val result = fileRepo.createDirectory(currentUri, dirName)
            result.onSuccess {
                refreshCurrentDirectory()
            }.onFailure { error ->
                _browserState.value = _browserState.value.copy(error = error.message)
            }
        }
    }

    /**
     * Deletes a file or directory.
     */
    fun deleteFile(uri: Uri) {
        viewModelScope.launch {
            val result = fileRepo.deleteFile(uri)
            result.onSuccess {
                refreshCurrentDirectory()
            }.onFailure { error ->
                _browserState.value = _browserState.value.copy(error = error.message)
            }
        }
    }

    /**
     * Refreshes the current directory listing.
     */
    fun refreshCurrentDirectory() {
        val currentUri = _browserState.value.currentDirectoryUri ?: return
        val currentPath = _browserState.value.currentPath

        viewModelScope.launch {
            _browserState.value = _browserState.value.copy(isLoading = true)
            val result = fileRepo.listFiles(currentUri)
            result.onSuccess { files ->
                _browserState.value = _browserState.value.copy(
                    files = files,
                    isLoading = false
                )
            }.onFailure { error ->
                _browserState.value = _browserState.value.copy(
                    isLoading = false,
                    error = error.message
                )
            }
        }
    }

    /**
     * Gets the MIME type for a file.
     */
    fun getMimeType(fileName: String): String {
        return fileRepo.getMimeType(fileName)
    }

    /**
     * Checks if a file is viewable in the editor.
     */
    fun isTextFile(fileName: String): Boolean {
        return fileRepo.isTextFile(fileName)
    }

    /**
     * Clears any error message.
     */
    fun clearError() {
        _browserState.value = _browserState.value.copy(error = null)
    }
}

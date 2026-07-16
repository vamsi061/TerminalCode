package com.terminalcode.app.ui.terminal

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * ViewModel that manages terminal sessions using Termux's TerminalSession.
 *
 * Uses the battle-tested Termux terminal-emulator library with JNI-based PTY,
 * providing a real TTY for the shell (proper prompt, job control, etc.).
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "TerminalViewModel"
    }

    data class TerminalTab(
        val id: String = UUID.randomUUID().toString(),
        val title: String = "Terminal",
        val session: TerminalSession? = null,
        val isRunning: Boolean = false
    )

    private val _tabs = MutableStateFlow<List<TerminalTab>>(emptyList())
    val tabs: StateFlow<List<TerminalTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    init {
        try {
            createNewTab()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create initial terminal session", e)
        }
    }

    /**
     * Finds the best available shell on the device.
     */
    private fun findShell(): String {
        val shellPaths = listOfNotNull(
            System.getenv("SHELL"),
            "/data/data/com.termux/files/usr/bin/bash",
            "/bin/bash", "/usr/bin/bash",
            "/system/bin/sh"
        )
        for (path in shellPaths) {
            if (path.isNotEmpty() && java.io.File(path).exists() && java.io.File(path).canExecute()) {
                Log.d(TAG, "Using shell: $path")
                return path
            }
        }
        return "/system/bin/sh"
    }

    /**
     * Creates environment variables for the terminal session.
     */
    private fun createEnvironment(shell: String): Array<String> {
        val path = listOfNotNull(
            System.getenv("PATH"),
            "/usr/local/sbin:/usr/local/bin",
            "/usr/sbin:/usr/bin",
            "/sbin:/bin",
            "/data/data/com.termux/files/usr/bin",
            "/system/bin", "/system/xbin"
        ).joinToString(":")

        val user = System.getenv("USER") ?: "shell"
        val home = System.getenv("HOME") ?: "/data/data/com.terminalcode.app/files/home"

        return arrayOf(
            "TERM=xterm-256color",
            "HOME=$home",
            "SHELL=$shell",
            "USER=$user",
            "LOGNAME=$user",
            "LANG=en_US.UTF-8",
            "PATH=$path"
        )
    }

    /**
     * Creates a new terminal tab with a real PTY-backed shell session.
     */
    fun createNewTab(): TerminalTab {
        try {
            val shell = findShell()
            val env = createEnvironment(shell)
            val args = arrayOf(shell)

            val session = TerminalSession(
                shell,
                null,
                args,
                env,
                null,
                object : TerminalSessionClient {
                    override fun onTextChanged(changedSession: TerminalSession?) {
                        // TerminalView handles rendering
                    }

                    override fun onTitleChanged(changedSession: TerminalSession?) {
                        val title = changedSession?.getTitle() ?: "Terminal"
                        _tabs.value = _tabs.value.map { tab ->
                            if (tab.session == changedSession) tab.copy(title = title)
                            else tab
                        }
                    }

                    override fun onSessionFinished(finishedSession: TerminalSession?) {
                        Log.d(TAG, "Session finished")
                        _tabs.value = _tabs.value.map { tab ->
                            if (tab.session == finishedSession) tab.copy(isRunning = false)
                            else tab
                        }
                    }

                    override fun onCopyTextToClipboard(session: TerminalSession?, text: String?) { }
                    override fun onPasteTextFromClipboard(session: TerminalSession?) { }
                    override fun onBell(session: TerminalSession?) { }
                    override fun onColorsChanged(session: TerminalSession?) { }
                    override fun onTerminalCursorStateChange(state: Boolean) { }
                    override fun getTerminalCursorStyle(): Int? = null

                    override fun logInfo(tag: String?, message: String?) {
                        Log.d(tag ?: TAG, message ?: "")
                    }
                    override fun logWarn(tag: String?, message: String?) {
                        Log.w(tag ?: TAG, message ?: "")
                    }
                    override fun logDebug(tag: String?, message: String?) {
                        Log.d(tag ?: TAG, message ?: "")
                    }
                    override fun logError(tag: String?, message: String?) {
                        Log.e(tag ?: TAG, message ?: "")
                    }
                    override fun logVerbose(tag: String?, message: String?) { }
                    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
                        Log.e(tag ?: TAG, "$message", e)
                    }
                    override fun logStackTrace(tag: String?, e: Exception?) {
                        Log.e(tag ?: TAG, "Stack trace", e)
                    }
                }
            )

            val tab = TerminalTab(
                session = session,
                isRunning = true
            )

            _tabs.value = _tabs.value + tab
            _activeTabId.value = tab.id
            return tab

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create terminal session", e)
            // Return a placeholder tab so the UI doesn't crash
            val fallback = TerminalTab(title = "Error", isRunning = false)
            _tabs.value = _tabs.value + fallback
            _activeTabId.value = fallback.id
            return fallback
        }
    }

    fun switchTab(tabId: String) {
        _activeTabId.value = tabId
    }

    fun closeTab(tabId: String) {
        val tab = _tabs.value.find { it.id == tabId } ?: return
        tab.session?.finishIfRunning()

        _tabs.value = _tabs.value.filter { it.id != tabId }

        if (_activeTabId.value == tabId) {
            _activeTabId.value = _tabs.value.lastOrNull()?.id
        }

        if (_tabs.value.isEmpty()) {
            createNewTab()
        }
    }

    fun sendCtrlC() {
        val tab = _tabs.value.find { it.id == _activeTabId.value } ?: return
        tab.session?.writeCodePoint(false, 3) // Ctrl+C = 0x03
    }

    fun sendCtrlD() {
        val tab = _tabs.value.find { it.id == _activeTabId.value } ?: return
        tab.session?.writeCodePoint(false, 4) // Ctrl+D = 0x04
    }

    fun sendTab() {
        val tab = _tabs.value.find { it.id == _activeTabId.value } ?: return
        tab.session?.writeCodePoint(false, 9) // Tab = 0x09
    }

    override fun onCleared() {
        super.onCleared()
        _tabs.value.forEach { tab ->
            tab.session?.finishIfRunning()
        }
    }
}

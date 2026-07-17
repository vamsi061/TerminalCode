package com.terminalcode.app.ui.terminal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

/**
 * ViewModel that manages terminal sessions using pure ProcessBuilder (PIPE approach).
 *
 * This has ZERO JNI dependencies - no native libraries are loaded.
 * The shell runs as a subprocess with stdin/stdout connected via pipes.
 * All terminal emulation is done in pure Kotlin.
 */
class TerminalViewModel : ViewModel() {

    companion object {
        private const val TAG = "TerminalViewModel"
        private val ESC = 27.toChar()
    }

    data class TerminalTab(
        val id: String = UUID.randomUUID().toString(),
        val title: String = "Terminal",
        val process: Process? = null,
        val isRunning: Boolean = false,
        val output: String = "",
        val outputLines: List<String> = emptyList()
    )

    private val _tabs = MutableStateFlow<List<TerminalTab>>(emptyList())
    val tabs: StateFlow<List<TerminalTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    private val inputQueues = ConcurrentHashMap<String, LinkedBlockingQueue<ByteArray>>()

    init {
        try {
            createNewTab()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create initial tab", e)
        }
    }

    private fun findShell(): String {
        val shellPaths = listOf(
            "/system/bin/sh", "/bin/sh",
            "/system/bin/bash", "/bin/bash"
        )
        for (path in shellPaths) {
            if (File(path).exists() && File(path).canExecute()) {
                Log.d(TAG, "Using shell: $path")
                return path
            }
        }
        return "/system/bin/sh"
    }

    private fun createEnvironment(shell: String): Array<String> {
        val esc = ESC
        return arrayOf(
            "TERM=xterm-256color",
            "HOME=/data/data/com.terminalcode.app/files",
            "SHELL=$shell",
            "USER=shell",
            "LOGNAME=shell",
            "LANG=en_US.UTF-8",
            "PATH=/system/bin:/system/xbin:/sbin:/bin:/usr/bin:/usr/sbin",
            "PS1=${esc}[1;32mTerminalCode${esc}[0m:${esc}[1;34m\\w${esc}[0m$ "
        )
    }

    fun createNewTab(): TerminalTab {
        try {
            val shell = findShell()
            val env = createEnvironment(shell)

            Log.d(TAG, "Starting shell: $shell")

            val pb = ProcessBuilder()
            pb.command(shell, "-i")
            pb.environment().putAll(
                env.associate {
                    val eqIdx = it.indexOf('=')
                    if (eqIdx > 0) it.substring(0, eqIdx) to it.substring(eqIdx + 1)
                    else it to ""
                }
            )
            pb.redirectErrorStream(true)

            val process = pb.start()
            val tab = TerminalTab(
                process = process,
                isRunning = true,
                output = "",
                outputLines = listOf("${ESC}[1;32mTerminalCode${ESC}[0m v1.0.0\nStarting shell...\n")
            )

            _tabs.value = _tabs.value + tab
            _activeTabId.value = tab.id
            startIoThreads(tab.id, process)

            Log.d(TAG, "Tab ${tab.id} created")
            return tab

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create terminal session", e)
            val fallback = TerminalTab(
                title = "Error",
                isRunning = false,
                output = "Failed to start terminal:\n${e.message ?: "Unknown error"}\n",
                outputLines = listOf("Failed to start terminal:\n${e.message ?: "Unknown error"}\n")
            )
            _tabs.value = _tabs.value + fallback
            _activeTabId.value = fallback.id
            return fallback
        }
    }

    private fun startIoThreads(tabId: String, process: Process) {
        val inputStream = process.inputStream
        val outputStream = process.outputStream
        val inputQueue = LinkedBlockingQueue<ByteArray>(100)
        inputQueues[tabId] = inputQueue

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(inputStream))
                val buffer = CharArray(4096)
                val textBuilder = StringBuilder()

                while (true) {
                    val bytesRead = reader.read(buffer, 0, buffer.size)
                    if (bytesRead == -1) break
                    textBuilder.append(buffer, 0, bytesRead)
                    val text = textBuilder.toString()
                    updateTabOutput(tabId, text)
                    if (textBuilder.length > 50000) {
                        textBuilder.delete(0, textBuilder.length - 30000)
                    }
                }
            } catch (e: IOException) {
                if (e.message?.contains("Stream closed") != true) {
                    Log.e(TAG, "Error reading output for tab $tabId", e)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in output thread for tab $tabId", e)
            } finally {
                updateTabRunning(tabId, false)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val writer = BufferedWriter(OutputStreamWriter(outputStream))
                while (true) {
                    val data = inputQueue.take()
                    if (data.isEmpty()) continue
                    if (data.size == 1 && data[0] == 0x00.toByte()) break
                    writer.write(String(data, Charsets.UTF_8))
                    writer.flush()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error writing input for tab $tabId", e)
            } catch (e: Throwable) {
                Log.e(TAG, "Error in input thread for tab $tabId", e)
            }
        }
    }

    private fun updateTabOutput(tabId: String, newText: String) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) {
                val combined = tab.output + newText
                val truncated = if (combined.length > 50000) combined.takeLast(50000) else combined
                val lines = truncated.split("\n").takeLast(500)
                tab.copy(output = truncated, outputLines = lines)
            } else tab
        }
    }

    private fun updateTabRunning(tabId: String, isRunning: Boolean) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) tab.copy(isRunning = isRunning) else tab
        }
    }

    fun writeInput(tabId: String?, text: String) {
        val activeTab = _tabs.value.find { it.id == (tabId ?: _activeTabId.value) } ?: return
        val queue = inputQueues[activeTab.id] ?: return
        if (!activeTab.isRunning) return
        try {
            queue.put(text.toByteArray(Charsets.UTF_8))
        } catch (e: Throwable) {
            Log.e(TAG, "Error queueing input", e)
        }
    }

    private fun sendControlCharacter(codePoint: Int) {
        val tab = _tabs.value.find { it.id == _activeTabId.value } ?: return
        val queue = inputQueues[tab.id] ?: return
        if (!tab.isRunning) return
        try {
            queue.put(byteArrayOf(codePoint.toByte()))
        } catch (e: Throwable) {
            Log.e(TAG, "Error sending control char", e)
        }
    }

    fun sendCtrlC() = sendControlCharacter(3)
    fun sendCtrlD() = sendControlCharacter(4)
    fun sendTab() = writeInput(null, "\t")

    fun switchTab(tabId: String) {
        _activeTabId.value = tabId
    }

    fun closeTab(tabId: String) {
        val tab = _tabs.value.find { it.id == tabId } ?: return
        val queue = inputQueues.remove(tabId)
        try { queue?.put(byteArrayOf(0x00)) } catch (_: Throwable) {}
        tab.process?.let { p -> try { p.destroyForcibly() } catch (_: Throwable) {} }
        _tabs.value = _tabs.value.filter { it.id != tabId }
        if (_activeTabId.value == tabId) {
            _activeTabId.value = _tabs.value.lastOrNull()?.id
        }
        if (_tabs.value.isEmpty()) createNewTab()
    }

    fun clearOutput(tabId: String?) {
        val id = tabId ?: _activeTabId.value ?: return
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == id) tab.copy(output = "", outputLines = emptyList()) else tab
        }
    }

    override fun onCleared() {
        super.onCleared()
        _tabs.value.forEach { tab ->
            inputQueues.remove(tab.id)
            tab.process?.let { p -> try { p.destroyForcibly() } catch (_: Throwable) {} }
        }
        inputQueues.clear()
    }
}

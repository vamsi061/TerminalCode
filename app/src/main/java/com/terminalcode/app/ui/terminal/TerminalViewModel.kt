package com.terminalcode.app.ui.terminal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terminalcode.app.TerminalCodeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

class TerminalViewModel : ViewModel() {

    companion object {
        private const val TAG = "TerminalVM"
    }

    data class TerminalTab(
        val id: String = UUID.randomUUID().toString(),
        val title: String = "Terminal",
        val process: Process? = null,
        val isRunning: Boolean = false,
        val output: String = "",
        val outputLines: List<String> = emptyList(),
        val isUbuntu: Boolean = false
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

    fun createNewTab(launchUbuntu: Boolean = false): TerminalTab {
        try {
            val context = TerminalCodeApp.instance
            val filesDir = context.filesDir
            val homeDir = filesDir.absolutePath
            val binDir = File(filesDir, "bin").absolutePath

            val pb = ProcessBuilder()
            val env = pb.environment()

            // Base environment
            env["TERM"] = "xterm-256color"
            env["HOME"] = homeDir
            env["SHELL"] = "/system/bin/sh"
            env["USER"] = "shell"
            env["LANG"] = "en_US.UTF-8"
            env["PATH"] = "/system/bin:/system/xbin:/sbin:/bin:/usr/bin:/usr/sbin:$binDir"
            env["PS1"] = "\\[\\e[1;32m\\]TerminalCode\\[\\e[0m\\]:\\[\\e[1;34m\\]\\w\\[\\e[0m\\]$ "

            if (launchUbuntu) {
                // Check for udroid/PRoot installations in common locations
                val prootPaths = listOf(
                    "$binDir/proot",
                    "$homeDir/ubuntu-on-android/proot",
                    "/data/data/com.termux/files/usr/bin/proot",
                    "/system/bin/proot"
                )
                val prootBin = prootPaths.firstOrNull { File(it).exists() }
                val ubuntuFs = File(filesDir, "ubuntu-fs")

                if (prootBin != null && ubuntuFs.isDirectory) {
                    Log.d(TAG, "Launching Ubuntu via: $prootBin")
                    pb.command(prootBin,
                        "-b", "/dev", "-b", "/proc", "-b", "/sys",
                        "-b", "/system", "-b", "/data", "-b", "/storage",
                        "-r", ubuntuFs.absolutePath,
                        "-w", "/root",
                        "/bin/bash", "--login"
                    )
                    env["HOME"] = "/root"
                    env["USER"] = "root"
                } else {
                    // No Ubuntu found, create a normal tab instead
                    Log.d(TAG, "Ubuntu not installed, starting normal shell")
                    return createNewTab(launchUbuntu = false)
                }
            } else {
                pb.command("/system/bin/sh", "-i")
            }

            pb.redirectErrorStream(true)
            val process = pb.start()

            val tab = TerminalTab(
                process = process,
                isRunning = true,
                output = "",
                outputLines = emptyList(),
                isUbuntu = launchUbuntu
            )

            _tabs.value = _tabs.value + tab
            _activeTabId.value = tab.id
            startIoThreads(tab.id, process)

            Log.d(TAG, "Tab created: ubuntu=$launchUbuntu")
            return tab

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create terminal", e)
            val fallback = TerminalTab(
                title = "Error",
                isRunning = false,
                output = "Error: ${e.message ?: "Unknown"}\n",
                outputLines = listOf("Error: ${e.message ?: "Unknown"}\n")
            )
            _tabs.value = _tabs.value + fallback
            _activeTabId.value = fallback.id
            return fallback
        }
    }

    private fun startIoThreads(tabId: String, process: Process) {
        val inputQueue = LinkedBlockingQueue<ByteArray>(100)
        inputQueues[tabId] = inputQueue

        // Output reader
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(
                    InputStreamReader(process.inputStream, Charsets.UTF_8)
                )
                val buffer = CharArray(4096)
                val outputBuilder = StringBuilder()

                while (true) {
                    val read = reader.read(buffer, 0, buffer.size)
                    if (read == -1) break
                    outputBuilder.append(buffer, 0, read)
                    val fullText = outputBuilder.toString()
                    updateTabOutput(tabId, fullText)
                    if (outputBuilder.length > 100000) {
                        outputBuilder.delete(0, 50000)
                    }
                }
            } catch (e: IOException) {
                if (e.message?.contains("Stream closed") != true) {
                    Log.d(TAG, "Output stream ended for tab $tabId")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Output error tab $tabId", e)
            } finally {
                updateTabRunning(tabId, false)
            }
        }

        // Input writer
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val writer = BufferedWriter(
                    OutputStreamWriter(process.outputStream, Charsets.UTF_8)
                )
                while (true) {
                    val data = inputQueue.take()
                    if (data.size == 1 && data[0] == 0x00.toByte()) break
                    if (data.isEmpty()) continue
                    writer.write(String(data, Charsets.UTF_8))
                    writer.flush()
                }
            } catch (e: IOException) {
                Log.d(TAG, "Input stream ended for tab $tabId")
            } catch (e: Throwable) {
                Log.e(TAG, "Input error tab $tabId", e)
            }
        }
    }

    fun writeInput(tabId: String?, text: String) {
        val id = tabId ?: _activeTabId.value ?: return
        val tab = _tabs.value.find { it.id == id } ?: return
        if (!tab.isRunning) return
        val queue = inputQueues[id] ?: return
        try { queue.put(text.toByteArray(Charsets.UTF_8)) } catch (_: Throwable) {}
    }

    private fun sendCtrl(code: Int) {
        val id = _activeTabId.value ?: return
        val queue = inputQueues[id] ?: return
        try { queue.put(byteArrayOf(code.toByte())) } catch (_: Throwable) {}
    }

    fun sendCtrlC() = sendCtrl(3)
    fun sendCtrlD() = sendCtrl(4)
    fun sendTab() = writeInput(null, "\t")

    fun switchTab(tabId: String) { _activeTabId.value = tabId }
    fun clearOutput(tabId: String?) {
        val id = tabId ?: _activeTabId.value ?: return
        _tabs.value = _tabs.value.map { t ->
            if (t.id == id) t.copy(output = "", outputLines = emptyList()) else t
        }
    }

    fun closeTab(tabId: String) {
        val tab = _tabs.value.find { it.id == tabId } ?: return
        inputQueues.remove(tabId)?.put(byteArrayOf(0x00))
        tab.process?.destroyForcibly()
        _tabs.value = _tabs.value.filter { it.id != tabId }
        if (_activeTabId.value == tabId) {
            _activeTabId.value = _tabs.value.lastOrNull()?.id
        }
        if (_tabs.value.isEmpty()) createNewTab()
    }

    private fun updateTabOutput(tabId: String, text: String) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) {
                val truncated = if (text.length > 50000) text.takeLast(50000) else text
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

    override fun onCleared() {
        super.onCleared()
        _tabs.value.forEach { tab ->
            inputQueues.remove(tab.id)
            tab.process?.destroyForcibly()
        }
        inputQueues.clear()
    }
}

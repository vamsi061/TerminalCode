package com.terminalcode.app.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.OutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Terminal session using simple PIPE approach.
 *
 * No PTY complexity - just spawn bash with -i flag over PIPEs:
 * - Write user input to process stdin
 * - Read process stdout (merged with stderr) and display
 *
 * bash -i forces interactive mode so prompts and job control work
 * even though stdin/stdout are PIPEs.
 */
class TerminalSession(
    val sessionId: String = UUID.randomUUID().toString(),
    private val shellPath: String = "/system/bin/sh"
) {
    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val inputQueue: BlockingQueue<ByteArray> = LinkedBlockingQueue()
    private var shellProcess: Process? = null
    private var outputReader: Thread? = null
    private var inputWriter: Thread? = null
    private var job: Job? = null
    private val running = AtomicBoolean(false)

    companion object {
        private val SHELL_PATHS = listOf(
            System.getenv("SHELL") ?: "",
            "/data/data/com.termux/files/usr/bin/bash",
            "/data/data/com.termux/files/usr/bin/zsh",
            "/bin/bash", "/usr/bin/bash",
            "/bin/zsh", "/usr/bin/zsh",
            "/system/bin/bash",
            "/system/bin/sh"
        ).filter { it.isNotEmpty() }

        private val LINUX_PATH = listOf(
            "/usr/local/sbin", "/usr/local/bin",
            "/usr/sbin", "/usr/bin",
            "/sbin", "/bin",
            "/data/data/com.termux/files/usr/bin",
            "/system/bin", "/system/xbin"
        ).joinToString(":")
    }

    fun start() {
        if (running.get()) return
        running.set(true)
        _isRunning.value = true
        _output.value = ""

        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                spawnShell()
                startThreads()
                // Send an initial newline + echo to force the shell to produce output
                // This proves the I/O pipeline is working
                inputQueue.offer("\n".toByteArray())
                Thread.sleep(200)
                inputQueue.offer("echo '[Terminal ready]'\n".toByteArray())
            } catch (e: Exception) {
                _output.value += "\r\n\u001b[31mError: ${e.message}\u001b[0m\r\n"
                running.set(false)
                _isRunning.value = false
            }
        }
    }

    private fun spawnShell() {
        val shellToUse = findShell()
        val pb = ProcessBuilder()

        try {
            // Use PIPEs for stdin/stdout/stderr
            pb.redirectInput(ProcessBuilder.Redirect.PIPE)
            pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
            pb.redirectErrorStream(true) // Merge stderr into stdout

            // Set environment
            val env = pb.environment()
            env["TERM"] = "xterm-256color"
            env["HOME"] = System.getenv("HOME") ?: "/root"
            env["SHELL"] = shellToUse
            env["USER"] = System.getenv("USER") ?: "shell"
            env["LOGNAME"] = System.getenv("USER") ?: "shell"
            env["LANG"] = "en_US.UTF-8"
            env["PATH"] = System.getenv("PATH") ?: LINUX_PATH
            env["PS1"] = "\\[\\e[32m\\]\\u@\\h\\[\\e[0m\\]:\\[\\e[34m\\]\\w\\[\\e[0m\\]\\$ "

            // Force interactive mode - bash will show prompt and accept input
            pb.command(shellToUse, "-i")
            shellProcess = pb.start()

        } catch (e: Exception) {
            throw IOException("Failed to spawn shell '$shellToUse': ${e.message}", e)
        }
    }

    private fun findShell(): String {
        for (path in SHELL_PATHS) {
            try {
                if (java.io.File(path).exists() && java.io.File(path).canExecute()) return path
            } catch (e: Exception) { continue }
        }
        return shellPath
    }

    private fun startThreads() {
        val processIn: OutputStream = shellProcess!!.outputStream
        val processOut: InputStream = shellProcess!!.inputStream

        // Read shell output from process stdout -> StateFlow
        outputReader = Thread {
            try {
                val buf = ByteArray(4096)
                while (running.get()) {
                    val n = processOut.read(buf)
                    if (n <= 0) break
                    val text = String(buf, 0, n, Charsets.UTF_8)
                    _output.value += text
                    // Yield to allow other threads to process
                    Thread.yield()
                }
            } catch (e: IOException) {
                // Silently exit when session stops
            }
        }.apply {
            isDaemon = true
            name = "shell-output-reader"
            start()
        }

        // Write user input to process stdin
        inputWriter = Thread {
            try {
                while (running.get()) {
                    val data = inputQueue.take()
                    processIn.write(data)
                    processIn.flush()
                }
            } catch (e: Exception) {
                // Clean exit
            }
        }.apply {
            isDaemon = true
            name = "shell-input-writer"
            start()
        }
    }

    fun writeInput(input: String) {
        if (!running.get()) return
        inputQueue.offer(input.toByteArray(Charsets.UTF_8))
    }

    fun writeBytes(data: ByteArray) {
        if (!running.get()) return
        inputQueue.offer(data)
    }

    private var cols: Int = 80
    private var rows: Int = 24

    fun resize(cols: Int, rows: Int) {
        this.cols = cols
        this.rows = rows
        // No PTY to resize in PIPE mode, but we track the size
    }

    fun clearOutput() { _output.value = "" }

    fun sendCtrlC() { writeInput("\u0003") }
    fun sendCtrlD() { writeInput("\u0004") }
    fun sendTab() { writeInput("\u0009") }

    fun stop() {
        running.set(false)
        _isRunning.value = false
        try { outputReader?.interrupt() } catch (_: Exception) {}
        try { inputWriter?.interrupt() } catch (_: Exception) {}
        try {
            shellProcess?.let { p ->
                p.destroy()
                p.waitFor(3, TimeUnit.SECONDS)
                p.destroyForcibly()
            }
        } catch (_: Exception) {}
        shellProcess = null
        job?.cancel()
        job = null
        _output.value += "\r\n\u001b[33m[Session terminated]\u001b[0m\r\n"
    }
}

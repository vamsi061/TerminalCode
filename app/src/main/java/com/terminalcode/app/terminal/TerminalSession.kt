package com.terminalcode.app.terminal

import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages a pseudo-terminal (PTY) session connected to a shell process.
 *
 * The shell connects directly to the PTY slave device (real terminal),
 * not through PIPEs, so it runs in full interactive mode with prompt,
 * job control, and all terminal features.
 */
class TerminalSession(
    val sessionId: String = UUID.randomUUID().toString(),
    private val shellPath: String = "/system/bin/sh"
) {
    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val inputChannel = Channel<ByteArray>(Channel.BUFFERED)

    private var masterFd: FileDescriptor? = null
    private var slaveFd: FileDescriptor? = null
    private var slavePath: String? = null
    private var shellProcess: Process? = null
    private var outputThread: Thread? = null
    private var inputThread: Thread? = null
    private var job: Job? = null
    private val running = AtomicBoolean(false)

    private var rows: Int = 24
    private var columns: Int = 80

    companion object {
        private const val TAG = "TerminalSession"
        private val SHELL_PATHS = listOf(
            System.getenv("SHELL") ?: "",
            "/data/data/com.termux/files/usr/bin/bash",
            "/data/data/com.termux/files/usr/bin/zsh",
            "/data/data/com.termux/files/usr/bin/fish",
            "/bin/bash", "/usr/bin/bash",
            "/bin/zsh", "/usr/bin/zsh",
            "/system/bin/bash",
            "/system/bin/sh", "/bin/sh"
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

        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                setupPty()
                spawnShell()
                startIoThreads()
            } catch (e: Exception) {
                _output.value += "\r\n\u001b[31mError: ${e.message}\u001b[0m\r\n"
                running.set(false)
                _isRunning.value = false
            }
        }
    }

    private fun setupPty() {
        try {
            masterFd = Os.open("/dev/ptmx",
                OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)

            try {
                val osClass = Os::class.java
                osClass.getMethod("grantpt", FileDescriptor::class.java)
                    .invoke(null, masterFd)
                osClass.getMethod("unlockpt", FileDescriptor::class.java)
                    .invoke(null, masterFd)
                slavePath = osClass.getMethod("ptsname", FileDescriptor::class.java)
                    .invoke(null, masterFd) as String
            } catch (e: Exception) {
                val ptsNum = try {
                    java.io.File("/sys/devices/virtual/tty/ptmx/tty").readText().trim()
                } catch (e2: Exception) {
                    slaveFd = masterFd
                    slavePath = null
                    return
                }
                slavePath = "/dev/pts/$ptsNum"
            }

            // Open slave side so we can close it later
            slaveFd = slavePath?.let {
                try {
                    Os.open(it, OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)
                } catch (e: Exception) { null }
            }

        } catch (e: Exception) {
            throw IOException("Failed to create PTY: ${e.message}", e)
        }
    }

    /**
     * Spawns shell connected DIRECTLY to the PTY slave device.
     * This makes the shell think it's on a real terminal (full interactive mode).
     */
    private fun spawnShell() {
        val shellToUse = findShell()
        val pb = ProcessBuilder()

        try {
            // Redirect shell stdin/stdout/stderr to the PTY slave device
            // This is KEY - shell connects to real TTY, not a PIPE
            val slaveFile = slavePath?.let { java.io.File(it) }
            if (slaveFile != null) {
                pb.redirectInput(ProcessBuilder.Redirect.from(slaveFile))
                pb.redirectOutput(ProcessBuilder.Redirect.to(slaveFile))
                pb.redirectErrorStream(true)
            } else {
                pb.redirectInput(ProcessBuilder.Redirect.PIPE)
                pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
                pb.redirectErrorStream(true)
            }

            val env = pb.environment()
            env["TERM"] = "xterm-256color"
            env["HOME"] = System.getenv("HOME") ?: "/root"
            env["SHELL"] = shellToUse
            env["USER"] = System.getenv("USER") ?: "shell"
            env["LOGNAME"] = System.getenv("USER") ?: "shell"
            env["PATH"] = System.getenv("PATH") ?: LINUX_PATH
            env["LANG"] = "en_US.UTF-8"

            // Start shell without special flags since it's on a real TTY
            pb.command(shellToUse)

            shellProcess = pb.start()

        } catch (e: Exception) {
            throw IOException("Failed to spawn shell: ${e.message}", e)
        }
    }

    private fun findShell(): String {
        for (path in SHELL_PATHS) {
            try {
                val file = java.io.File(path)
                if (file.exists() && file.canExecute()) return path
            } catch (e: Exception) { continue }
        }
        return shellPath
    }

    /**
     * Reads shell output from PTY master and sends to WebView.
     * Writes user input to PTY master (which goes to shell via slave).
     */
    private fun startIoThreads() {
        // Output: read from PTY master (shell output) -> send to WebView
        outputThread = Thread {
            try {
                val buffer = ByteArray(4096)
                val inputStream = FileInputStream(masterFd!!)
                while (running.get()) {
                    val read = inputStream.read(buffer)
                    if (read <= 0) break
                    val text = String(buffer, 0, read, Charsets.UTF_8)
                    _output.value += text
                }
            } catch (e: IOException) { /* shell exited */ }
        }.apply { isDaemon = true; name = "terminal-output"; start() }

        // Input: read from input channel (user keystrokes) -> write to PTY master
        inputThread = Thread {
            try {
                val masterOutputStream = FileOutputStream(masterFd!!)
                while (running.get()) {
                    val data = runBlocking { inputChannel.receive() }
                    masterOutputStream.write(data)
                    masterOutputStream.flush()
                }
            } catch (e: Exception) { }
        }.apply { isDaemon = true; name = "terminal-input"; start() }
    }

    fun writeInput(input: String) {
        if (!running.get()) return
        inputChannel.trySend(input.toByteArray(Charsets.UTF_8))
    }

    fun writeBytes(data: ByteArray) {
        if (!running.get()) return
        inputChannel.trySend(data)
    }

    fun resize(cols: Int, rows: Int) {
        this.columns = cols
        this.rows = rows
        try {
            if (masterFd != null) {
                val TIOCSWINSZ = 0x5414
                val winsize = java.nio.ByteBuffer.allocate(8)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .putShort(rows.toShort())
                    .putShort(cols.toShort())
                    .putShort(0).putShort(0)
                    .array()
                try {
                    val osClass = Os::class.java
                    osClass.getMethod("ioctl", FileDescriptor::class.java,
                        Int::class.java, ByteArray::class.java)
                        .invoke(null, masterFd!!, TIOCSWINSZ, winsize)
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
    }

    fun clearOutput() { _output.value = "" }
    fun sendCtrlC() { writeInput("\u0003") }
    fun sendCtrlD() { writeInput("\u0004") }

    fun stop() {
        running.set(false)
        _isRunning.value = false
        try { inputChannel.close() } catch (_: Exception) {}
        try { outputThread?.interrupt() } catch (_: Exception) {}
        try { inputThread?.interrupt() } catch (_: Exception) {}
        try {
            shellProcess?.destroy()
            shellProcess?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            shellProcess?.destroyForcibly()
        } catch (_: Exception) {}
        try { if (masterFd != null) Os.close(masterFd!!) } catch (_: Exception) {}
        try { if (slaveFd != null) Os.close(slaveFd!!) } catch (_: Exception) {}
        masterFd = null; slaveFd = null
        shellProcess = null; job?.cancel(); job = null
        _output.value += "\r\n\u001b[33m[Session terminated]\u001b[0m\r\n"
    }
}

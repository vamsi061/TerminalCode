package com.terminalcode.app.terminal

import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileDescriptor
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Terminal session using PTY (pseudo-terminal).
 *
 * Architecture (inspired by Termux):
 * 1. Open PTY master (/dev/ptmx) → grantpt/unlockpt → get slave path
 * 2. Open PTY slave device (needed for PTY to function)
 * 3. Spawn shell with stdin/stdout/stderr redirected to the slave device
 * 4. Read shell output from PTY master → display in WebView
 * 5. Write user input to PTY master → goes to shell via slave
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

    private var masterFd: FileDescriptor? = null
    private var slaveFd: FileDescriptor? = null
    private var slavePath: String? = null
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
        _output.value = "Starting terminal...\r\n"

        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                setupPty()
                spawnShell()
                startThreads()
                _output.value += "Terminal ready.\r\n"
            } catch (e: Exception) {
                _output.value += "\r\n\u001b[31mError: ${e.message}\u001b[0m\r\n"
                running.set(false)
                _isRunning.value = false
            }
        }
    }

    /**
     * Creates a PTY pair (master + slave) using Android's Os API.
     */
    private fun setupPty() {
        try {
            // 1. Open PTY master
            masterFd = Os.open("/dev/ptmx",
                OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)

            // 2. grantpt - grant access to slave
            val osClass = Os::class.java
            osClass.getMethod("grantpt", FileDescriptor::class.java)
                .invoke(null, masterFd)

            // 3. unlockpt - unlock the slave
            osClass.getMethod("unlockpt", FileDescriptor::class.java)
                .invoke(null, masterFd)

            // 4. ptsname - get slave device path
            slavePath = osClass.getMethod("ptsname", FileDescriptor::class.java)
                .invoke(null, masterFd) as String

            if (slavePath == null || slavePath!!.isEmpty()) {
                throw IOException("Failed to get PTY slave name")
            }

            // 5. Open slave device (critical - PTY needs both ends open)
            slaveFd = Os.open(slavePath!!,
                OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)

        } catch (e: Exception) {
            throw IOException("PTY setup failed: ${e.message}", e)
        }
    }

    /**
     * Spawns shell with stdin/stdout/stderr connected to the PTY slave device.
     * This gives the shell a real TTY - it will show a prompt, support job control, etc.
     */
    private fun spawnShell() {
        val shellToUse = findShell()
        val pb = ProcessBuilder()

        try {
            val slaveFile = java.io.File(slavePath!!)

            // Connect shell's I/O directly to PTY slave device
            pb.redirectInput(ProcessBuilder.Redirect.from(slaveFile))
            pb.redirectOutput(ProcessBuilder.Redirect.to(slaveFile))
            pb.redirectErrorStream(true) // stderr -> stdout (both go to slave)

            // Set environment
            val env = pb.environment()
            env["TERM"] = "xterm-256color"
            env["HOME"] = System.getenv("HOME") ?: "/root"
            env["SHELL"] = shellToUse
            env["USER"] = System.getenv("USER") ?: "shell"
            env["LOGNAME"] = System.getenv("USER") ?: "shell"
            env["LANG"] = "en_US.UTF-8"
            env["PATH"] = System.getenv("PATH") ?: LINUX_PATH

            // Build command
            val cmdArgs = mutableListOf(shellToUse)
            pb.command(cmdArgs)

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

    /**
     * Starts I/O threads:
     * - outputReader: reads shell output from PTY master → sends to StateFlow
     * - inputWriter: reads user input from channel → writes to PTY master
     */
    private fun startThreads() {
        val masterIn = FileInputStream(masterFd!!)
        val masterOut = FileOutputStream(masterFd!!)

        // Read shell output from PTY master
        outputReader = Thread {
            try {
                val buf = ByteArray(4096)
                while (running.get()) {
                    val n = masterIn.read(buf)
                    if (n <= 0) break
                    val text = String(buf, 0, n, Charsets.UTF_8)
                    _output.value += text
                }
            } catch (e: IOException) {
                // Silently exit when session stops
            }
        }.apply {
            isDaemon = true
            name = "pty-output-reader"
            start()
        }

        // Write user input to PTY master
        inputWriter = Thread {
            try {
                while (running.get()) {
                    val data = inputQueue.take()
                    masterOut.write(data)
                    masterOut.flush()
                }
            } catch (e: Exception) {
                // Channel closed, session stopping
            }
        }.apply {
            isDaemon = true
            name = "pty-input-writer"
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

    fun resize(cols: Int, rows: Int) {
        try {
            if (masterFd != null) {
                val TIOCSWINSZ = 0x5414
                val winsize = java.nio.ByteBuffer.allocate(8)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .putShort(rows.toShort()).putShort(cols.toShort())
                    .putShort(0).putShort(0).array()
                Os::class.java.getMethod("ioctl", FileDescriptor::class.java,
                    Int::class.java, ByteArray::class.java)
                    .invoke(null, masterFd!!, TIOCSWINSZ, winsize)
            }
        } catch (e: Exception) { }
    }

    fun clearOutput() { _output.value = "" }

    fun sendCtrlC() { writeInput("\u0003") }
    fun sendCtrlD() { writeInput("\u0004") }
    fun sendTab() { writeInput("\u0009") }

    fun stop() {
        running.set(false)
        _isRunning.value = false
        // BlockingQueue doesn't need explicit close - interrupt handles it
        try { outputReader?.interrupt() } catch (_: Exception) {}
        try { inputWriter?.interrupt() } catch (_: Exception) {}
        try { shellProcess?.destroy(); shellProcess?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS); shellProcess?.destroyForcibly() } catch (_: Exception) {}
        try { if (slaveFd != null) Os.close(slaveFd!!) } catch (_: Exception) {}
        try { if (masterFd != null) Os.close(masterFd!!) } catch (_: Exception) {}
        slaveFd = null; masterFd = null; shellProcess = null; job?.cancel(); job = null
        _output.value += "\r\n\u001b[33m[Session terminated]\u001b[0m\r\n"
    }
}

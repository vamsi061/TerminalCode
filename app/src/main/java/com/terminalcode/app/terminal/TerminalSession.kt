package com.terminalcode.app.terminal

import android.system.ErrnoException
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
 * This class handles:
 * - Creating and managing a PTY device
 * - Spawning a shell (bash/sh/zsh)
 * - Reading output from the shell and making it available via a flow
 * - Writing user input to the shell
 * - Managing the terminal size (rows/columns)
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
    private var shellProcess: Process? = null
    private var outputThread: Thread? = null
    private var inputThread: Thread? = null
    private var job: Job? = null
    private val running = AtomicBoolean(false)

    private var rows: Int = 24
    private var columns: Int = 80

    companion object {
        private const val TAG = "TerminalSession"
        // Linux shell paths - prioritized: bash first, then zsh, then sh
        private val SHELL_PATHS = listOf(
            // Check $SHELL env var first (user's preferred shell)
            System.getenv("SHELL") ?: "",
            // Termux bash (most common on Android)
            "/data/data/com.termux/files/usr/bin/bash",
            "/data/data/com.termux/files/usr/bin/zsh",
            "/data/data/com.termux/files/usr/bin/fish",
            // Standard Linux paths (for PRoot environments)
            "/bin/bash",
            "/usr/bin/bash",
            "/bin/zsh",
            "/usr/bin/zsh",
            // Android system paths (fallback)
            "/system/bin/bash",
            "/system/bin/sh",
            "/bin/sh"
        ).filter { it.isNotEmpty() }

        // Linux PATH for full terminal capability
        private val LINUX_PATH = listOf(
            "/usr/local/sbin",
            "/usr/local/bin",
            "/usr/sbin",
            "/usr/bin",
            "/sbin",
            "/bin",
            "/data/data/com.termux/files/usr/bin",
            "/data/data/com.termux/files/usr/bin/applets",
            "/system/bin",
            "/system/xbin"
        ).joinToString(":")
    }

    /**
     * Starts the terminal session by:
     * 1. Opening a PTY
     * 2. Spawning a shell process
     * 3. Connecting the shell to the PTY
     * 4. Starting I/O threads
     */
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

    /**
     * Creates a pseudo-terminal device using the Linux PTY system.
     * Opens /dev/ptmx to get the master side, then grants access and
     * unlocks the slave side. Uses reflection for hidden API methods.
     */
    private fun setupPty() {
        try {
            // Open the master side of the PTY
            masterFd = Os.open("/dev/ptmx",
                OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)

            // Grant & unlock PTY using reflection (hidden APIs)
            try {
                val osClass = Os::class.java
                osClass.getMethod("grantpt", FileDescriptor::class.java)
                    .invoke(null, masterFd)
                osClass.getMethod("unlockpt", FileDescriptor::class.java)
                    .invoke(null, masterFd)
                val slaveName = osClass.getMethod("ptsname", FileDescriptor::class.java)
                    .invoke(null, masterFd) as String

                // Open the slave side
                slaveFd = Os.open(slaveName,
                    OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)
            } catch (e: Exception) {
                // Fallback: directly open slave via /dev/pts/
                val ptsNum = try {
                    java.io.File("/sys/devices/virtual/tty/ptmx/tty").readText().trim()
                } catch (e: Exception) {
                    // Last resort - open pty directly
                    slaveFd = masterFd
                    return
                }
                slaveFd = Os.open("/dev/pts/$ptsNum",
                    OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)
            }

        } catch (e: ErrnoException) {
            throw IOException("Failed to create PTY: ${e.message}", e)
        }
    }

    /**
     * Spawns a shell process connected to the slave side of the PTY.
     * Detects shell type and applies appropriate flags for a full Linux experience.
     */
    private fun spawnShell() {
        val shellToUse = findShell()
        val isBashOrZsh = shellToUse.contains("bash") || shellToUse.contains("zsh")
        val pb = ProcessBuilder()

        try {
            pb.redirectInput(ProcessBuilder.Redirect.PIPE)
            pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
            pb.redirectErrorStream(true)

            // Set comprehensive Linux environment
            val env = pb.environment()
            env["TERM"] = "xterm-256color"
            env["HOME"] = System.getenv("HOME") ?: "/root"
            env["SHELL"] = shellToUse
            env["USER"] = System.getenv("USER") ?: "u0_a" + android.os.Process.myUid()
            env["LOGNAME"] = env["USER"]
            env["PATH"] = System.getenv("PATH") ?: LINUX_PATH
            env["LANG"] = "en_US.UTF-8"
            env["LC_ALL"] = "en_US.UTF-8"
            env["EDITOR"] = "nano"
            env["PAGER"] = "less"

            // Use --login for bash/zsh to source profile, no flag for others
            if (isBashOrZsh) {
                pb.command(shellToUse, "--login")
            } else {
                pb.command(shellToUse)
            }

            shellProcess = pb.start()

            val processInput = shellProcess!!.outputStream
            val processOutput = shellProcess!!.inputStream

            // Pipe: PTY slave input -> process output
            Thread {
                try {
                    val buffer = ByteArray(4096)
                    val inputStream = FileInputStream(slaveFd!!)
                    while (running.get()) {
                        val read = inputStream.read(buffer)
                        if (read <= 0) break
                        processInput.write(buffer, 0, read)
                        processInput.flush()
                    }
                } catch (e: IOException) { }
            }.apply {
                isDaemon = true
                name = "pty-to-process"
                start()
            }

            // Pipe: Process output -> PTY master
            outputThread = Thread {
                try {
                    val buffer = ByteArray(4096)
                    val masterOutputStream = FileOutputStream(masterFd!!)
                    while (running.get()) {
                        val read = processOutput.read(buffer)
                        if (read <= 0) break
                        masterOutputStream.write(buffer, 0, read)
                        masterOutputStream.flush()
                        val text = String(buffer, 0, read, Charsets.UTF_8)
                        _output.value += text
                    }
                } catch (e: IOException) { }
            }.apply {
                isDaemon = true
                name = "process-output"
                start()
            }

        } catch (e: Exception) {
            throw IOException("Failed to spawn shell: ${e.message}", e)
        }
    }

    /**
     * Finds the best available shell. Checks $SHELL first,
     * then looks for bash in standard Linux and Termux paths.
     */
    private fun findShell(): String {
        for (path in SHELL_PATHS) {
            try {
                val file = java.io.File(path)
                if (file.exists() && file.canExecute()) {
                    return path
                }
            } catch (e: Exception) { continue }
        }
        return shellPath
    }

    /**
     * Starts threads to handle I/O between the PTY and the WebView interface.
     */
    private fun startIoThreads() {
        // Input thread: reads from the input channel (user keystrokes)
        // and writes to the PTY master
        inputThread = Thread {
            try {
                val masterOutputStream = FileOutputStream(masterFd!!)
                while (running.get()) {
                    val data = runBlocking { inputChannel.receive() }
                    masterOutputStream.write(data)
                    masterOutputStream.flush()
                }
            } catch (e: Exception) {
                // Channel closed or session ended
            }
        }.apply {
            isDaemon = true
            name = "terminal-input"
            start()
        }
    }

    /**
     * Writes user input to the terminal session.
     * This is called from the WebView JavaScript interface when
     * the user types in the xterm.js terminal.
     */
    fun writeInput(input: String) {
        if (!running.get()) return
        val bytes = input.toByteArray(Charsets.UTF_8)
        inputChannel.trySend(bytes)
    }

    /**
     * Writes raw bytes to the terminal session.
     */
    fun writeBytes(data: ByteArray) {
        if (!running.get()) return
        inputChannel.trySend(data)
    }

    /**
     * Resizes the terminal PTY to the specified dimensions.
     * This is called when the xterm.js terminal is resized.
     */
    fun resize(cols: Int, rows: Int) {
        this.columns = cols
        this.rows = rows
        try {
            if (masterFd != null) {
                // The TIOCSWINSZ ioctl code
                val TIOCSWINSZ = 0x5414
                // Pack winsize struct using ByteBuffer for clarity:
                // struct winsize { unsigned short ws_row; unsigned short ws_col;
                //                  unsigned short ws_xpixel; unsigned short ws_ypixel; }
                val winsize = java.nio.ByteBuffer.allocate(8)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .putShort(rows.toShort())
                    .putShort(cols.toShort())
                    .putShort(0) // xpixel
                    .putShort(0) // ypixel
                    .array()
                // Use reflection for hidden ioctl API
                try {
                    val osClass = Os::class.java
                    osClass.getMethod("ioctl", FileDescriptor::class.java, Int::class.java, ByteArray::class.java)
                        .invoke(null, masterFd!!, TIOCSWINSZ, winsize)
                } catch (e: Exception) {
                    // ioctl not available, ignore
                }
            }
        } catch (e: Exception) {
            // Ignore resize errors
        }
    }

    /**
     * Clears the terminal output buffer.
     */
    fun clearOutput() {
        _output.value = ""
    }

    /**
     * Sends a Ctrl+C signal to the foreground process.
     */
    fun sendCtrlC() {
        writeInput("\u0003")
    }

    /**
     * Sends a Ctrl+D signal (EOF).
     */
    fun sendCtrlD() {
        writeInput("\u0004")
    }

    /**
     * Stops the terminal session and cleans up all resources.
     */
    fun stop() {
        running.set(false)
        _isRunning.value = false

        try {
            inputChannel.close()
        } catch (_: Exception) {}

        try {
            outputThread?.interrupt()
        } catch (_: Exception) {}

        try {
            inputThread?.interrupt()
        } catch (_: Exception) {}

        try {
            shellProcess?.destroy()
            shellProcess?.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            shellProcess?.destroyForcibly()
        } catch (_: Exception) {}

        try {
            if (masterFd != null) Os.close(masterFd!!)
        } catch (_: Exception) {}

        try {
            if (slaveFd != null) Os.close(slaveFd!!)
        } catch (_: Exception) {}

        masterFd = null
        slaveFd = null
        shellProcess = null
        job?.cancel()
        job = null

        _output.value += "\r\n\u001b[33m[Session terminated]\u001b[0m\r\n"
    }
}

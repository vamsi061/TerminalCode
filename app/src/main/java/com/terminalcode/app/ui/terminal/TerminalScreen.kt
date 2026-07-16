package com.terminalcode.app.ui.terminal

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.view.TerminalView
import com.terminalcode.app.ui.theme.*

/**
 * Terminal screen using Termux's native TerminalView.
 *
 * Features:
 * - Real PTY-backed terminal via Termux's JNI terminal emulator
 * - Multiple terminal tabs
 * - Tab management (add/close/switch)
 * - Control toolbar (^C, ^D, Tab, Clear)
 * - GitHub Dark themed UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Tab bar
        if (tabs.isNotEmpty()) {
            TerminalTabBar(
                tabs = tabs,
                activeTabId = activeTabId,
                onTabClick = { viewModel.switchTab(it) },
                onTabClose = { viewModel.closeTab(it) },
                onNewTab = { viewModel.createNewTab() }
            )
        }

        // Terminal View
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            tabs.forEach { tab ->
                if (tab.id == activeTabId && tab.session != null) {
                    val session = tab.session

                    // Use Termux's native TerminalView wrapping in AndroidView
                    AndroidView(
                        factory = { ctx ->
                            TerminalView(ctx, null).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                // Set up the terminal client (TerminalViewClient v0.118.3 interface)
                                setTerminalViewClient(object : com.termux.view.TerminalViewClient {
                                    override fun onScale(scale: Float): Float = scale

                                    override fun onSingleTapUp(e: android.view.MotionEvent?) {
                                        requestFocus()
                                        val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                        imm.showSoftInput(this@apply, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                    }

                                    override fun shouldBackButtonBeMappedToEscape(): Boolean = true
                                    override fun shouldEnforceCharBasedInput(): Boolean = true
                                    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
                                    override fun isTerminalViewSelected(): Boolean = true
                                    override fun copyModeChanged(copyMode: Boolean) { }

                                    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: com.termux.terminal.TerminalSession?): Boolean = false
                                    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false

                                    override fun onLongPress(event: android.view.MotionEvent?): Boolean = false

                                    override fun readControlKey(): Boolean = false
                                    override fun readAltKey(): Boolean = false
                                    override fun readShiftKey(): Boolean = false
                                    override fun readFnKey(): Boolean = false

                                    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: com.termux.terminal.TerminalSession?): Boolean = false
                                    override fun onEmulatorSet() { }

                                    override fun logInfo(tag: String?, message: String?) {
                                        android.util.Log.d(tag ?: "TerminalView", message ?: "")
                                    }
                                    override fun logWarn(tag: String?, message: String?) {
                                        android.util.Log.w(tag ?: "TerminalView", message ?: "")
                                    }
                                    override fun logDebug(tag: String?, message: String?) {
                                        android.util.Log.d(tag ?: "TerminalView", message ?: "")
                                    }
                                    override fun logError(tag: String?, message: String?) {
                                        android.util.Log.e(tag ?: "TerminalView", message ?: "")
                                    }
                                    override fun logVerbose(tag: String?, message: String?) { }
                                    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { }
                                    override fun logStackTrace(tag: String?, e: Exception?) { }
                                })

                                // Attach the session - this connects PTY to the view
                                // attachSession() -> updateSize() -> session.updateSize() -> initializeEmulator()
                                // creating the PTY via JNI. The emulator will be properly resized
                                // when the view layout happens (onLayout -> updateSize).
                                try {
                                    attachSession(session)
                                } catch (e: Throwable) {
                                    android.util.Log.e("TerminalScreen", "Failed to attach session", e)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Control toolbar
        TerminalToolbar(
            onCtrlC = { viewModel.sendCtrlC() },
            onCtrlD = { viewModel.sendCtrlD() },
            onTab = { viewModel.sendTab() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TerminalTabBar(
    tabs: List<TerminalViewModel.TerminalTab>,
    activeTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tab list
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            tabs.forEach { tab ->
                val isActive = tab.id == activeTabId
                Surface(
                    onClick = { onTabClick(tab.id) },
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    color = if (isActive) DarkBackground
                    else DarkSurfaceVariant,
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp)
                    ) {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isActive) DarkTextPrimary
                            else DarkTextSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        if (tabs.size > 1) {
                            IconButton(
                                onClick = { onTabClose(tab.id) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close tab",
                                    tint = DarkTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // New tab button
        IconButton(
            onClick = onNewTab,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New tab",
                tint = DarkTextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TerminalToolbar(
    onCtrlC: () -> Unit,
    onCtrlD: () -> Unit,
    onTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = DarkSurface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarButton("^C", "Send Ctrl+C (interrupt)", onClick = onCtrlC)
            ToolbarButton("^D", "Send Ctrl+D (EOF)", onClick = onCtrlD)
            ToolbarButton("Tab", "Send Tab", onClick = onTab)
        }
    }
}

@Composable
private fun ToolbarButton(
    text: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = DarkSurfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = DarkTextPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

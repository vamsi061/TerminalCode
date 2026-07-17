package com.terminalcode.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalcode.app.ui.theme.*

/**
 * Terminal screen using pure Compose UI with PIPE-based shell I/O.
 *
 * This has ZERO native code dependencies - no JNI, no .so files.
 * The shell runs as a subprocess with stdin/stdout connected via pipes.
 * All terminal rendering is done in pure Kotlin/Compose.
 *
 * Features:
 * - Real shell subprocess (not simulated)
 * - Multi-tab support
 * - ANSI color support (basic)
 * - Command input
 * - Scrollable output
 * - Ctrl+C/D/Tab controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()

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

        // Terminal output area
        val activeTab = tabs.find { it.id == activeTabId }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(TerminalBackground)
        ) {
            if (activeTab != null) {
                if (activeTab.isRunning || activeTab.outputLines.isNotEmpty()) {
                    TerminalOutput(
                        lines = activeTab.outputLines,
                        isRunning = activeTab.isRunning,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Error or waiting state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = activeTab.output.ifEmpty { "Starting terminal..." },
                            color = DarkTextSecondary,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            } else {
                // No tabs
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No terminal session",
                        color = DarkTextSecondary,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }

        // Input area
        if (activeTab?.isRunning == true) {
            TerminalInputBar(
                onSend = { text ->
                    viewModel.writeInput(activeTabId, text)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Control toolbar
        TerminalToolbar(
            onCtrlC = { viewModel.sendCtrlC() },
            onCtrlD = { viewModel.sendCtrlD() },
            onTab = { viewModel.sendTab() },
            onClear = { viewModel.clearOutput(activeTabId) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Terminal output display with monospace text and scrolling.
 */
@Composable
private fun TerminalOutput(
    lines: List<String>,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    // Auto-scroll to bottom when new output arrives
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .padding(8.dp)
            .horizontalScroll(horizontalScrollState)
    ) {
        items(lines) { line ->
            val annotated = parseAnsiText(line)
            Text(
                text = annotated,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                ),
                modifier = Modifier.padding(vertical = 0.dp)
            )
        }

        // Blinking cursor
        if (isRunning) {
            item {
                Text(
                    text = "█",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = TerminalCursor
                    ),
                    modifier = Modifier.padding(vertical = 0.dp)
                )
            }
        }
    }
}

/**
 * Input bar for typing commands.
 */
@Composable
private fun TerminalInputBar(
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var history by remember { mutableStateOf(listOf<String>()) }

    Surface(
        modifier = modifier,
        color = DarkSurface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Prompt symbol
            Text(
                text = "$ ",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = TerminalGreen,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(end = 4.dp)
            )

            // Text input field
            BasicTextField(
                value = input,
                onValueChange = { newValue ->
                    input = newValue
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = DarkTextPrimary
                ),
                cursorBrush = SolidColor(TerminalCursor),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                singleLine = true,
                keyboardActions = KeyboardActions(
                    onDone = {
                        val text = input.text.trim()
                        if (text.isNotEmpty()) {
                            onSend("$text\n")
                            history = (history + text).takeLast(100)
                            input = TextFieldValue("")
                        }
                    }
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                decorationBox = { innerTextField ->
                    Box {
                        if (input.text.isEmpty()) {
                            Text(
                                text = "Type a command...",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = DarkTextSecondary
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Send button
            Surface(
                onClick = {
                            val text = input.text.trim()
                            if (text.isNotEmpty()) {
                                onSend("$text\n")
                                history = (history + text).takeLast(100)
                                input = TextFieldValue("")
                            }
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = DarkAccent,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "↵",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
        }
    }
}

/**
 * Basic ANSI escape code parser.
 * Supports: bold, colors (30-37, 90-97), reset, and simple sequences.
 */
private fun parseAnsiText(text: String) = buildAnnotatedString {
    val defaultColor = DarkTextPrimary
    var currentColor = defaultColor
    var isBold = false
    var i = 0

    while (i < text.length) {
        if (text[i] == '\u001b' && i + 1 < text.length && text[i + 1] == '[') {
            // Find end of escape sequence (ends with a letter from A-Z or a-z)
            val endIdx = findAnsiEnd(text, i + 2)
            if (endIdx == -1) {
                // No complete sequence found, take the rest
                append(text.substring(i))
                break
            }

            val cmd = text[endIdx]
            // Only process SGR sequences (ending with 'm' - color/style commands)
            if (cmd == 'm') {
                val params = text.substring(i + 2, endIdx).split(";")
                for (param in params) {
                    when (param) {
                        "0", "" -> {
                            currentColor = defaultColor
                            isBold = false
                        }
                        "1" -> isBold = true
                        "30" -> currentColor = DarkTextPrimary
                        "31" -> currentColor = TerminalRed
                        "32" -> currentColor = TerminalGreen
                        "33" -> currentColor = TerminalYellow
                        "34" -> currentColor = TerminalBlue
                        "35" -> currentColor = TerminalMagenta
                        "36" -> currentColor = TerminalCyan
                        "37" -> currentColor = DarkTextPrimary
                        "90" -> currentColor = DarkTextSecondary
                        "91" -> currentColor = TerminalRed
                        "92" -> currentColor = TerminalGreen
                        "93" -> currentColor = TerminalYellow
                        "94" -> currentColor = TerminalBlue
                        "95" -> currentColor = TerminalMagenta
                        "96" -> currentColor = TerminalCyan
                        "97" -> currentColor = DarkTextPrimary
                    }
                }
            }
            // Non-SGR sequences (cursor movement [A/B/C/D], clear screen [2J], etc.)
            // are stripped/skipped entirely - they don't render in a text-based terminal

            i = endIdx + 1
        } else {
            var end = i + 1
            while (end < text.length && !(text[end] == '\u001b' && end + 1 < text.length && text[end + 1] == '[')) {
                end++
            }

            withStyle(
                SpanStyle(
                    color = currentColor,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                )
            ) {
                append(text.substring(i, end))
            }
            i = end
        }
    }
}

/**
 * Finds the end of an ANSI escape sequence starting at startIdx.
 * ANSI sequences end with a letter (A-Z or a-z).
 */
private fun findAnsiEnd(text: String, startIdx: Int): Int {
    var i = startIdx
    while (i < text.length) {
        val c = text[i]
        if (c in 'A'..'Z' || c in 'a'..'z') return i
        // Numbers, semicolons, and parameter characters are part of the sequence
        if (c in '0'..'9' || c == ';' || c == '?' || c == '=' || c == '<' || c == '>') {
            i++
        } else {
            return -1 // Unexpected character
        }
    }
    return -1 // Unterminated sequence
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
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            tabs.forEach { tab ->
                val isActive = tab.id == activeTabId
                val tabColor = when {
                    tab.isRunning -> DarkBackground
                    tab.process != null -> DarkSurfaceVariant
                    else -> DarkError.copy(alpha = 0.2f)
                }

                Surface(
                    onClick = { onTabClick(tab.id) },
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    color = if (isActive) tabColor else DarkSurfaceVariant,
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp)
                    ) {
                        // Status dot
                        if (tab.isRunning) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = StatusRunning,
                                        shape = RoundedCornerShape(3.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isActive) DarkTextPrimary else DarkTextSecondary,
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
    onClear: () -> Unit,
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
            ToolbarButton("Clear", "Clear screen", onClick = onClear)
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

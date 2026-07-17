package com.terminalcode.app.ui.terminal

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalcode.app.ui.theme.*

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
                onNewTab = { viewModel.createNewTab() },
                onNewUbuntuTab = { viewModel.createNewTab(launchUbuntu = true) }
            )
        }

        // Terminal output area
        val activeTab = tabs.find { it.id == activeTabId }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(TerminalBackground)
        ) {
            when {
                activeTab == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No terminal session", color = DarkTextSecondary,
                            fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    }
                }
                !activeTab.isRunning && activeTab.outputLines.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Starting...", color = DarkTextSecondary,
                            fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    }
                }
                else -> {
                    TerminalOutput(
                        lines = activeTab.outputLines,
                        isRunning = activeTab.isRunning,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Input bar
        if (activeTab?.isRunning == true) {
            TerminalInputBar(
                onSend = { viewModel.writeInput(activeTabId, it) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Control toolbar
        TerminalToolbar(
            onCtrlC = viewModel::sendCtrlC,
            onCtrlD = viewModel::sendCtrlD,
            onTab = viewModel::sendTab,
            onClear = { viewModel.clearOutput(activeTabId) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TerminalOutput(
    lines: List<String>,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scrollState = rememberScrollState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .padding(8.dp)
            .horizontalScroll(scrollState)
    ) {
        items(lines) { line ->
            Text(
                text = parseAnsiText(line),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                ),
                modifier = Modifier.padding(vertical = 0.dp)
            )
        }
        if (isRunning) {
            item {
                Text("█",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp, lineHeight = 17.sp,
                        color = TerminalCursor
                    )
                )
            }
        }
    }
}

@Composable
private fun TerminalInputBar(
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf(TextFieldValue("")) }

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
            Text("$ ",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = TerminalGreen,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(end = 4.dp)
            )

            BasicTextField(
                value = input,
                onValueChange = { input = it },
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
                        val text = input.text
                        if (text.isNotEmpty()) {
                            onSend("$text\n")
                            input = TextFieldValue("")
                        }
                    }
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                decorationBox = { inner ->
                    Box {
                        if (input.text.isEmpty()) {
                            Text("Type a command...",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = DarkTextSecondary
                                )
                            )
                        }
                        inner()
                    }
                }
            )

            Spacer(modifier = Modifier.width(6.dp))

            Surface(
                onClick = {
                    val text = input.text
                    if (text.isNotEmpty()) {
                        onSend("$text\n")
                        input = TextFieldValue("")
                    }
                },
                shape = RoundedCornerShape(6.dp),
                color = DarkAccent
            ) {
                Text("↵",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
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
            ToolbarBtn("^C", "Interrupt", onCtrlC)
            ToolbarBtn("^D", "EOF", onCtrlD)
            ToolbarBtn("⇥", "Tab", onTab)
            ToolbarBtn("Clear", "Clear", onClear)
        }
    }
}

@Composable
private fun ToolbarBtn(text: String, desc: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = DarkSurfaceVariant
    ) {
        Text(text,
            style = MaterialTheme.typography.labelMedium,
            color = DarkTextPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TerminalTabBar(
    tabs: List<TerminalViewModel.TerminalTab>,
    activeTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onNewUbuntuTab: () -> Unit
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
                val bg = when {
                    isActive -> DarkBackground
                    else -> DarkSurfaceVariant
                }
                Surface(
                    onClick = { onTabClick(tab.id) },
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    color = bg,
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (tab.isRunning) StatusRunning
                                    else StatusStopped
                                )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (tab.isUbuntu) "Ubuntu" else tab.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isActive) DarkTextPrimary else DarkTextSecondary,
                            maxLines = 1
                        )
                        if (tabs.size > 1) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onTabClose(tab.id) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Default.Close, null,
                                    tint = DarkTextSecondary,
                                    modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // Ubuntu button
        Surface(
            onClick = onNewUbuntuTab,
            shape = RoundedCornerShape(6.dp),
            color = TerminalGreen.copy(alpha = 0.2f),
            modifier = Modifier.padding(end = 4.dp)
        ) {
            Text("Ubuntu",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TerminalGreen
                ),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        IconButton(onClick = onNewTab, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Add, "New tab",
                tint = DarkTextPrimary,
                modifier = Modifier.size(20.dp))
        }
    }
}

// ==================== ANSI Parser ====================

private fun parseAnsiText(text: String) = buildAnnotatedString {
    val default = DarkTextPrimary
    var color = default
    var bold = false
    var i = 0

    while (i < text.length) {
        if (text[i] == '\u001b' && i + 1 < text.length && text[i + 1] == '[') {
            val end = findAnsiEnd(text, i + 2)
            if (end == -1) { append(text.substring(i)); break }
            val cmd = text[end]
            if (cmd == 'm') {
                val params = text.substring(i + 2, end).split(";")
                for (p in params) {
                    when (p) {
                        "0", "" -> { color = default; bold = false }
                        "1" -> bold = true
                        "30", "37" -> color = DarkTextPrimary
                        "31" -> color = TerminalRed
                        "32", "92" -> color = TerminalGreen
                        "33", "93" -> color = TerminalYellow
                        "34", "94" -> color = TerminalBlue
                        "35", "95" -> color = TerminalMagenta
                        "36", "96" -> color = TerminalCyan
                        "90", "97" -> color = DarkTextPrimary
                        "91" -> color = TerminalRed
                    }
                }
            }
            i = end + 1
        } else {
            var end = i + 1
            while (end < text.length && !(text[end] == '\u001b' && end + 1 < text.length && text[end + 1] == '[')) {
                end++
            }
            withStyle(SpanStyle(color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)) {
                append(text.substring(i, end))
            }
            i = end
        }
    }
}

private fun findAnsiEnd(text: String, start: Int): Int {
    var i = start
    while (i < text.length) {
        val c = text[i]
        if (c in 'A'..'Z' || c in 'a'..'z') return i
        if (c in '0'..'9' || c == ';' || c == '?' || c == '=' || c == '<' || c == '>') i++
        else return -1
    }
    return -1
}

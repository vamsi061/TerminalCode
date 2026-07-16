package com.terminalcode.app.ui.editor

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.terminalcode.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Code editor screen with WebView-based Monaco Editor.
 *
 * Features:
 * - Multiple editor tabs
 * - Monaco Editor with syntax highlighting for 50+ languages
 * - File save/open support
 * - Tab management
 * - GitHub Dark theme
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val bridge by viewModel.editorBridge.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor(DarkBackground))
    ) {
        // Editor tab bar
        if (tabs.isNotEmpty()) {
            EditorTabBar(
                tabs = tabs,
                activeTabId = activeTabId,
                onTabClick = { viewModel.switchTab(it) },
                onTabClose = { viewModel.closeTab(it) },
                onNewTab = { viewModel.createNewTab() }
            )
        }

        // Monaco Editor WebView
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            tabs.forEach { tab ->
                if (tab.id == activeTabId) {
                    MonacoEditorWebView(
                        content = tab.content,
                        fileName = tab.fileName,
                        bridge = bridge,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Bottom status bar
        EditorStatusBar(
            bridge = bridge,
            tab = tabs.find { it.id == activeTabId },
            onSave = {
                scope.launch {
                    // Get content from editor and save
                    bridge // trigger recomposition
                }
            }
        )
    }
}

@Composable
private fun EditorTabBar(
    tabs: List<EditorViewModel.EditorTab>,
    activeTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ComposeColor(DarkSurface))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            tabs.forEach { tab ->
                val isActive = tab.id == activeTabId
                Surface(
                    onClick = { onTabClick(tab.id) },
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    color = if (isActive) ComposeColor(DarkBackground)
                    else ComposeColor(DarkSurfaceVariant),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp)
                    ) {
                        // File icon
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = ComposeColor(DarkTextSecondary),
                            modifier = Modifier.size(14.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // File name
                        Text(
                            text = tab.fileName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isActive) ComposeColor(DarkTextPrimary)
                            else ComposeColor(DarkTextSecondary),
                            maxLines = 1
                        )

                        // Modified indicator
                        if (tab.isModified) {
                            Text(
                                text = " ●",
                                color = ComposeColor(DarkWarning),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Close button
                        IconButton(
                            onClick = { onTabClose(tab.id) },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close tab",
                                tint = ComposeColor(DarkTextSecondary),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // New file button
        IconButton(
            onClick = onNewTab,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New file",
                tint = ComposeColor(DarkTextPrimary),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MonacoEditorWebView(
    content: String,
    fileName: String,
    bridge: com.terminalcode.app.editor.MonacoEditorBridge,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Configure WebView
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.javaScriptCanOpenWindowsAutomatically = false

                // Performance
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                settings.layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NARROW_COLUMNS
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true

                setBackgroundColor(Color.TRANSPARENT)

                // Add JavaScript interface
                addJavascriptInterface(bridge, com.terminalcode.app.editor.MonacoEditorBridge.INTERFACE_NAME)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Open the file content in Monaco
                        if (content.isNotEmpty()) {
                            evaluateJavascript(
                                bridge.getOpenFileScript(fileName, content),
                                null
                            )
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {}

                // Load Monaco Editor
                loadUrl("file:///android_asset/editor/index.html")
            }
        },
        modifier = modifier
    )
}

@Composable
private fun EditorStatusBar(
    bridge: com.terminalcode.app.editor.MonacoEditorBridge,
    tab: EditorViewModel.EditorTab?,
    onSave: () -> Unit
) {
    val cursorPosition by bridge.cursorPosition.collectAsState()
    val isModified by bridge.isModified.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ComposeColor(DarkSurface))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side: file info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // File name
            Text(
                text = tab?.fileName ?: "Untitled",
                style = MaterialTheme.typography.labelSmall,
                color = ComposeColor(DarkTextSecondary)
            )

            // Modified indicator
            if (isModified) {
                Text(
                    text = "Modified",
                    style = MaterialTheme.typography.labelSmall,
                    color = ComposeColor(DarkWarning)
                )
            }
        }

        // Right side: cursor position and actions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Cursor position
            Text(
                text = "Ln ${cursorPosition.first}, Col ${cursorPosition.second}",
                style = MaterialTheme.typography.labelSmall,
                color = ComposeColor(DarkTextSecondary)
            )

            // Language
            Text(
                text = tab?.language?.substringAfter("/")?.uppercase() ?: "TXT",
                style = MaterialTheme.typography.labelSmall,
                color = ComposeColor(DarkAccent)
            )
        }
    }
}

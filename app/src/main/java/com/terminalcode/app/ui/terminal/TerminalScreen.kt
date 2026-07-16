package com.terminalcode.app.ui.terminal

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
import com.terminalcode.app.terminal.TerminalWebViewBridge
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.terminalcode.app.ui.theme.*

/**
 * Terminal screen with WebView-based xterm.js terminal.
 *
 * Features:
 * - Multiple terminal tabs
 * - Tab management (add/close/switch)
 * - Full xterm.js terminal emulation
 * - GitHub Dark themed UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
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

        // Terminal WebView
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
        ) {
            tabs.forEach { tab ->
                if (tab.id == activeTabId) {
                    TerminalWebView(
                        tab = tab,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
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
        // Tab list (scrollable)
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
                        // Tab title
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isActive) DarkTextPrimary
                            else DarkTextSecondary,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Close button (not for last tab)
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TerminalWebView(
    tab: TerminalViewModel.TerminalTab,
    viewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    val bridge = tab.bridge ?: return

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isFocusable = true
                isFocusableInTouchMode = true

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                settings.layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NARROW_COLUMNS
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                setBackgroundColor(Color.TRANSPARENT)

                addJavascriptInterface(bridge, TerminalWebViewBridge.INTERFACE_NAME)

                // Show soft keyboard when terminal is tapped
                setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        v.requestFocus()
                        val imm = v.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                        // Focus xterm.js terminal
                        evaluateJavascript("setTimeout(() => { term.focus(); }, 100);", null)
                    }
                    true
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        viewModel.registerWebView(tab.id, this@apply)
                        requestFocus()
                        // Focus xterm.js terminal
                        evaluateJavascript("setTimeout(() => { term.focus(); }, 200);", null)
                        // Show keyboard
                        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(this@apply, InputMethodManager.SHOW_IMPLICIT)
                    }
                }

                webChromeClient = object : WebChromeClient() {}

                loadUrl("file:///android_asset/terminal/index.html")
            }
        },
        modifier = modifier
    )
}

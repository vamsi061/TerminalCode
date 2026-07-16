package com.terminalcode.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.terminalcode.app.ui.editor.EditorScreen
import com.terminalcode.app.ui.editor.EditorViewModel
import com.terminalcode.app.ui.files.FileManagerScreen
import com.terminalcode.app.ui.files.FileViewModel
import com.terminalcode.app.ui.terminal.TerminalScreen
import com.terminalcode.app.ui.terminal.TerminalViewModel
import com.terminalcode.app.ui.theme.*

/**
 * Main navigation screen with bottom navigation bar.
 *
 * Provides switching between:
 * - Terminal: Full xterm.js terminal with multi-tab support
 * - Editor: Monaco Editor for code editing
 * - Files: File browser with SAF integration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenFile: (Uri, String) -> Unit,
    terminalViewModel: TerminalViewModel = viewModel(),
    editorViewModel: EditorViewModel = viewModel(),
    fileViewModel: FileViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        containerColor = DarkBackground,
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(300)
            ) { tab ->
                when (tab) {
                    0 -> TerminalScreen(
                        viewModel = terminalViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> EditorScreen(
                        viewModel = editorViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    2 -> FileManagerScreen(
                        viewModel = fileViewModel,
                        onFileClick = { uri, name ->
                            editorViewModel.openFile(uri, name)
                            selectedTab = 1
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Bottom navigation bar with three tabs and a subtle separator line.
 */
@Composable
private fun BottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NavBarBackground,
        shadowElevation = 8.dp
    ) {
        // Top separator line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(NavBarBorder)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabItem(
                icon = Icons.Outlined.Terminal,
                selectedIcon = Icons.Default.Terminal,
                label = "Terminal",
                isSelected = selectedTab == 0,
                onClick = { onTabSelected(0) }
            )
            TabItem(
                icon = Icons.Outlined.Code,
                selectedIcon = Icons.Default.Code,
                label = "Editor",
                isSelected = selectedTab == 1,
                onClick = { onTabSelected(1) }
            )
            TabItem(
                icon = Icons.Outlined.Folder,
                selectedIcon = Icons.Default.Folder,
                label = "Files",
                isSelected = selectedTab == 2,
                onClick = { onTabSelected(2) }
            )
        }
    }
}

/**
 * Individual bottom navigation tab item with icon and label.
 */
@Composable
private fun TabItem(
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable { onClick() }
        ) {
        // Active indicator
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .background(
                        color = TabActiveIndicator,
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Icon
        Icon(
            imageVector = if (isSelected) selectedIcon else icon,
            contentDescription = label,
            tint = if (isSelected) TabActiveIndicator
            else TabInactive,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isSelected) TabActiveIndicator
            else TabInactive
        )
    }
}

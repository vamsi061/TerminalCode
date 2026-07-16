package com.terminalcode.app.ui.files

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.terminalcode.app.files.FileRepository
import com.terminalcode.app.ui.theme.*

/**
 * File browser screen using Android's Storage Access Framework.
 *
 * Features:
 * - Browse files and directories
 * - Pick root directory via SAF
 * - Navigate into subdirectories
 * - Create/delete files and folders
 * - Visual file type indicators
 * - GitHub Dark themed UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    viewModel: FileViewModel,
    onFileClick: (Uri, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.browserState.collectAsState()
    val context = LocalContext.current
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    // SAF directory picker launcher
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable permission
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            viewModel.setRootDirectory(it)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = it.lastPathSegment ?: "File"
            onFileClick(it, fileName)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Header with path and actions
        FileBrowserHeader(
            currentPath = state.currentPath,
            canGoBack = state.hasDirectoryAccess,
            onPickDirectory = { directoryPickerLauncher.launch(null) },
            onPickFile = {
                filePickerLauncher.launch(arrayOf("*/*"))
            },
            onNewFile = { showNewFileDialog = true },
            onNewFolder = { showNewFolderDialog = true },
            onRefresh = { viewModel.refreshCurrentDirectory() }
        )

        // Storage access prompt if no directory selected
        if (!state.hasDirectoryAccess) {
            StorageAccessPrompt(
                onPickDirectory = { directoryPickerLauncher.launch(null) }
            )
        } else if (state.isLoading) {
            // Loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = DarkAccent,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else {
            // File list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                // Navigate back item
                if (state.currentDirectoryUri != null) {
                    item {
                        FileListItem(
                            icon = Icons.Default.ArrowBack,
                            name = "..",
                            subtitle = "Parent directory",
                            isDirectory = true,
                            onClick = { viewModel.navigateBack() }
                        )
                    }
                }

                items(state.files, key = { it.uri.toString() }) { file ->
                    FileListItem(
                        icon = getFileIcon(file.name, file.isDirectory),
                        name = file.name,
                        subtitle = buildString {
                            if (file.isDirectory) append("Directory")
                            else append(formatFileSize(file.size))
                            if (file.lastModified > 0) {
                                append(" • ")
                                append(formatDate(file.lastModified))
                            }
                        },
                        isDirectory = file.isDirectory,
                        onClick = {
                            if (file.isDirectory) {
                                viewModel.navigateToDirectory(file.uri, file.name)
                            } else {
                                onFileClick(file.uri, file.name)
                            }
                        }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showNewFileDialog) {
        CreateFileDialog(
            title = "New File",
            onDismiss = { showNewFileDialog = false },
            onCreate = { name ->
                state.currentDirectoryUri?.let { viewModel.createFile(name) }
                showNewFileDialog = false
            }
        )
    }

    if (showNewFolderDialog) {
        CreateFileDialog(
            title = "New Folder",
            onDismiss = { showNewFolderDialog = false },
            onCreate = { name ->
                state.currentDirectoryUri?.let { viewModel.createDirectory(name) }
                showNewFolderDialog = false
            }
        )
    }
}

@Composable
private fun FileBrowserHeader(
    currentPath: String,
    canGoBack: Boolean,
    onPickDirectory: () -> Unit,
    onPickFile: () -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(8.dp)
    ) {
        // Path display
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = DarkWarning,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = currentPath,
                style = MaterialTheme.typography.titleSmall,
                color = DarkTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Open directory
            FilledTonalButton(
                onClick = onPickDirectory,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = DarkSurfaceVariant,
                    contentColor = DarkTextPrimary
                ),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Open", style = MaterialTheme.typography.labelSmall)
            }

            // New file
            FilledTonalButton(
                onClick = onNewFile,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = DarkSurfaceVariant,
                    contentColor = DarkTextPrimary
                ),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    Icons.Default.NoteAdd,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("File", style = MaterialTheme.typography.labelSmall)
            }

            // New folder
            FilledTonalButton(
                onClick = onNewFolder,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = DarkSurfaceVariant,
                    contentColor = DarkTextPrimary
                ),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Folder", style = MaterialTheme.typography.labelSmall)
            }

            // Refresh
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = DarkTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StorageAccessPrompt(
    onPickDirectory: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = DarkTextSecondary,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Directory Selected",
                style = MaterialTheme.typography.titleLarge,
                color = DarkTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap the button below to select a working directory.\nTerminalCode will have read/write access to it.",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onPickDirectory,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkAccent,
                    contentColor    = Color.White
                )
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Directory")
            }
        }
    }
}

@Composable
private fun FileListItem(
    icon: ImageVector,
    name: String,
    subtitle: String,
    isDirectory: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = if (isDirectory) DarkAccentMuted.copy(alpha = 0.15f)
                    else DarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDirectory) DarkWarning
                else DarkAccent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = DarkTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = DarkTextSecondary,
                maxLines = 1
            )
        }

        // Chevron for directories
        if (isDirectory) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = DarkTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CreateFileDialog(
    title: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var fileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = DarkTextPrimary,
        textContentColor = DarkTextSecondary,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("Name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccent,
                    unfocusedBorderColor = DarkBorder,
                    focusedLabelColor = DarkAccent,
                    cursorColor = DarkAccent,
                    focusedTextColor = DarkTextPrimary,
                    unfocusedTextColor = DarkTextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(fileName) },
                enabled = fileName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkAccent
                )
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DarkTextSecondary)
            }
        }
    )
}

// ============================================================
// Helper Functions
// ============================================================

/**
 * Returns the appropriate icon for a file based on its name and type.
 */
private fun getFileIcon(fileName: String, isDirectory: Boolean): ImageVector {
    if (isDirectory) return Icons.Default.Folder
    val ext = fileName.substringAfterLast('.').lowercase()
    return when (ext) {
        "js", "ts", "tsx" -> Icons.Default.Code
        "py", "kt", "kts", "java" -> Icons.Default.Code
        "html", "css", "xml", "json" -> Icons.Default.Code
        "md" -> Icons.Default.Article
        "txt" -> Icons.Default.TextSnippet
        "sh", "bash" -> Icons.Default.Terminal
        "png", "jpg", "jpeg", "gif" -> Icons.Default.Image
        "pdf" -> Icons.Default.PictureAsPdf
        "zip", "rar", "tar", "gz" -> Icons.Default.FolderZip
        else -> Icons.Default.Description
    }
}

/**
 * Formats file size in human-readable format.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }
}

/**
 * Formats a timestamp as a readable date string.
 */
private fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

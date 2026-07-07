package com.bitzcodes.bitzcodes.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitzcodes.bitzcodes.data.FileItem
import com.bitzcodes.bitzcodes.ui.ChatViewModel
import com.bitzcodes.bitzcodes.ui.common.AdMobBanner
import com.bitzcodes.bitzcodes.ui.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: ChatViewModel) {
    val activeWorkspace by viewModel.activeWorkspace.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.currentFiles.collectAsState()
    
    val openFilePath by viewModel.openFilePath.collectAsState()
    val openFileContent by viewModel.openFileContent.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var createIsDirectory by remember { mutableStateOf(false) }

    var showRenameDialogFor by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteDialogFor by remember { mutableStateOf<FileItem?>(null) }

    if (activeWorkspace == null) {
        EmptyState(
            title = "No Active Workspace Selected",
            description = "Go to the Workspace tab and select a workspace to browse and edit files."
        )
        return
    }

    if (openFilePath != null && openFileContent != null) {
        // File Editor Mode
        CodeEditorMode(
            fileName = openFilePath!!.substringAfterLast("/"),
            relativePath = openFilePath!!,
            initialContent = openFileContent!!,
            onBack = { 
                viewModel.closeOpenFile()
                viewModel.refreshFiles()
            },
            onSave = { content ->
                viewModel.saveFile(openFilePath!!, content)
            }
        )
    } else {
        // File Explorer Mode
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(activeWorkspace!!.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = if (currentPath.isEmpty()) "/" else "/$currentPath",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        if (currentPath.isNotEmpty()) {
                            IconButton(onClick = {
                                val parts = currentPath.split("/").filter { it.isNotEmpty() }
                                val parent = parts.dropLast(1).joinToString("/")
                                viewModel.browseTo(parent)
                            }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            createIsDirectory = false
                            showCreateDialog = true
                        }) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "New File")
                        }
                        IconButton(onClick = {
                            createIsDirectory = true
                            showCreateDialog = true
                        }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                        }
                        IconButton(onClick = { viewModel.refreshFiles() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AdMobBanner()

                if (files.isEmpty()) {
                    EmptyState(
                        title = "Empty Directory",
                        description = "This folder contains no files. Tap the icons above to create files or subdirectories."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                    ) {
                        items(files, key = { it.relativePath }) { item ->
                            FileListItem(
                                item = item,
                                onClick = {
                                    if (item.isDirectory) {
                                        viewModel.browseTo(item.relativePath)
                                    } else {
                                        viewModel.openFile(item.relativePath)
                                    }
                                },
                                onRename = { showRenameDialogFor = item },
                                onDelete = { showDeleteDialogFor = item }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create File / Folder Dialog
    if (showCreateDialog) {
        var nameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (createIsDirectory) "Create Folder" else "Create File", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Name") },
                    placeholder = { Text(if (createIsDirectory) "my-folder" else "MainActivity.kt") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.trim().isNotEmpty()) {
                            viewModel.createFile(nameInput.trim(), createIsDirectory)
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialogFor != null) {
        val item = showRenameDialogFor!!
        var renameInput by remember { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialogFor = null },
            title = { Text("Rename ${item.name}", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.trim().isNotEmpty() && renameInput != item.name) {
                            viewModel.renameFile(item.relativePath, renameInput.trim())
                            showRenameDialogFor = null
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation
    if (showDeleteDialogFor != null) {
        val item = showDeleteDialogFor!!
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            title = { Text("Delete ${item.name}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to permanently delete this ${if (item.isDirectory) "folder and all its contents" else "file"}? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFile(item.relativePath)
                        showDeleteDialogFor = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    item: FileItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { expandedMenu = true }
            ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                if (!item.isDirectory) {
                    Text(
                        text = "${item.size} bytes",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
            Box {
                IconButton(onClick = { expandedMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = expandedMenu,
                    onDismissRequest = { expandedMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            expandedMenu = false
                            onRename()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            expandedMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorMode(
    fileName: String,
    relativePath: String,
    initialContent: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit
) {
    var content by remember { mutableStateOf(initialContent) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }

    val visualTransformation = remember(fileName) {
        VisualTransformation { text ->
            val highlighted = SyntaxHighlighter.highlight(text.text, fileName)
            TransformedText(highlighted, OffsetMapping.Identity)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(fileName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = if (hasUnsavedChanges) "Unsaved changes" else "Saved",
                            fontSize = 11.sp,
                            color = if (hasUnsavedChanges) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // Resetting state in VM by triggering folder refresh
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close Editor")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onSave(content)
                            hasUnsavedChanges = false
                        },
                        enabled = hasUnsavedChanges
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Save file",
                            tint = if (hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AdMobBanner()

            TextField(
                value = content,
                onValueChange = {
                    content = it
                    hasUnsavedChanges = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                visualTransformation = visualTransformation,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
    }
}

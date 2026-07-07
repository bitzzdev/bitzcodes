package com.bitzcodes.bitzcodes.ui.workspace

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.documentfile.provider.DocumentFile
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitzcodes.bitzcodes.domain.model.Workspace
import com.bitzcodes.bitzcodes.ui.ChatViewModel
import com.bitzcodes.bitzcodes.ui.common.AdMobBanner
import com.bitzcodes.bitzcodes.ui.common.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val workspaces by viewModel.workspaces.collectAsState()
    val activeWorkspace by viewModel.activeWorkspace.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showRulesDialogFor by remember { mutableStateOf<Workspace?>(null) }

    // SAF Document Tree Launcher
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                // Grant persistable permission
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val lastPathSegment = uri.lastPathSegment ?: "SAF-Folder"
            val folderName = lastPathSegment.substringAfterLast(":")
            viewModel.addWorkspace(
                name = folderName.ifEmpty { "SAF Workspace" },
                path = uri.toString(),
                isSaf = true
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BITZCODES Workspaces", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Workspace")
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

            if (workspaces.isEmpty()) {
                EmptyState(
                    title = "No Workspaces Added",
                    description = "Add a local folder or select a directory using Storage Access Framework (SAF) to start coding.",
                    actionButton = {
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Workspace")
                        }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(workspaces, key = { it.id }) { workspace ->
                        WorkspaceItem(
                            workspace = workspace,
                            isActive = workspace.id == activeWorkspace?.id,
                            onSelect = { viewModel.selectWorkspace(workspace) },
                            onDelete = { viewModel.deleteWorkspace(workspace) },
                            onEditRules = { showRulesDialogFor = workspace }
                        )
                    }
                }
            }
        }
    }

    // Add Workspace Dialog
    if (showAddDialog) {
        var localPath by remember { mutableStateOf("") }
        var workspaceName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Workspace", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "You can choose a directory using SAF (recommended for Android 10+) or enter a local path (for sandbox/emulator environments).",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            safLauncher.launch(null)
                            showAddDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Explore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Folder via SAF (Document Picker)")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("Or enter a local absolute path:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)

                    OutlinedTextField(
                        value = workspaceName,
                        onValueChange = { workspaceName = it },
                        label = { Text("Workspace Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = localPath,
                        onValueChange = { localPath = it },
                        label = { Text("Local Folder Path") },
                        placeholder = { Text("/sdcard/Download/my-project") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (localPath.isNotEmpty() && workspaceName.isNotEmpty()) {
                            viewModel.addWorkspace(workspaceName, localPath, isSaf = false)
                            showAddDialog = false
                        }
                    },
                    enabled = localPath.isNotEmpty() && workspaceName.isNotEmpty()
                ) {
                    Text("Add Local Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Workspace Specific Rules Dialog
    if (showRulesDialogFor != null) {
        val workspace = showRulesDialogFor!!
        var rulesText by remember { mutableStateOf(workspace.rules) }

        AlertDialog(
            onDismissRequest = { showRulesDialogFor = null },
            title = { Text("Rules: ${workspace.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Specify custom instructions that the AI should follow when this workspace is active.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = rulesText,
                        onValueChange = { rulesText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = { Text("e.g. Always write comments in Spanish; Use library X instead of Y") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateWorkspaceRules(workspace, rulesText)
                        showRulesDialogFor = null
                    }
                ) {
                    Text("Save Rules")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRulesDialogFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WorkspaceItem(
    workspace: Workspace,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onEditRules: () -> Unit
) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val bgBrush = if (isActive) {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surface
            )
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .background(bgBrush),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (workspace.isSafUri) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workspace.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = workspace.path,
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1
                )
            }

            Row {
                IconButton(onClick = onEditRules) {
                    Icon(
                        Icons.Default.SettingsSuggest,
                        contentDescription = "Edit Rules",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Workspace",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

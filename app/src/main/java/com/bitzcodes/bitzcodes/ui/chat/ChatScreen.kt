package com.bitzcodes.bitzcodes.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitzcodes.bitzcodes.domain.model.AIProvider
import com.bitzcodes.bitzcodes.domain.model.ChatMessage
import com.bitzcodes.bitzcodes.domain.model.Conversation
import com.bitzcodes.bitzcodes.domain.model.ToolCall
import com.bitzcodes.bitzcodes.ui.ChatViewModel
import com.bitzcodes.bitzcodes.ui.common.AdMobBanner
import com.bitzcodes.bitzcodes.ui.common.EmptyState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val activeWorkspace by viewModel.activeWorkspace.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val activeConversation by viewModel.activeConversation.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val streamingMsg by viewModel.streamingMessage.collectAsState()
    val pendingTools by viewModel.pendingToolCalls.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showHistoryDrawer by remember { mutableStateOf(false) }
    var showProviderDialog by remember { mutableStateOf(false) }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(activeConversation?.messages?.size, streamingMsg, pendingTools) {
        val totalItems = (activeConversation?.messages?.size ?: 0) + 
                (if (streamingMsg != null) 1 else 0) + 
                (if (pendingTools.isNotEmpty()) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    if (activeWorkspace == null) {
        EmptyState(
            title = "No Active Workspace Selected",
            description = "Go to the Workspace tab and select a workspace first to enable the AI coding assistant."
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeConversation?.title ?: "Chat Assistant",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        selectedProvider?.let {
                            Text(
                                text = "Model: ${it.name} (${it.model})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showHistoryDrawer = true }) {
                        Icon(Icons.Default.History, contentDescription = "Conversation History")
                    }
                },
                actions = {
                    IconButton(onClick = { showProviderDialog = true }) {
                        Icon(Icons.Default.SmartToy, contentDescription = "Select AI Provider")
                    }
                    if (isStreaming) {
                        IconButton(onClick = { viewModel.stopStreaming() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AdMobBanner()

                // Messages list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
                ) {
                    val messages = activeConversation?.messages ?: emptyList()
                    
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg)
                    }

                    // Render active stream message
                    if (streamingMsg != null) {
                        item(key = "streaming_message") {
                            MessageBubble(message = streamingMsg!!)
                        }
                    }

                    // Pending tool calls needing approval
                    if (pendingTools.isNotEmpty()) {
                        item(key = "pending_tools") {
                            PendingToolsCard(
                                tools = pendingTools,
                                onApprove = { viewModel.approveToolCall(it) },
                                onReject = { viewModel.rejectToolCall(it) }
                            )
                        }
                    }
                }

                // Input Bar
                ChatInputBar(
                    isStreaming = isStreaming,
                    onSendMessage = { text ->
                        viewModel.sendMessage(text)
                    }
                )
            }

            // History Drawer Modal Overlay
            if (showHistoryDrawer) {
                HistoryDrawerOverlay(
                    conversations = conversations,
                    activeConv = activeConversation,
                    onDismiss = { showHistoryDrawer = false },
                    onSelect = {
                        viewModel.selectConversation(it)
                        showHistoryDrawer = false
                    },
                    onDelete = { viewModel.deleteConversation(it) },
                    onNewChat = {
                        viewModel.createNewConversation("New Chat")
                        showHistoryDrawer = false
                    }
                )
            }

            // Provider Configuration / Selector Dialog
            if (showProviderDialog) {
                ProviderSelectorDialog(
                    providers = providers,
                    selectedProvider = selectedProvider,
                    onSelect = {
                        viewModel.selectProvider(it.id)
                        showProviderDialog = false
                    },
                    onSaveConfig = { provider ->
                        viewModel.updateProviderConfig(provider)
                    },
                    onDismiss = { showProviderDialog = false }
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val isTool = message.role == "tool"
    val isSystem = message.role == "system"

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = when {
            isUser -> Alignment.CenterEnd
            isSystem -> Alignment.Center
            else -> Alignment.CenterStart
        }
    ) {
        val cardColor = when {
            isUser -> MaterialTheme.colorScheme.primary
            isTool -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            isSystem -> Color.Transparent
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }

        val textColor = when {
            isUser -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurface
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(if (isSystem) 0.9f else 0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(cardColor)
                .padding(12.dp)
        ) {
            if (isSystem) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                // Header (e.g. role identifier for tool or thinking indicator)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (message.role) {
                            "user" -> "You"
                            "tool" -> "Tool Call Result"
                            else -> "BITZCODES AI"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Optional collapsible thinking processes
                if (message.isThinking) {
                    var expanded by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded },
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Thinking Process...", fontSize = 11.sp, fontStyle = FontStyle.Italic)
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            AnimatedVisibility(visible = expanded) {
                                Text(
                                    text = message.thinkingContent ?: "Processing...",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Message Text / Tool JSON
                SelectionContainer {
                    Text(
                        text = message.content,
                        fontSize = 14.sp,
                        color = textColor,
                        fontFamily = if (isTool) FontFamily.Monospace else FontFamily.Default
                    )
                }

                // If tool calls were made by this message
                message.toolCalls?.let { calls ->
                    if (calls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        calls.forEach { call ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Construction, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Called: ${call.name}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PendingToolsCard(
    tools: List<ToolCall>,
    onApprove: (ToolCall) -> Unit,
    onReject: (ToolCall) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tool Call Approval Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "The assistant wants to execute the following workspace tools:",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            tools.forEach { call ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Tool: ${call.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = call.arguments,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onReject(call) }) {
                                Text("Reject", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { onApprove(call) }) {
                                Text("Approve")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    isStreaming: Boolean,
    onSendMessage: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Ask Bitzcodes AI or edit files...") },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            FloatingActionButton(
                onClick = {
                    if (text.trim().isNotEmpty() && !isStreaming) {
                        onSendMessage(text)
                        text = ""
                    }
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.size(48.dp),
                containerColor = if (text.trim().isEmpty() || isStreaming) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.primary
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun HistoryDrawerOverlay(
    conversations: List<Conversation>,
    activeConv: Conversation?,
    onDismiss: () -> Unit,
    onSelect: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    onNewChat: () -> Unit
) {
    // Backdrop shadow click-dismiss
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        // Drawer content
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.75f)
                .clickable(enabled = false) {}, // Prevent click propagation
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chat History", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onNewChat,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Chat")
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(conversations, key = { it.id }) { conv ->
                        val isSelected = conv.id == activeConv?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable { onSelect(conv) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = conv.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = { onDelete(conv) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderSelectorDialog(
    providers: List<AIProvider>,
    selectedProvider: AIProvider?,
    onSelect: (AIProvider) -> Unit,
    onSaveConfig: (AIProvider) -> Unit,
    onDismiss: () -> Unit
) {
    var editProvider by remember { mutableStateOf<AIProvider?>(null) }

    if (editProvider != null) {
        ProviderConfigDialog(
            provider = editProvider!!,
            onSave = {
                onSaveConfig(it)
                editProvider = null
            },
            onDismiss = { editProvider = null }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Select AI Provider", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(providers) { p ->
                        val isSelected = p.id == selectedProvider?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else Color.Transparent
                                )
                                .clickable { onSelect(p) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text(p.model, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editProvider = p }) {
                                Icon(Icons.Default.Edit, contentDescription = "Configure", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun ProviderConfigDialog(
    provider: AIProvider,
    onSave: (AIProvider) -> Unit,
    onDismiss: () -> Unit
) {
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var model by remember { mutableStateOf(provider.model) }
    var temp by remember { mutableStateOf(provider.temperature) }
    var thinking by remember { mutableStateOf(provider.isThinkingMode) }
    var autoApprove by remember { mutableStateOf(provider.autoApproveTools) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure: ${provider.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Temperature Slider
                Column {
                    Text("Temperature: ${String.format("%.2f", temp)}", fontSize = 12.sp)
                    Slider(
                        value = temp,
                        onValueChange = { temp = it },
                        valueRange = 0f..2f
                    )
                }

                // Checkboxes
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = thinking, onCheckedChange = { thinking = it })
                    Text("Enable Thinking Mode", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoApprove, onCheckedChange = { autoApprove = it })
                    Text("Auto Approve Tool Calls", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        provider.copy(
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            model = model,
                            temperature = temp,
                            isThinkingMode = thinking,
                            autoApproveTools = autoApprove
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Simple SelectionContainer stub if SelectionContainer is not importable directly
@Composable
fun SelectionContainer(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        content()
    }
}

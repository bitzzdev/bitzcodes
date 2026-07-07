package com.bitzcodes.bitzcodes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bitzcodes.bitzcodes.data.ApiClient
import com.bitzcodes.bitzcodes.data.FileItem
import com.bitzcodes.bitzcodes.data.FileResolver
import com.bitzcodes.bitzcodes.data.TelemetryClient
import com.bitzcodes.bitzcodes.domain.model.*
import com.bitzcodes.bitzcodes.tool.registry.ToolRegistry
import com.bitzcodes.bitzcodes.util.StateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val stateStore = StateStore(application)
    private val fileResolver = FileResolver(application)
    private val apiClient = ApiClient()
    private val telemetryClient = TelemetryClient()
    private val toolRegistry = ToolRegistry(application, fileResolver)

    // State flows
    private val _workspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val workspaces = _workspaces.asStateFlow()

    private val _activeWorkspace = MutableStateFlow<Workspace?>(null)
    val activeWorkspace = _activeWorkspace.asStateFlow()

    private val _providers = MutableStateFlow<List<AIProvider>>(emptyList())
    val providers = _providers.asStateFlow()

    private val _selectedProvider = MutableStateFlow<AIProvider?>(null)
    val selectedProvider = _selectedProvider.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations = _conversations.asStateFlow()

    private val _activeConversation = MutableStateFlow<Conversation?>(null)
    val activeConversation = _activeConversation.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings = _settings.asStateFlow()

    // File Explorer State
    private val _currentPath = MutableStateFlow("")
    val currentPath = _currentPath.asStateFlow()

    private val _currentFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val currentFiles = _currentFiles.asStateFlow()

    private val _openFileContent = MutableStateFlow<String?>(null)
    val openFileContent = _openFileContent.asStateFlow()

    private val _openFilePath = MutableStateFlow<String?>(null)
    val openFilePath = _openFilePath.asStateFlow()

    // Chat / Stream / Tool State
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming = _isStreaming.asStateFlow()

    private val _streamingMessage = MutableStateFlow<ChatMessage?>(null)
    val streamingMessage = _streamingMessage.asStateFlow()

    private val _pendingToolCalls = MutableStateFlow<List<ToolCall>>(emptyList())
    val pendingToolCalls = _pendingToolCalls.asStateFlow()

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            val loadedSettings = stateStore.loadSettings()
            _settings.value = loadedSettings

            val loadedWorkspaces = stateStore.loadWorkspaces()
            _workspaces.value = loadedWorkspaces

            val loadedProviders = stateStore.loadProviders()
            _providers.value = loadedProviders

            // Resolve active workspace
            val activeW = loadedWorkspaces.find { it.id == loadedSettings.activeWorkspaceId }
                ?: loadedWorkspaces.firstOrNull()
            _activeWorkspace.value = activeW

            // Resolve selected provider
            val activeP = loadedProviders.find { it.id == loadedSettings.selectedProviderId }
                ?: loadedProviders.firstOrNull()
            _selectedProvider.value = activeP

            // Load conversations for active workspace
            loadConversationsForWorkspace(activeW?.id)

            // Resolve active conversation
            val loadedConversations = _conversations.value
            val activeC = loadedConversations.find { it.id == loadedSettings.activeConversationId }
                ?: loadedConversations.firstOrNull()
            _activeConversation.value = activeC

            // Load files for workspace
            if (activeW != null) {
                refreshFiles()
            }
        }
    }

    // Workspaces
    fun addWorkspace(name: String, path: String, isSaf: Boolean) {
        viewModelScope.launch {
            val newW = Workspace(
                id = UUID.randomUUID().toString(),
                name = name,
                path = path,
                isSafUri = isSaf
            )
            val updated = _workspaces.value + newW
            _workspaces.value = updated
            stateStore.saveWorkspaces(updated)
            selectWorkspace(newW)
            telemetryClient.logWorkspaceAdded(newW)
        }
    }

    fun selectWorkspace(workspace: Workspace?) {
        viewModelScope.launch {
            _activeWorkspace.value = workspace
            _currentPath.value = ""
            _openFilePath.value = null
            _openFileContent.value = null
            
            // Save state
            val updatedSettings = _settings.value.copy(activeWorkspaceId = workspace?.id)
            _settings.value = updatedSettings
            stateStore.saveSettings(updatedSettings)

            loadConversationsForWorkspace(workspace?.id)
            refreshFiles()
            
            // Select first conversation or auto-create one
            if (_conversations.value.isEmpty() && workspace != null) {
                createNewConversation("New Conversation")
            } else {
                _activeConversation.value = _conversations.value.firstOrNull()
            }
        }
    }

    fun updateWorkspaceRules(workspace: Workspace, rules: String) {
        viewModelScope.launch {
            val updatedList = _workspaces.value.map {
                if (it.id == workspace.id) it.copy(rules = rules) else it
            }
            _workspaces.value = updatedList
            stateStore.saveWorkspaces(updatedList)
            if (_activeWorkspace.value?.id == workspace.id) {
                _activeWorkspace.value = _activeWorkspace.value?.copy(rules = rules)
            }
        }
    }

    fun deleteWorkspace(workspace: Workspace) {
        viewModelScope.launch {
            val updated = _workspaces.value.filter { it.id != workspace.id }
            _workspaces.value = updated
            stateStore.saveWorkspaces(updated)
            if (_activeWorkspace.value?.id == workspace.id) {
                selectWorkspace(updated.firstOrNull())
            }
        }
    }

    // App Settings
    fun saveSettings(globalRules: String, themeMode: String) {
        viewModelScope.launch {
            val updatedSettings = _settings.value.copy(
                globalRules = globalRules,
                themeMode = themeMode
            )
            _settings.value = updatedSettings
            stateStore.saveSettings(updatedSettings)
        }
    }

    // Providers
    fun selectProvider(providerId: String) {
        viewModelScope.launch {
            val provider = _providers.value.find { it.id == providerId }
            _selectedProvider.value = provider
            
            val updatedSettings = _settings.value.copy(selectedProviderId = providerId)
            _settings.value = updatedSettings
            stateStore.saveSettings(updatedSettings)
        }
    }

    fun updateProviderConfig(provider: AIProvider) {
        viewModelScope.launch {
            val updatedList = _providers.value.map {
                if (it.id == provider.id) provider else it
            }
            _providers.value = updatedList
            stateStore.saveProviders(updatedList)
            if (_selectedProvider.value?.id == provider.id) {
                _selectedProvider.value = provider
            }
        }
    }

    // Conversations
    private fun loadConversationsForWorkspace(workspaceId: String?) {
        val loaded = stateStore.loadConversations(workspaceId)
        _conversations.value = loaded
    }

    fun createNewConversation(title: String) {
        viewModelScope.launch {
            val workspace = _activeWorkspace.value ?: return@launch
            val newC = Conversation(
                id = UUID.randomUUID().toString(),
                title = title,
                workspaceId = workspace.id
            )
            _activeConversation.value = newC
            stateStore.saveConversation(newC)
            loadConversationsForWorkspace(workspace.id)

            val updatedSettings = _settings.value.copy(activeConversationId = newC.id)
            _settings.value = updatedSettings
            stateStore.saveSettings(updatedSettings)
        }
    }

    fun selectConversation(conversation: Conversation) {
        viewModelScope.launch {
            _activeConversation.value = conversation
            val updatedSettings = _settings.value.copy(activeConversationId = conversation.id)
            _settings.value = updatedSettings
            stateStore.saveSettings(updatedSettings)
        }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            stateStore.deleteConversation(conversation.id)
            loadConversationsForWorkspace(_activeWorkspace.value?.id)
            if (_activeConversation.value?.id == conversation.id) {
                _activeConversation.value = _conversations.value.firstOrNull()
            }
        }
    }

    // File Explorer
    fun refreshFiles() {
        val workspace = _activeWorkspace.value ?: return
        _currentFiles.value = fileResolver.listFiles(workspace, _currentPath.value)
    }

    fun browseTo(relativePath: String) {
        _currentPath.value = relativePath
        refreshFiles()
    }

    fun openFile(relativePath: String) {
        val workspace = _activeWorkspace.value ?: return
        viewModelScope.launch {
            val content = fileResolver.readFile(workspace, relativePath)
            _openFilePath.value = relativePath
            _openFileContent.value = content
        }
    }

    fun closeOpenFile() {
        _openFilePath.value = null
        _openFileContent.value = null
    }

    fun saveFile(relativePath: String, content: String) {
        val workspace = _activeWorkspace.value ?: return
        viewModelScope.launch {
            fileResolver.writeFile(workspace, relativePath, content)
            if (_openFilePath.value == relativePath) {
                _openFileContent.value = content
            }
            refreshFiles()
        }
    }

    fun createFile(name: String, isDir: Boolean) {
        val workspace = _activeWorkspace.value ?: return
        viewModelScope.launch {
            fileResolver.createFile(workspace, _currentPath.value, name, isDir)
            refreshFiles()
        }
    }

    fun deleteFile(relativePath: String) {
        val workspace = _activeWorkspace.value ?: return
        viewModelScope.launch {
            fileResolver.deleteFile(workspace, relativePath)
            if (_openFilePath.value == relativePath) {
                _openFilePath.value = null
                _openFileContent.value = null
            }
            refreshFiles()
        }
    }

    fun renameFile(relativePath: String, newName: String) {
        val workspace = _activeWorkspace.value ?: return
        viewModelScope.launch {
            fileResolver.renameFile(workspace, relativePath, newName)
            if (_openFilePath.value == relativePath) {
                // Adjust open file relative path
                val parts = relativePath.split("/")
                val parentPath = parts.dropLast(1).joinToString("/")
                val adjustedPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
                _openFilePath.value = adjustedPath
            }
            refreshFiles()
        }
    }

    // Messaging & Agentic Execution Loop
    fun sendMessage(text: String) {
        val conversation = _activeConversation.value ?: return
        val provider = _selectedProvider.value ?: return
        val workspace = _activeWorkspace.value ?: return

        viewModelScope.launch {
            _isStreaming.value = true

            // 1. Add User Message
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "user",
                content = text
            )
            val updatedMessages = conversation.messages + userMsg
            val updatedConv = conversation.copy(messages = updatedMessages)
            _activeConversation.value = updatedConv
            stateStore.saveConversation(updatedConv)

            telemetryClient.logChatSent(provider.name, hasTools = false)

            runAgenticLoop(updatedConv, provider, workspace)
        }
    }

    private suspend fun runAgenticLoop(conversation: Conversation, provider: AIProvider, workspace: Workspace) {
        var currentConversation = conversation
        var loopCount = 0
        val maxLoops = 5

        while (_isStreaming.value && loopCount < maxLoops) {
            loopCount++

            // Generate System Prompt including instructions and active rules
            val rulesText = buildString {
                if (_settings.value.globalRules.isNotEmpty()) {
                    append("## Global AI Rules:\n")
                    append(_settings.value.globalRules)
                    append("\n\n")
                }
                if (workspace.rules.isNotEmpty()) {
                    append("## Workspace specific rules:\n")
                    append(workspace.rules)
                    append("\n\n")
                }
            }
            val systemPrompt = toolRegistry.getSystemInstructions() + "\n" + rulesText

            // Setup a streaming placeholder message
            val assistantMsgId = UUID.randomUUID().toString()
            _streamingMessage.value = ChatMessage(
                id = assistantMsgId,
                role = "assistant",
                content = "",
                isThinking = provider.isThinkingMode
            )

            // Streaming API Call
            val streamResult: Pair<String, List<ToolCall>> = try {
                apiClient.streamChat(provider, systemPrompt, currentConversation) { chunk ->
                    // Accumulate streaming message
                    val currentStream = _streamingMessage.value
                    if (currentStream != null) {
                        _streamingMessage.value = currentStream.copy(
                            content = currentStream.content + chunk
                        )
                    }
                }
            } catch (e: Exception) {
                // Add error message to chat and stop
                val errorMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "Error calling AI: ${e.message}"
                )
                val finalConv = currentConversation.copy(messages = currentConversation.messages + errorMsg)
                _activeConversation.value = finalConv
                stateStore.saveConversation(finalConv)
                _streamingMessage.value = null
                _isStreaming.value = false
                return
            }

            val finalContent = streamResult.first
            val toolCalls = streamResult.second

            // Save the finished assistant response
            val completedAssistantMsg = ChatMessage(
                id = assistantMsgId,
                role = "assistant",
                content = finalContent,
                toolCalls = toolCalls
            )

            currentConversation = currentConversation.copy(messages = currentConversation.messages + completedAssistantMsg)
            _activeConversation.value = currentConversation
            stateStore.saveConversation(currentConversation)
            _streamingMessage.value = null

            // If there are tool calls, execute or prompt user
            if (toolCalls.isNotEmpty()) {
                if (provider.autoApproveTools) {
                    // Auto approve all
                    val results = toolCalls.map { call ->
                        _isStreaming.value = true
                        call.status = "executing"
                        val resultText = toolRegistry.executeTool(call.name, call.arguments, workspace)
                        call.status = "executed"
                        call.result = resultText
                        
                        telemetryClient.logToolExecuted(call.name, "auto_approved")

                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = "tool",
                            content = resultText,
                            toolCallId = call.id
                        )
                    }
                    currentConversation = currentConversation.copy(messages = currentConversation.messages + results)
                    _activeConversation.value = currentConversation
                    stateStore.saveConversation(currentConversation)
                } else {
                    // Manual approval needed - halt stream loop and wait
                    _pendingToolCalls.value = toolCalls
                    _isStreaming.value = false
                    return
                }
            } else {
                // Loop completes naturally (no more tool calls)
                _isStreaming.value = false
                break
            }
        }
        _isStreaming.value = false
    }

    fun approveToolCall(toolCall: ToolCall) {
        val workspace = _activeWorkspace.value ?: return
        val conversation = _activeConversation.value ?: return
        val provider = _selectedProvider.value ?: return

        viewModelScope.launch {
            _isStreaming.value = true
            _pendingToolCalls.value = _pendingToolCalls.value.filter { it.id != toolCall.id }

            // Execute the approved tool
            toolCall.status = "executing"
            val resultText = toolRegistry.executeTool(toolCall.name, toolCall.arguments, workspace)
            toolCall.status = "executed"
            toolCall.result = resultText

            telemetryClient.logToolExecuted(toolCall.name, "manually_approved")

            val toolMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "tool",
                content = resultText,
                toolCallId = toolCall.id
            )

            val updatedConv = conversation.copy(messages = conversation.messages + toolMsg)
            _activeConversation.value = updatedConv
            stateStore.saveConversation(updatedConv)

            // Continue the loop if all pending are resolved
            if (_pendingToolCalls.value.isEmpty()) {
                runAgenticLoop(updatedConv, provider, workspace)
            }
        }
    }

    fun rejectToolCall(toolCall: ToolCall) {
        val conversation = _activeConversation.value ?: return
        val provider = _selectedProvider.value ?: return
        val workspace = _activeWorkspace.value ?: return

        viewModelScope.launch {
            _pendingToolCalls.value = _pendingToolCalls.value.filter { it.id != toolCall.id }

            toolCall.status = "rejected"
            toolCall.result = "Error: Tool execution rejected by the user."

            val toolMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "tool",
                content = "Error: Tool execution rejected by the user.",
                toolCallId = toolCall.id
            )

            val updatedConv = conversation.copy(messages = conversation.messages + toolMsg)
            _activeConversation.value = updatedConv
            stateStore.saveConversation(updatedConv)

            // Continue the loop if all pending are resolved
            if (_pendingToolCalls.value.isEmpty()) {
                _isStreaming.value = true
                runAgenticLoop(updatedConv, provider, workspace)
            }
        }
    }

    fun stopStreaming() {
        _isStreaming.value = false
        _streamingMessage.value = null
        _pendingToolCalls.value = emptyList()
    }
}

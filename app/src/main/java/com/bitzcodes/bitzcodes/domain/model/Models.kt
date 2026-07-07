package com.bitzcodes.bitzcodes.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Workspace(
    val id: String,
    val name: String,
    val path: String, // Can be a local absolute path or a SAF content:// URI
    val isSafUri: Boolean,
    val rules: String = "" // Workspace-specific AI rules
)

@Serializable
data class AIProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Float = 0.7f,
    val isThinkingMode: Boolean = false,
    val autoApproveTools: Boolean = false
)

@Serializable
data class ChatMessage(
    val id: String,
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isThinking: Boolean = false,
    val thinkingContent: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null // For role = "tool"
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String, // JSON arguments string
    var status: String = "pending", // "pending", "approved", "rejected", "executed"
    var result: String? = null
)

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val workspaceId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AppSettings(
    val globalRules: String = "",
    val activeWorkspaceId: String? = null,
    val activeConversationId: String? = null,
    val selectedProviderId: String? = null,
    val themeMode: String = "system" // "light", "dark", "system"
)

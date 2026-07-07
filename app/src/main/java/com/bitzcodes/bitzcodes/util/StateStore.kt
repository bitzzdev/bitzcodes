package com.bitzcodes.bitzcodes.util

import android.content.Context
import com.bitzcodes.bitzcodes.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class StateStore(private val context: Context) {
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val settingsFile = File(context.filesDir, "settings.json")
    private val workspacesFile = File(context.filesDir, "workspaces.json")
    private val providersFile = File(context.filesDir, "providers.json")
    private val conversationsDir = File(context.filesDir, "conversations").apply { mkdirs() }

    fun loadSettings(): AppSettings {
        return try {
            if (settingsFile.exists()) {
                json.decodeFromString(settingsFile.readText())
            } else {
                AppSettings()
            }
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        try {
            settingsFile.writeText(json.encodeToString(settings))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadWorkspaces(): List<Workspace> {
        return try {
            if (workspacesFile.exists()) {
                json.decodeFromString(workspacesFile.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveWorkspaces(workspaces: List<Workspace>) {
        try {
            workspacesFile.writeText(json.encodeToString(workspaces))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadProviders(): List<AIProvider> {
        return try {
            if (providersFile.exists()) {
                json.decodeFromString(providersFile.readText())
            } else {
                defaultProviders()
            }
        } catch (e: Exception) {
            defaultProviders()
        }
    }

    fun saveProviders(providers: List<AIProvider>) {
        try {
            providersFile.writeText(json.encodeToString(providers))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadConversations(workspaceId: String?): List<Conversation> {
        return try {
            conversationsDir.listFiles()
                ?.mapNotNull { file ->
                    try {
                        val conv = json.decodeFromString<Conversation>(file.readText())
                        if (conv.workspaceId == workspaceId) conv else null
                    } catch (e: Exception) {
                        null
                    }
                }
                ?.sortedByDescending { it.timestamp }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveConversation(conversation: Conversation) {
        try {
            val file = File(conversationsDir, "${conversation.id}.json")
            file.writeText(json.encodeToString(conversation))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteConversation(conversationId: String) {
        try {
            val file = File(conversationsDir, "$conversationId.json")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun defaultProviders(): List<AIProvider> {
        return listOf(
            AIProvider(
                id = "gemini",
                name = "Google Gemini",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/models",
                apiKey = "",
                model = "gemini-2.5-flash",
                temperature = 0.7f,
                isThinkingMode = false,
                autoApproveTools = false
            ),
            AIProvider(
                id = "openai",
                name = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                apiKey = "",
                model = "gpt-4o-mini",
                temperature = 0.7f,
                isThinkingMode = false,
                autoApproveTools = false
            ),
            AIProvider(
                id = "anthropic",
                name = "Anthropic Claude",
                baseUrl = "https://api.anthropic.com/v1",
                apiKey = "",
                model = "claude-3-5-sonnet-20241022",
                temperature = 0.7f,
                isThinkingMode = false,
                autoApproveTools = false
            )
        )
    }
}

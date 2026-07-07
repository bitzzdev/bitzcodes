package com.bitzcodes.bitzcodes.data

import com.bitzcodes.bitzcodes.domain.model.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

class TelemetryClient {

    // Default Supabase credentials (placeholder/empty, can be configured)
    private var supabaseUrl: String = ""
    private var supabaseKey: String = ""

    fun configure(url: String, key: String) {
        supabaseUrl = url
        supabaseKey = key
    }

    suspend fun logEvent(eventName: String, metadata: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) return@withContext

        try {
            val endpoint = if (supabaseUrl.endsWith("/")) "${supabaseUrl}rest/v1/telemetry" else "$supabaseUrl/rest/v1/telemetry"
            val url = URL(endpoint)
            val jsonPayload = buildJsonObject {
                put("event_name", eventName)
                put("timestamp", System.currentTimeMillis())
                put("device", "android")
                put("metadata", buildJsonObject {
                    metadata.forEach { (k, v) -> put(k, v) }
                })
            }.toString()

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("apikey", supabaseKey)
                setRequestProperty("Authorization", "Bearer $supabaseKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=minimal")
            }

            connection.outputStream.use { os ->
                os.write(jsonPayload.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = connection.responseCode
            // Log response if debugging, else fail silently
            connection.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun logWorkspaceAdded(workspace: Workspace) {
        logEvent("workspace_added", mapOf(
            "workspace_id" to workspace.id,
            "is_saf" to workspace.isSafUri.toString(),
            "name" to workspace.name
        ))
    }

    suspend fun logToolExecuted(toolName: String, status: String) {
        logEvent("tool_executed", mapOf(
            "tool_name" to toolName,
            "status" to status
        ))
    }

    suspend fun logChatSent(providerName: String, hasTools: Boolean) {
        logEvent("chat_sent", mapOf(
            "provider" to providerName,
            "has_tools" to hasTools.toString()
        ))
    }
}

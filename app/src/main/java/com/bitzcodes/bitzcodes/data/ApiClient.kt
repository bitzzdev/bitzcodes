package com.bitzcodes.bitzcodes.data

import com.bitzcodes.bitzcodes.domain.model.AIProvider
import com.bitzcodes.bitzcodes.domain.model.Conversation
import com.bitzcodes.bitzcodes.domain.model.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ApiClient {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    suspend fun streamChat(
        provider: AIProvider,
        systemPrompt: String,
        conversation: Conversation,
        onChunk: (String) -> Unit
    ): Pair<String, List<ToolCall>> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        val fullContent = StringBuilder()

        try {
            val isAnthropic = provider.id == "anthropic" || provider.baseUrl.contains("anthropic.com")
            
            // Adjust Gemini base URL if using Google Gemini but not OpenAI-compatible endpoint
            val targetUrl: String
            val headers = mutableMapOf<String, String>()
            val requestBodyJson: JsonObject

            if (isAnthropic) {
                targetUrl = if (provider.baseUrl.endsWith("/messages")) provider.baseUrl else "${provider.baseUrl.trimEnd('/')}/messages"
                headers["x-api-key"] = provider.apiKey
                headers["anthropic-version"] = "2023-06-01"
                headers["content-type"] = "application/json"

                // Map messages
                val msgList = conversation.messages.map { msg ->
                    buildJsonObject {
                        put("role", if (msg.role == "assistant") "assistant" else "user")
                        put("content", msg.content)
                    }
                }

                requestBodyJson = buildJsonObject {
                    put("model", provider.model)
                    put("system", systemPrompt)
                    put("messages", buildJsonArray { msgList.forEach { add(it) } })
                    put("stream", true)
                    put("max_tokens", 4096)
                    put("temperature", provider.temperature)
                }
            } else {
                // OpenAI-compatible (including Gemini via OpenAI endpoint, Ollama, LM Studio, etc.)
                val isGemini = provider.id == "gemini" || provider.baseUrl.contains("googleapis.com")
                targetUrl = if (isGemini) {
                    // Gemini OpenAI compatibility endpoint:
                    // https://generativelanguage.googleapis.com/v1beta/openai/chat/completions?key=API_KEY
                    if (provider.baseUrl.contains("openai")) {
                        provider.baseUrl
                    } else {
                        "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions?key=${provider.apiKey}"
                    }
                } else {
                    if (provider.baseUrl.endsWith("/chat/completions")) provider.baseUrl else "${provider.baseUrl.trimEnd('/')}/chat/completions"
                }

                if (!isGemini && provider.apiKey.isNotEmpty()) {
                    headers["Authorization"] = "Bearer ${provider.apiKey}"
                }
                headers["Content-Type"] = "application/json"

                // Build messages
                val messagesArray = buildJsonArray {
                    // System message
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    // Conversation messages
                    conversation.messages.forEach { msg ->
                        add(buildJsonObject {
                            put("role", when (msg.role) {
                                "tool" -> "tool"
                                "assistant" -> "assistant"
                                else -> "user"
                            })
                            // If role is tool, include tool_call_id
                            if (msg.role == "tool") {
                                put("tool_call_id", msg.toolCallId ?: "")
                            }
                            put("content", msg.content)
                        })
                    }
                }

                requestBodyJson = buildJsonObject {
                    put("model", provider.model)
                    put("messages", messagesArray)
                    put("stream", true)
                    put("temperature", provider.temperature)
                }
            }

            val url = URL(targetUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                connectTimeout = 30000
                readTimeout = 60000
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }

            connection.outputStream.use { os ->
                os.write(requestBodyJson.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errText = connection.errorStream?.use { it.reader().readText() } ?: "Unknown error"
                throw Exception("API call failed with code $responseCode: $errText")
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: break
                if (currentLine.startsWith("data:")) {
                    val dataContent = currentLine.substring(5).trim()
                    if (dataContent == "[DONE]") continue

                    try {
                        val textChunk = if (isAnthropic) {
                            parseAnthropicChunk(dataContent)
                        } else {
                            parseOpenAIChunk(dataContent)
                        }
                        if (textChunk != null && textChunk.isNotEmpty()) {
                            fullContent.append(textChunk)
                            onChunk(textChunk)
                        }
                    } catch (e: Exception) {
                        // Ignore JSON parsing errors for partial/malformed chunk lines
                    }
                }
            }
        } finally {
            connection?.disconnect()
        }

        val finalString = fullContent.toString()
        val toolCalls = parseToolCalls(finalString)
        return@withContext Pair(finalString, toolCalls)
    }

    private fun parseOpenAIChunk(data: String): String? {
        val root = jsonParser.parseToJsonElement(data).jsonObject
        val choices = root["choices"]?.jsonArray
        if (choices != null && choices.isNotEmpty()) {
            val delta = choices[0].jsonObject["delta"]?.jsonObject
            return delta?.get("content")?.jsonPrimitive?.content
        }
        return null
    }

    private fun parseAnthropicChunk(data: String): String? {
        val root = jsonParser.parseToJsonElement(data).jsonObject
        val type = root["type"]?.jsonPrimitive?.content
        if (type == "content_block_delta") {
            val delta = root["delta"]?.jsonObject
            if (delta?.get("type")?.jsonPrimitive?.content == "text_delta") {
                return delta["text"]?.jsonPrimitive?.content
            }
        }
        return null
    }

    fun parseToolCalls(text: String): List<ToolCall> {
        val list = mutableListOf<ToolCall>()
        
        // Find tags like: <tool_call name="tool_name">JSON_ARGUMENTS</tool_call>
        val xmlPattern = Regex("<tool_call\\s+name=\"([^\"]+)\">([\\s\\S]*?)</tool_call>")
        xmlPattern.findAll(text).forEach { match ->
            val name = match.groups[1]?.value.orEmpty().trim()
            val args = match.groups[2]?.value.orEmpty().trim()
            list.add(ToolCall(id = "call_" + System.nanoTime(), name = name, arguments = args))
        }

        // Support fallback: ```tool_call\n{\n "name": "tool_name",\n "arguments": ...\n}\n```
        val mdPattern = Regex("```tool_call\\s*([\\s\\S]*?)```")
        mdPattern.findAll(text).forEach { match ->
            try {
                val block = match.groups[1]?.value.orEmpty().trim()
                val json = jsonParser.parseToJsonElement(block).jsonObject
                val name = json["name"]?.jsonPrimitive?.content.orEmpty()
                val args = json["arguments"]?.toString() ?: json["args"]?.toString() ?: "{}"
                if (name.isNotEmpty()) {
                    list.add(ToolCall(id = "call_" + System.nanoTime(), name = name, arguments = args))
                }
            } catch (e: Exception) {
                // Ignore invalid markdown JSON blocks
            }
        }

        return list
    }
}

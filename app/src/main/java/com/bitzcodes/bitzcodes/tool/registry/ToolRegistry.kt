package com.bitzcodes.bitzcodes.tool.registry

import android.content.Context
import com.bitzcodes.bitzcodes.data.FileResolver
import com.bitzcodes.bitzcodes.domain.model.Workspace
import com.bitzcodes.bitzcodes.tool.file.FileTools
import com.bitzcodes.bitzcodes.tool.terminal.TerminalTools
import com.bitzcodes.bitzcodes.tool.web.WebTools
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: String
)

class ToolRegistry(
    private val context: Context,
    private val fileResolver: FileResolver
) {
    private val fileTools = FileTools(fileResolver)
    private val terminalTools = TerminalTools()
    private val webTools = WebTools()
    private val json = Json { ignoreUnknownKeys = true }

    val tools = listOf(
        ToolDefinition(
            name = "list_files",
            description = "Lists all files and directories in the workspace recursively. Use parameter path (default empty) to specify a subdirectory.",
            parameters = "{\"path\": \"Optional relative path to subdirectory\"}"
        ),
        ToolDefinition(
            name = "read_file",
            description = "Reads the complete contents of a file in the workspace.",
            parameters = "{\"path\": \"Required relative path to the file\"}"
        ),
        ToolDefinition(
            name = "write_file",
            description = "Writes complete contents to a file in the workspace, creating any parent folders if necessary.",
            parameters = "{\"path\": \"Required relative path\", \"content\": \"Full text content to write\"}"
        ),
        ToolDefinition(
            name = "apply_edits",
            description = "Modifies an existing file by replacing a contiguous block of text (target_content) with a new block of text (replacement_content).",
            parameters = "{\"path\": \"Required relative path\", \"target_content\": \"Exact text block in the file to replace\", \"replacement_content\": \"Text block to replace with\"}"
        ),
        ToolDefinition(
            name = "run_command",
            description = "Runs a shell command in the workspace directory. Commands are executed inside the app's local Sandbox.",
            parameters = "{\"command\": \"The command string to execute, e.g. 'ls -l'\"}"
        ),
        ToolDefinition(
            name = "search_web",
            description = "Searches the web for a given query and returns top organic results and summaries.",
            parameters = "{\"query\": \"Search query terms\"}"
        ),
        ToolDefinition(
            name = "read_url",
            description = "Fetches a web page URL and parses its content into clean readable text using JSoup.",
            parameters = "{\"url\": \"Web URL to fetch\"}"
        )
    )

    fun getSystemInstructions(): String {
        val sb = StringBuilder()
        sb.append("You are BITZCODES, an AI coding assistant. You have access to the user's workspace files and can invoke tools.\n")
        sb.append("To execute a tool, write a <tool_call> XML block in your response. The arguments must be formatted as valid JSON.\n")
        sb.append("Format details:\n")
        sb.append("<tool_call name=\"TOOL_NAME\">\n")
        sb.append("{\n")
        sb.append("  \"arg1\": \"value1\"\n")
        sb.append("}\n")
        sb.append("</tool_call>\n\n")
        sb.append("Here is the list of available tools:\n")
        tools.forEach { tool ->
            sb.append("- Name: ${tool.name}\n")
            sb.append("  Description: ${tool.description}\n")
            sb.append("  Parameters: ${tool.parameters}\n\n")
        }
        sb.append("Always return a tool response before performing next steps. Wait for the user to provide the tool execution result.\n")
        return sb.toString()
    }

    suspend fun executeTool(name: String, argumentsJson: String, workspace: Workspace): String {
        return try {
            val parsedArgs = json.parseToJsonElement(argumentsJson).jsonObject
            when (name) {
                "list_files" -> {
                    val subPath = parsedArgs["path"]?.jsonPrimitive?.content ?: ""
                    fileTools.listFiles(workspace, subPath)
                }
                "read_file" -> {
                    val path = parsedArgs["path"]?.jsonPrimitive?.content ?: throw Exception("Missing 'path' argument")
                    fileTools.readFile(workspace, path)
                }
                "write_file" -> {
                    val path = parsedArgs["path"]?.jsonPrimitive?.content ?: throw Exception("Missing 'path' argument")
                    val content = parsedArgs["content"]?.jsonPrimitive?.content ?: ""
                    fileTools.writeFile(workspace, path, content)
                }
                "apply_edits" -> {
                    val path = parsedArgs["path"]?.jsonPrimitive?.content ?: throw Exception("Missing 'path' argument")
                    val target = parsedArgs["target_content"]?.jsonPrimitive?.content ?: throw Exception("Missing 'target_content'")
                    val replacement = parsedArgs["replacement_content"]?.jsonPrimitive?.content ?: ""
                    fileTools.applyEdits(workspace, path, target, replacement)
                }
                "run_command" -> {
                    val command = parsedArgs["command"]?.jsonPrimitive?.content ?: throw Exception("Missing 'command' argument")
                    terminalTools.runCommand(command, workspace)
                }
                "search_web" -> {
                    val query = parsedArgs["query"]?.jsonPrimitive?.content ?: throw Exception("Missing 'query' argument")
                    webTools.searchWeb(query)
                }
                "read_url" -> {
                    val url = parsedArgs["url"]?.jsonPrimitive?.content ?: throw Exception("Missing 'url' argument")
                    webTools.readUrl(url)
                }
                else -> {
                    "Error: Tool '$name' not found."
                }
            }
        } catch (e: Exception) {
            "Error executing tool '$name': ${e.message}"
        }
    }
}

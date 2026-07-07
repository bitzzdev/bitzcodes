package com.bitzcodes.bitzcodes.tool.file

import com.bitzcodes.bitzcodes.data.FileResolver
import com.bitzcodes.bitzcodes.domain.model.Workspace

class FileTools(private val fileResolver: FileResolver) {

    fun listFiles(workspace: Workspace, subPath: String): String {
        return try {
            val list = mutableListOf<String>()
            buildFileListRecursive(workspace, subPath, list)
            if (list.isEmpty()) {
                "No files found in workspace relative path: '$subPath'"
            } else {
                list.joinToString("\n")
            }
        } catch (e: Exception) {
            "Error listing files: ${e.message}"
        }
    }

    private fun buildFileListRecursive(workspace: Workspace, subPath: String, output: MutableList<String>) {
        val files = fileResolver.listFiles(workspace, subPath)
        for (item in files) {
            if (item.isDirectory) {
                output.add("[Dir]  ${item.relativePath}")
                buildFileListRecursive(workspace, item.relativePath, output)
            } else {
                output.add("[File] ${item.relativePath} (${item.size} bytes)")
            }
        }
    }

    fun readFile(workspace: Workspace, path: String): String {
        return try {
            val content = fileResolver.readFile(workspace, path)
            if (content.isEmpty() && fileResolver.listFiles(workspace, path).isNotEmpty()) {
                "Error: '$path' is a directory, not a file."
            } else {
                content
            }
        } catch (e: Exception) {
            "Error reading file '$path': ${e.message}"
        }
    }

    fun writeFile(workspace: Workspace, path: String, content: String): String {
        return try {
            val success = fileResolver.writeFile(workspace, path, content)
            if (success) {
                "Successfully wrote to file '$path'."
            } else {
                "Failed to write to file '$path'."
            }
        } catch (e: Exception) {
            "Error writing file '$path': ${e.message}"
        }
    }

    fun applyEdits(workspace: Workspace, path: String, target: String, replacement: String): String {
        return try {
            val content = fileResolver.readFile(workspace, path)
            if (content.isEmpty()) {
                return "Error: File '$path' is empty or does not exist."
            }
            if (!content.contains(target)) {
                return "Error: Could not find target content block in file '$path'. Make sure your target_content matches exactly (including spaces, newlines, and indentation)."
            }
            val newContent = content.replace(target, replacement)
            val success = fileResolver.writeFile(workspace, path, newContent)
            if (success) {
                "Successfully applied edits to file '$path'."
            } else {
                "Failed to write updated content to file '$path'."
            }
        } catch (e: Exception) {
            "Error applying edits to file '$path': ${e.message}"
        }
    }
}

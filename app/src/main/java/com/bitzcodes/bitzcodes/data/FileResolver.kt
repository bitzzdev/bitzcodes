package com.bitzcodes.bitzcodes.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.bitzcodes.bitzcodes.domain.model.Workspace
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class FileItem(
    val name: String,
    val relativePath: String, // Path relative to workspace root, e.g. "src/main"
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

class FileResolver(private val context: Context) {

    fun listFiles(workspace: Workspace, relativePath: String): List<FileItem> {
        val cleanPath = relativePath.trim('/')
        if (workspace.isSafUri) {
            val rootUri = Uri.parse(workspace.path)
            // Take persistable permission if possible (in case we lost it)
            try {
                context.contentResolver.takePersistableUriPermission(
                    rootUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if already taken or not possible
            }
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
            val targetDoc = findDocFileByPath(rootDoc, cleanPath) ?: return emptyList()
            if (!targetDoc.isDirectory) return emptyList()

            return targetDoc.listFiles().map { doc ->
                val childRelativePath = if (cleanPath.isEmpty()) doc.name.orEmpty() else "$cleanPath/${doc.name.orEmpty()}"
                FileItem(
                    name = doc.name.orEmpty(),
                    relativePath = childRelativePath,
                    isDirectory = doc.isDirectory,
                    size = doc.length(),
                    lastModified = doc.lastModified()
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } else {
            val rootFile = File(workspace.path)
            val targetFile = if (cleanPath.isEmpty()) rootFile else File(rootFile, cleanPath)
            if (!targetFile.exists() || !targetFile.isDirectory) return emptyList()

            return targetFile.listFiles()?.map { file ->
                val childRelativePath = if (cleanPath.isEmpty()) file.name else "$cleanPath/${file.name}"
                FileItem(
                    name = file.name,
                    relativePath = childRelativePath,
                    isDirectory = file.isDirectory,
                    size = file.length(),
                    lastModified = file.lastModified()
                )
            }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        }
    }

    fun readFile(workspace: Workspace, relativePath: String): String {
        val cleanPath = relativePath.trim('/')
        if (workspace.isSafUri) {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(workspace.path)) ?: return ""
            val docFile = findDocFileByPath(rootDoc, cleanPath) ?: return ""
            if (docFile.isDirectory) return ""
            return context.contentResolver.openInputStream(docFile.uri)?.use { stream ->
                InputStreamReader(stream).readText()
            } ?: ""
        } else {
            val rootFile = File(workspace.path)
            val file = File(rootFile, cleanPath)
            if (!file.exists() || file.isDirectory) return ""
            return file.readText()
        }
    }

    fun writeFile(workspace: Workspace, relativePath: String, content: String): Boolean {
        val cleanPath = relativePath.trim('/')
        if (workspace.isSafUri) {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(workspace.path)) ?: return false
            val docFile = findDocFileByPath(rootDoc, cleanPath) ?: return false
            if (docFile.isDirectory) return false
            return try {
                context.contentResolver.openOutputStream(docFile.uri, "rwt")?.use { stream ->
                    OutputStreamWriter(stream).use { writer ->
                        writer.write(content)
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        } else {
            return try {
                val rootFile = File(workspace.path)
                val file = File(rootFile, cleanPath)
                file.parentFile?.mkdirs()
                file.writeText(content)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    fun createFile(workspace: Workspace, parentRelativePath: String, fileName: String, isDirectory: Boolean): Boolean {
        val cleanParent = parentRelativePath.trim('/')
        if (workspace.isSafUri) {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(workspace.path)) ?: return false
            val parentDoc = findDocFileByPath(rootDoc, cleanParent) ?: return false
            if (!parentDoc.isDirectory) return false
            return try {
                if (isDirectory) {
                    parentDoc.createDirectory(fileName) != null
                } else {
                    parentDoc.createFile("text/plain", fileName) != null
                }
            } catch (e: Exception) {
                false
            }
        } else {
            val rootFile = File(workspace.path)
            val parentFile = if (cleanParent.isEmpty()) rootFile else File(rootFile, cleanParent)
            if (!parentFile.exists()) parentFile.mkdirs()
            val newFile = File(parentFile, fileName)
            return try {
                if (isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    newFile.createNewFile()
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    fun deleteFile(workspace: Workspace, relativePath: String): Boolean {
        val cleanPath = relativePath.trim('/')
        if (workspace.isSafUri) {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(workspace.path)) ?: return false
            val docFile = findDocFileByPath(rootDoc, cleanPath) ?: return false
            return docFile.delete()
        } else {
            val rootFile = File(workspace.path)
            val file = File(rootFile, cleanPath)
            if (!file.exists()) return false
            return file.deleteRecursively()
        }
    }

    fun renameFile(workspace: Workspace, relativePath: String, newName: String): Boolean {
        val cleanPath = relativePath.trim('/')
        if (workspace.isSafUri) {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(workspace.path)) ?: return false
            val docFile = findDocFileByPath(rootDoc, cleanPath) ?: return false
            return docFile.renameTo(newName)
        } else {
            val rootFile = File(workspace.path)
            val file = File(rootFile, cleanPath)
            if (!file.exists()) return false
            val dest = File(file.parentFile, newName)
            return file.renameTo(dest)
        }
    }

    private fun findDocFileByPath(root: DocumentFile, subPath: String): DocumentFile? {
        if (subPath.isEmpty()) return root
        val parts = subPath.split("/").filter { it.isNotEmpty() }
        var current: DocumentFile? = root
        for (part in parts) {
            current = current?.findFile(part)
        }
        return current
    }
}

// Helper object for taking SAF URI intent flags
object Intent {
    const val FLAG_GRANT_READ_URI_PERMISSION = 1
    const val FLAG_GRANT_WRITE_URI_PERMISSION = 2
}

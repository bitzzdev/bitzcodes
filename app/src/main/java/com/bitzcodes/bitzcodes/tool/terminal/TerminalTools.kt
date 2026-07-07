package com.bitzcodes.bitzcodes.tool.terminal

import com.bitzcodes.bitzcodes.domain.model.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class TerminalTools {

    suspend fun runCommand(command: String, workspace: Workspace): String = withContext(Dispatchers.IO) {
        if (workspace.isSafUri) {
            return@withContext "Error: Shell command execution is not supported on SAF content:// workspaces. Please use a local storage workspace for terminal command capabilities."
        }

        val workingDir = File(workspace.path)
        if (!workingDir.exists() || !workingDir.isDirectory) {
            return@withContext "Error: Workspace directory '${workspace.path}' does not exist or is not valid."
        }

        try {
            val process = ProcessBuilder()
                .directory(workingDir)
                .command("sh", "-c", command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(15, TimeUnit.SECONDS)

            if (!finished) {
                process.destroyForcibly()
                return@withContext "Error: Command timed out after 15 seconds.\nPartial Output:\n$output"
            }

            val exitCode = process.exitValue()
            if (output.trim().isEmpty()) {
                "Command executed with exit code $exitCode (no output)."
            } else {
                "Output:\n$output\n[Exit Code: $exitCode]"
            }
        } catch (e: Exception) {
            "Error running command '$command': ${e.message}"
        }
    }
}

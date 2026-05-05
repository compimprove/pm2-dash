package org.compi.pm2dash.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Paths

internal data class CommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
)

internal fun interface CommandExecutor {
    suspend fun run(command: List<String>): CommandResult
}

internal class ProcessCommandExecutor : CommandExecutor {
    override suspend fun run(command: List<String>): CommandResult = withContext(Dispatchers.IO) {
        try {
            val processBuilder = ProcessBuilder(command)
                .redirectErrorStream(false)

            enrichPathForCommand(processBuilder, command.firstOrNull())

            val process = processBuilder.start()

            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            CommandResult(stdout = stdout, stderr = stderr, exitCode = exitCode)
        } catch (exception: IOException) {
            throw IOException("Failed to execute `${command.joinToString(" ")}`: ${exception.message}", exception)
        }
    }
}

private fun enrichPathForCommand(
    processBuilder: ProcessBuilder,
    executable: String?,
) {
    if (executable.isNullOrBlank()) return

    val executablePath = runCatching { Paths.get(executable) }.getOrNull() ?: return
    val parentDir = executablePath.parent?.toAbsolutePath()?.normalize()?.toString() ?: return

    val environment = processBuilder.environment()
    val currentPath = environment["PATH"].orEmpty()
    environment["PATH"] = if (currentPath.isBlank()) {
        parentDir
    } else {
        "$parentDir:$currentPath"
    }
}

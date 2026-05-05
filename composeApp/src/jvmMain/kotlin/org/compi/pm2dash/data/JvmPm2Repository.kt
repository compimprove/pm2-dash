package org.compi.pm2dash.data

import kotlinx.coroutines.flow.Flow
import org.compi.pm2dash.model.DashboardError
import org.compi.pm2dash.model.DashboardErrorKind
import org.compi.pm2dash.model.ProcessLogsState
import org.compi.pm2dash.state.Pm2ProcessLoadResult
import org.compi.pm2dash.state.Pm2Repository
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.io.path.isDirectory
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText

internal class JvmPm2Repository(
    private val commandExecutor: CommandExecutor = ProcessCommandExecutor(),
    private val pm2Home: Path = Paths.get(System.getProperty("user.home"), ".pm2"),
) : Pm2Repository {
    override suspend fun loadProcesses(): Pm2ProcessLoadResult {
        val pm2Command = resolvePm2Command()
        val result = try {
            commandExecutor.run(listOf(pm2Command, "jlist"))
        } catch (exception: IOException) {
            return Pm2ProcessLoadResult.Error(
                DashboardError(
                    kind = DashboardErrorKind.Pm2NotInstalled,
                    title = "PM2 is not available",
                    message = "The app could not execute `pm2 jlist`. Install PM2 or expose it in a standard location such as Homebrew or NVM.",
                    details = exception.message,
                ),
            )
        } catch (exception: Exception) {
            return Pm2ProcessLoadResult.Error(
                DashboardError(
                    kind = DashboardErrorKind.CommandFailed,
                    title = "PM2 command failed",
                    message = "The app could not read the PM2 process list.",
                    details = exception.message,
                ),
            )
        }

        if (result.exitCode != 0) {
            return Pm2ProcessLoadResult.Error(mapCommandFailure(result))
        }

        return try {
            Pm2ProcessLoadResult.Success(parseProcessList(result.stdout))
        } catch (exception: Exception) {
            Pm2ProcessLoadResult.Error(
                DashboardError(
                    kind = DashboardErrorKind.InvalidPm2Data,
                    title = "PM2 returned invalid data",
                    message = "The `pm2 jlist` output could not be parsed.",
                    details = exception.message,
                ),
            )
        }
    }

    override fun streamLogs(processName: String): Flow<ProcessLogsState> {
        return streamPm2Logs(processName = processName, logsDirectory = pm2Home.resolve("logs"))
    }

    override suspend fun clearLogs(processName: String): Result<Unit> {
        return runCatching {
            val logsDirectory = pm2Home.resolve("logs")
            val stdoutFile = logsDirectory.resolve("$processName-out.log")
            val stderrFile = logsDirectory.resolve("$processName-error.log")

            if (!stdoutFile.exists() && !stderrFile.exists()) {
                error("No PM2 log files found for $processName.")
            }

            if (stdoutFile.exists()) {
                stdoutFile.writeText("", options = arrayOf(StandardOpenOption.TRUNCATE_EXISTING))
            }
            if (stderrFile.exists()) {
                stderrFile.writeText("", options = arrayOf(StandardOpenOption.TRUNCATE_EXISTING))
            }
        }
    }
}

fun createPm2Repository(): Pm2Repository = JvmPm2Repository()

private fun resolvePm2Command(): String {
    val userHome = System.getProperty("user.home")
    val pathEntries = System.getenv("PATH")
        ?.split(":")
        ?.filter { it.isNotBlank() }
        .orEmpty()
        .map { Paths.get(it).resolve("pm2") }

    val explicitCandidates = buildList {
        addAll(pathEntries)
        add(Paths.get("/opt/homebrew/bin/pm2"))
        add(Paths.get("/usr/local/bin/pm2"))
        add(Paths.get(userHome, ".volta", "bin", "pm2"))
        addAll(findNvmPm2Candidates(userHome))
    }

    return explicitCandidates
        .firstOrNull(::isRunnableFile)
        ?.toString()
        ?: "pm2"
}

private fun findNvmPm2Candidates(userHome: String): List<Path> {
    val versionsDir = Paths.get(userHome, ".nvm", "versions", "node")
    if (!versionsDir.exists() || !versionsDir.isDirectory()) return emptyList()

    return versionsDir.listDirectoryEntries()
        .sortedByDescending { it.name }
        .map { it.resolve("bin").resolve("pm2") }
        .filter(::isRunnableFile)
}

private fun isRunnableFile(path: Path): Boolean = Files.isRegularFile(path) && Files.isExecutable(path)

internal fun mapCommandFailure(result: CommandResult): DashboardError {
    val combined = listOf(result.stderr, result.stdout)
        .joinToString("\n")
        .trim()
        .lowercase()

    return when {
        "command not found" in combined || "no such file or directory" in combined || "failed to execute `pm2 jlist`" in combined ->
            DashboardError(
                kind = DashboardErrorKind.Pm2NotInstalled,
                title = "PM2 is not installed",
                message = "The desktop app could not find the `pm2` executable.",
                details = result.stderr.ifBlank { result.stdout },
            )

        "daemon" in combined && ("not launched" in combined || "not running" in combined) ->
            DashboardError(
                kind = DashboardErrorKind.DaemonNotRunning,
                title = "PM2 daemon is not running",
                message = "Start PM2 once from your terminal and the dashboard will pick it up on the next refresh.",
                details = result.stderr.ifBlank { result.stdout },
            )

        else -> DashboardError(
            kind = DashboardErrorKind.CommandFailed,
            title = "PM2 command failed",
            message = "The app could not read PM2 state from `pm2 jlist`.",
            details = result.stderr.ifBlank { result.stdout },
        )
    }
}

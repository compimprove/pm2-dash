package org.compi.pm2dash.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.compi.pm2dash.model.DashboardError
import org.compi.pm2dash.model.DashboardErrorKind
import org.compi.pm2dash.model.LogChannel
import org.compi.pm2dash.model.LogStreamEntry
import org.compi.pm2dash.model.ProcessLogsState
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.name

private const val LOG_POLL_INTERVAL_MS = 1_000L
private const val MAX_LOG_ENTRIES = 1_000

internal fun streamPm2Logs(
    processName: String,
    logsDirectory: Path,
): Flow<ProcessLogsState> = flow {
    emit(ProcessLogsState.Loading)

    var previousFingerprint: String? = null
    while (true) {
        val state = readLogSnapshot(processName, logsDirectory)
        val fingerprint = state.fingerprint()
        if (fingerprint != previousFingerprint) {
            emit(state)
            previousFingerprint = fingerprint
        }
        delay(LOG_POLL_INTERVAL_MS)
    }
}.flowOn(Dispatchers.IO)

internal fun readLogSnapshot(
    processName: String,
    logsDirectory: Path,
    maxEntries: Int = MAX_LOG_ENTRIES,
): ProcessLogsState {
    val stdoutFile = logsDirectory.resolve("$processName-out.log")
    val stderrFile = logsDirectory.resolve("$processName-error.log")

    val stdoutExists = stdoutFile.exists()
    val stderrExists = stderrFile.exists()

    if (!stdoutExists && !stderrExists) {
        return ProcessLogsState.Missing(
            "No PM2 log files found for `$processName` under `${logsDirectory}`.",
        )
    }

    return try {
        val entries = buildList {
            if (stdoutExists) {
                addAll(readLastLines(stdoutFile, maxEntries / 2, LogChannel.Stdout))
            }
            if (stderrExists) {
                addAll(readLastLines(stderrFile, maxEntries / 2, LogChannel.Stderr))
            }
        }
            .sortedWith(compareBy<LogStreamEntry> { it.observedAtEpochMs }.thenBy { it.id })
            .takeLast(maxEntries)

        ProcessLogsState.Ready(entries)
    } catch (exception: Exception) {
        ProcessLogsState.Error(
            DashboardError(
                kind = DashboardErrorKind.LogFileMissing,
                title = "Could not read PM2 logs",
                message = "The PM2 log files for `$processName` could not be read.",
                details = exception.message,
            ),
        )
    }
}

internal fun readLastLines(
    file: Path,
    limit: Int,
    channel: LogChannel,
): List<LogStreamEntry> {
    val lines = ArrayDeque<String>(limit)
    file.bufferedReader().use { reader ->
        reader.lineSequence().forEach { line ->
            if (lines.size == limit) {
                lines.removeFirst()
            }
            lines.addLast(line)
        }
    }

    val timestamp = file.getLastModifiedTime().toMillis()
    return lines.mapIndexed { index, line ->
        LogStreamEntry(
            id = "${file.name}-${channel.name}-$index-${line.hashCode()}",
            channel = channel,
            message = line,
            observedAtEpochMs = timestamp + index,
        )
    }
}

private fun ProcessLogsState.fingerprint(): String = when (this) {
    ProcessLogsState.Idle -> "idle"
    ProcessLogsState.Loading -> "loading"
    is ProcessLogsState.Missing -> "missing:${message}"
    is ProcessLogsState.Error -> "error:${error.kind}:${error.message}"
    is ProcessLogsState.Ready -> entries.joinToString(separator = "|") { it.id }
}

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
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.name

private const val LOG_POLL_INTERVAL_MS = 1_000L
private const val MAX_LOG_ENTRIES = 1_000
private const val TAIL_SCAN_BUFFER_SIZE = 64 * 1_024

internal fun streamPm2Logs(
    processName: String,
    logsDirectory: Path,
): Flow<ProcessLogsState> = flow {
    emit(ProcessLogsState.Loading)

    val stdoutFile = logsDirectory.resolve("$processName-out.log")
    val stderrFile = logsDirectory.resolve("$processName-error.log")
    var previousFilesFingerprint: LogFilesFingerprint? = null
    var previousStateFingerprint: String? = null
    while (true) {
        val filesFingerprint = runCatching {
            LogFilesFingerprint(
                stdout = stdoutFile.fingerprint(),
                stderr = stderrFile.fingerprint(),
            )
        }.getOrNull()

        if (filesFingerprint == null || filesFingerprint != previousFilesFingerprint) {
            val state = readLogSnapshot(processName, logsDirectory)
            val stateFingerprint = state.fingerprint()
            if (stateFingerprint != previousStateFingerprint) {
                emit(state)
                previousStateFingerprint = stateFingerprint
            }

            if (filesFingerprint != null && state !is ProcessLogsState.Error) {
                previousFilesFingerprint = filesFingerprint
            }
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
    if (limit <= 0) return emptyList()

    val lines = readTailLines(file, limit)
    val timestamp = file.getLastModifiedTime().toMillis()
    return lines.mapIndexed { index, line ->
        LogStreamEntry(
            id = "${file.name}-${channel.name}-$index-${line.hashCode()}",
            channel = channel,
            message = line,
            observedAtEpochMs = timestamp + index,
            observedAtLocalTime = formatLocalLogTime(timestamp + index),
        )
    }
}

private fun readTailLines(file: Path, limit: Int): List<String> {
    FileChannel.open(file, StandardOpenOption.READ).use { channel ->
        val fileSize = channel.size()
        if (fileSize == 0L) return emptyList()

        val lastByte = ByteBuffer.allocate(1)
        channel.position(fileSize - 1)
        channel.readFully(lastByte)
        val endsWithNewline = lastByte.array()[0] == '\n'.code.toByte()
        val requiredNewlines = limit + if (endsWithNewline) 1 else 0

        var tailStart = 0L
        var scanPosition = fileSize
        var newlineCount = 0
        val scanBuffer = ByteBuffer.allocate(TAIL_SCAN_BUFFER_SIZE)

        scan@ while (scanPosition > 0) {
            val chunkSize = minOf(TAIL_SCAN_BUFFER_SIZE.toLong(), scanPosition).toInt()
            val chunkStart = scanPosition - chunkSize
            scanBuffer.clear()
            scanBuffer.limit(chunkSize)
            channel.position(chunkStart)
            channel.readFully(scanBuffer)

            for (index in chunkSize - 1 downTo 0) {
                if (scanBuffer.get(index) == '\n'.code.toByte()) {
                    newlineCount += 1
                    if (newlineCount == requiredNewlines) {
                        tailStart = chunkStart + index + 1
                        break@scan
                    }
                }
            }
            scanPosition = chunkStart
        }

        channel.position(tailStart)
        return Channels.newReader(channel, Charsets.UTF_8).buffered().use { reader ->
            buildList(limit) {
                while (size < limit) {
                    add(reader.readLine() ?: break)
                }
            }
        }
    }
}

private fun FileChannel.readFully(buffer: ByteBuffer) {
    while (buffer.hasRemaining()) {
        if (read(buffer) < 0) {
            throw EOFException("Log file changed while its tail was being read.")
        }
    }
}

private data class LogFilesFingerprint(
    val stdout: LogFileFingerprint?,
    val stderr: LogFileFingerprint?,
)

private data class LogFileFingerprint(
    val size: Long,
    val modifiedAtMillis: Long,
    val fileKey: String?,
)

private fun Path.fingerprint(): LogFileFingerprint? {
    if (!exists()) return null

    val attributes = Files.readAttributes(this, BasicFileAttributes::class.java)
    return LogFileFingerprint(
        size = attributes.size(),
        modifiedAtMillis = attributes.lastModifiedTime().toMillis(),
        fileKey = attributes.fileKey()?.toString(),
    )
}

private fun formatLocalLogTime(epochMs: Long): String {
    return LOCAL_LOG_TIME_FORMATTER.format(
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()),
    )
}

private val LOCAL_LOG_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun ProcessLogsState.fingerprint(): String = when (this) {
    ProcessLogsState.Idle -> "idle"
    ProcessLogsState.Loading -> "loading"
    is ProcessLogsState.Missing -> "missing:${message}"
    is ProcessLogsState.Error -> "error:${error.kind}:${error.message}"
    is ProcessLogsState.Ready -> entries.joinToString(separator = "|") { it.id }
}

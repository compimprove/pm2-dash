package org.compi.pm2dash.model

enum class Pm2ProcessStatus {
    Online,
    Stopped,
    Errored,
    Launching,
    Unknown,
}

enum class LogChannel {
    Stdout,
    Stderr,
}

enum class LogFilter {
    Combined,
    Stdout,
    Stderr,
}

enum class DashboardErrorKind {
    Pm2NotInstalled,
    DaemonNotRunning,
    InvalidPm2Data,
    CommandFailed,
    LogFileMissing,
}

data class DashboardError(
    val kind: DashboardErrorKind,
    val title: String,
    val message: String,
    val details: String? = null,
)

data class Pm2ProcessSummary(
    val pmId: Int,
    val name: String,
    val status: Pm2ProcessStatus,
    val cpuPercent: Double,
    val memoryBytes: Long,
    val pid: Int?,
    val restartCount: Int,
    val uptimeStartedAtEpochMs: Long?,
)

data class Pm2ProcessDetails(
    val summary: Pm2ProcessSummary,
    val namespace: String?,
    val interpreter: String?,
    val scriptPath: String?,
    val workingDirectory: String?,
    val watchEnabled: Boolean,
    val instanceCount: Int?,
)

data class Pm2ProcessGroup(
    val name: String,
    val processes: List<Pm2ProcessDetails>,
    val isCustom: Boolean = false,
)

data class CustomProcessGroup(
    val name: String,
    val processNames: List<String>,
)

data class LogStreamEntry(
    val id: String,
    val channel: LogChannel,
    val message: String,
    val observedAtEpochMs: Long,
    val observedAtLocalTime: String,
)

sealed interface DashboardState {
    data object Loading : DashboardState

    data class Ready(
        val groups: List<Pm2ProcessGroup>,
    ) : DashboardState

    data class Empty(
        val title: String,
        val message: String,
    ) : DashboardState

    data class Error(
        val error: DashboardError,
    ) : DashboardState
}

sealed interface ProcessLogsState {
    data object Idle : ProcessLogsState
    data object Loading : ProcessLogsState

    data class Ready(
        val entries: List<LogStreamEntry>,
    ) : ProcessLogsState

    data class Missing(
        val message: String,
    ) : ProcessLogsState

    data class Error(
        val error: DashboardError,
    ) : ProcessLogsState
}

data class Pm2DashboardUiState(
    val dashboardState: DashboardState = DashboardState.Loading,
    val customGroups: List<CustomProcessGroup> = emptyList(),
    val selectedProcessId: Int? = null,
    val selectedProcess: Pm2ProcessDetails? = null,
    val logsState: ProcessLogsState = ProcessLogsState.Idle,
    val logFilter: LogFilter = LogFilter.Combined,
    val followLogs: Boolean = true,
    val isClearingLogs: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastUpdatedEpochMs: Long? = null,
)

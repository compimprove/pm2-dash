package org.compi.pm2dash.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.compi.pm2dash.model.DashboardErrorKind
import org.compi.pm2dash.model.DashboardState
import org.compi.pm2dash.model.LogChannel
import org.compi.pm2dash.model.LogFilter
import org.compi.pm2dash.model.Pm2DashboardUiState
import org.compi.pm2dash.model.Pm2ProcessDetails
import org.compi.pm2dash.model.Pm2ProcessGroup
import org.compi.pm2dash.model.ProcessLogsState

private const val PROCESS_POLL_INTERVAL_MS = 3_000L

class Pm2DashboardStore(
    private val repository: Pm2Repository,
    private val scope: CoroutineScope,
) {
    var uiState by mutableStateOf(Pm2DashboardUiState())
        private set

    private val refreshMutex = Mutex()
    private var processPollingJob: Job? = null
    private var logStreamingJob: Job? = null
    private var latestCombinedLogsState: ProcessLogsState = ProcessLogsState.Idle

    fun start() {
        if (processPollingJob != null) return

        processPollingJob = scope.launch {
            loadProcesses()
            while (isActive) {
                delay(PROCESS_POLL_INTERVAL_MS)
                loadProcesses()
            }
        }
    }

    fun dispose() {
        processPollingJob?.cancel()
        logStreamingJob?.cancel()
        processPollingJob = null
        logStreamingJob = null
    }

    fun refreshNow() {
        scope.launch {
            loadProcesses(forceRefreshIndicator = true)
        }
    }

    fun selectProcess(processId: Int) {
        val groups = (uiState.dashboardState as? DashboardState.Ready)?.groups ?: return
        val selected = groups.flatMap(Pm2ProcessGroup::processes).firstOrNull { it.summary.pmId == processId } ?: return

        uiState = uiState.copy(
            selectedProcessId = processId,
            selectedProcess = selected,
            logsState = ProcessLogsState.Loading,
        )
        latestCombinedLogsState = ProcessLogsState.Loading
        observeLogs(selected.nameKey())
    }

    fun updateLogFilter(filter: LogFilter) {
        if (uiState.logFilter == filter) return

        uiState = uiState.copy(
            logFilter = filter,
            logsState = filterLogs(latestCombinedLogsState, filter),
        )
    }

    fun setFollowLogs(enabled: Boolean) {
        uiState = uiState.copy(followLogs = enabled)
    }

    fun clearLogs() {
        val selected = uiState.selectedProcess ?: return
        if (uiState.isClearingLogs) return

        scope.launch {
            uiState = uiState.copy(isClearingLogs = true)
            repository.clearLogs(selected.nameKey())
                .onSuccess {
                    latestCombinedLogsState = ProcessLogsState.Loading
                    uiState = uiState.copy(
                        logsState = ProcessLogsState.Loading,
                        isClearingLogs = false,
                    )
                    observeLogs(selected.nameKey())
                }
                .onFailure { exception ->
                    val error = org.compi.pm2dash.model.DashboardError(
                        kind = DashboardErrorKind.LogFileMissing,
                        title = "Could not clear PM2 logs",
                        message = "The selected PM2 log files could not be cleared.",
                        details = exception.message,
                    )
                    latestCombinedLogsState = ProcessLogsState.Error(error)
                    uiState = uiState.copy(
                        logsState = ProcessLogsState.Error(error),
                        isClearingLogs = false,
                    )
                }
        }
    }

    private suspend fun loadProcesses(forceRefreshIndicator: Boolean = false) {
        refreshMutex.withLock {
            if (forceRefreshIndicator) {
                uiState = uiState.copy(isRefreshing = true)
            }

            when (val result = repository.loadProcesses()) {
                is Pm2ProcessLoadResult.Error -> {
                    logStreamingJob?.cancel()
                    logStreamingJob = null
                    uiState = uiState.copy(
                        dashboardState = mapErrorToDashboardState(result.error),
                        selectedProcessId = null,
                        selectedProcess = null,
                        logsState = ProcessLogsState.Idle,
                        isClearingLogs = false,
                        isRefreshing = false,
                        lastUpdatedEpochMs = System.currentTimeMillis(),
                    )
                }

                is Pm2ProcessLoadResult.Success -> {
                    val processes = result.processes.sortedWith(
                        compareBy<Pm2ProcessDetails> { it.summary.name.lowercase() }
                            .thenBy { it.summary.pmId },
                    )

                    if (processes.isEmpty()) {
                        logStreamingJob?.cancel()
                        logStreamingJob = null
                        latestCombinedLogsState = ProcessLogsState.Idle
                        uiState = uiState.copy(
                            dashboardState = DashboardState.Empty(
                                title = "No PM2 processes",
                                message = "Run one or more apps with PM2 and they will show up here automatically.",
                            ),
                            selectedProcessId = null,
                            selectedProcess = null,
                            logsState = ProcessLogsState.Idle,
                            isClearingLogs = false,
                            isRefreshing = false,
                            lastUpdatedEpochMs = System.currentTimeMillis(),
                        )
                        return
                    }

                    val groups = processes.groupBy { it.summary.name }
                        .map { (name, instances) -> Pm2ProcessGroup(name = name, processes = instances) }
                        .sortedBy { it.name.lowercase() }

                    val selected = processes.firstOrNull { it.summary.pmId == uiState.selectedProcessId } ?: processes.first()
                    val selectedChanged = selected.summary.pmId != uiState.selectedProcessId

                    uiState = uiState.copy(
                        dashboardState = DashboardState.Ready(groups),
                        selectedProcessId = selected.summary.pmId,
                        selectedProcess = selected,
                        isClearingLogs = false,
                        isRefreshing = false,
                        lastUpdatedEpochMs = System.currentTimeMillis(),
                    )

                    if (selectedChanged || logStreamingJob == null) {
                        uiState = uiState.copy(logsState = ProcessLogsState.Loading)
                        latestCombinedLogsState = ProcessLogsState.Loading
                        observeLogs(selected.nameKey())
                    }
                }
            }
        }
    }

    private fun observeLogs(processName: String) {
        logStreamingJob?.cancel()
        logStreamingJob = scope.launch {
            repository.streamLogs(processName).collectLatest { logsState ->
                latestCombinedLogsState = logsState
                uiState = uiState.copy(logsState = filterLogs(logsState, uiState.logFilter))
            }
        }
    }

    private fun filterLogs(
        logsState: ProcessLogsState,
        filter: LogFilter,
    ): ProcessLogsState {
        return when (logsState) {
            is ProcessLogsState.Ready -> {
                val entries = when (filter) {
                    LogFilter.Combined -> logsState.entries
                    LogFilter.Stdout -> logsState.entries.filter { it.channel == LogChannel.Stdout }
                    LogFilter.Stderr -> logsState.entries.filter { it.channel == LogChannel.Stderr }
                }
                ProcessLogsState.Ready(entries)
            }

            else -> logsState
        }
    }

    private fun mapErrorToDashboardState(error: org.compi.pm2dash.model.DashboardError): DashboardState {
        return when (error.kind) {
            DashboardErrorKind.DaemonNotRunning -> DashboardState.Empty(
                title = error.title,
                message = error.message,
            )

            else -> DashboardState.Error(error)
        }
    }

    private fun Pm2ProcessDetails.nameKey(): String = summary.name
}

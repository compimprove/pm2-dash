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
import org.compi.pm2dash.model.CustomProcessGroup
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
    private val processGroupsRepository: ProcessGroupsRepository,
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
            loadCustomGroups()
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

    fun saveProcessList() {
        scope.launch {
            repository.saveProcessList()
                .onSuccess {
                    loadProcesses(forceRefreshIndicator = true)
                }
        }
    }

    fun resurrectProcessList() {
        scope.launch {
            repository.resurrectProcessList()
                .onSuccess {
                    loadProcesses(forceRefreshIndicator = true)
                }
        }
    }

    fun restartProcess(processId: Int) {
        scope.launch {
            repository.restartProcess(processId)
                .onSuccess {
                    loadProcesses(forceRefreshIndicator = true)
                }
        }
    }

    fun stopProcess(processId: Int) {
        scope.launch {
            repository.stopProcess(processId)
                .onSuccess {
                    loadProcesses(forceRefreshIndicator = true)
                }
        }
    }

    fun restartGroup(processIds: List<Int>) {
        if (processIds.isEmpty()) return

        scope.launch {
            processIds.distinct().forEach { processId ->
                repository.restartProcess(processId)
            }
            loadProcesses(forceRefreshIndicator = true)
        }
    }

    fun stopGroup(processIds: List<Int>) {
        if (processIds.isEmpty()) return

        scope.launch {
            processIds.distinct().forEach { processId ->
                repository.stopProcess(processId)
            }
            loadProcesses(forceRefreshIndicator = true)
        }
    }

    fun createGroup(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return

        updateCustomGroups { groups ->
            val existing = groups.find { it.name.equals(normalizedName, ignoreCase = true) }
            if (existing != null) {
                groups
            } else {
                groups + CustomProcessGroup(
                    name = normalizedName,
                    processNames = emptyList(),
                )
            }
        }
    }

    fun assignSelectedProcessToGroup(groupName: String) {
        val processName = uiState.selectedProcess?.summary?.name ?: return

        updateCustomGroups { groups ->
            val withoutProcess = groups.removeProcessName(processName)
            withoutProcess.map { group ->
                if (group.name == groupName) {
                    group.copy(processNames = (group.processNames + processName).distinct().sorted())
                } else {
                    group
                }
            }
        }
    }

    fun removeSelectedProcessFromCustomGroups() {
        val processName = uiState.selectedProcess?.summary?.name ?: return
        updateCustomGroups { groups -> groups.removeProcessName(processName) }
    }

    fun deleteGroup(groupName: String) {
        updateCustomGroups { groups -> groups.filterNot { it.name == groupName } }
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

                    val groups = buildProcessGroups(
                        processes = processes,
                        customGroups = uiState.customGroups,
                    )

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

    private suspend fun loadCustomGroups() {
        val groups = processGroupsRepository.loadGroups().getOrDefault(emptyList())
        uiState = uiState.copy(customGroups = groups.normalizeCustomGroups())
    }

    private fun updateCustomGroups(
        transform: (List<CustomProcessGroup>) -> List<CustomProcessGroup>,
    ) {
        scope.launch {
            refreshMutex.withLock {
                val updatedGroups = transform(uiState.customGroups).normalizeCustomGroups()
                processGroupsRepository.saveGroups(updatedGroups)
                    .onSuccess {
                        val currentDashboardState = uiState.dashboardState
                        val refreshedDashboardState = when (currentDashboardState) {
                            is DashboardState.Ready -> DashboardState.Ready(
                                buildProcessGroups(
                                    processes = currentDashboardState.groups.flatMap(Pm2ProcessGroup::processes).distinctBy { it.summary.pmId },
                                    customGroups = updatedGroups,
                                ),
                            )

                            else -> currentDashboardState
                        }

                        uiState = uiState.copy(
                            customGroups = updatedGroups,
                            dashboardState = refreshedDashboardState,
                        )
                    }
            }
        }
    }

    private fun Pm2ProcessDetails.nameKey(): String = summary.name
}

private fun buildProcessGroups(
    processes: List<Pm2ProcessDetails>,
    customGroups: List<CustomProcessGroup>,
): List<Pm2ProcessGroup> {
    val processByName = processes.groupBy { it.summary.name }
    val assignedNames = customGroups.flatMap(CustomProcessGroup::processNames).toSet()

    val customProcessGroups = customGroups.map { group ->
        val groupProcesses = group.processNames
            .flatMap { processByName[it].orEmpty() }
            .sortedBy { it.summary.pmId }

        Pm2ProcessGroup(
            name = group.name,
            processes = groupProcesses,
            isCustom = true,
        )
    }

    val automaticGroups = processes
        .filterNot { it.summary.name in assignedNames }
        .groupBy { it.summary.name }
        .map { (name, instances) ->
            Pm2ProcessGroup(
                name = name,
                processes = instances.sortedBy { it.summary.pmId },
            )
        }
        .sortedBy { it.name.lowercase() }

    return customProcessGroups + automaticGroups
}

private fun List<CustomProcessGroup>.removeProcessName(processName: String): List<CustomProcessGroup> {
    return map { group ->
        group.copy(processNames = group.processNames.filterNot { it == processName })
    }
}

private fun List<CustomProcessGroup>.normalizeCustomGroups(): List<CustomProcessGroup> {
    return mapNotNull { group ->
        val normalizedName = group.name.trim()
        if (normalizedName.isBlank()) {
            null
        } else {
            group.copy(
                name = normalizedName,
                processNames = group.processNames
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                    .sorted(),
            )
        }
    }
}

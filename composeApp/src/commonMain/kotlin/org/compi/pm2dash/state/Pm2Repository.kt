package org.compi.pm2dash.state

import kotlinx.coroutines.flow.Flow
import org.compi.pm2dash.model.DashboardError
import org.compi.pm2dash.model.Pm2ProcessDetails
import org.compi.pm2dash.model.ProcessLogsState

sealed interface Pm2ProcessLoadResult {
    data class Success(
        val processes: List<Pm2ProcessDetails>,
    ) : Pm2ProcessLoadResult

    data class Error(
        val error: DashboardError,
    ) : Pm2ProcessLoadResult
}

interface Pm2Repository {
    suspend fun loadProcesses(): Pm2ProcessLoadResult
    fun streamLogs(processName: String): Flow<ProcessLogsState>
    suspend fun clearLogs(processName: String): Result<Unit>
    suspend fun saveProcessList(): Result<Unit>
    suspend fun resurrectProcessList(): Result<Unit>
    suspend fun restartProcess(processId: Int): Result<Unit>
    suspend fun stopProcess(processId: Int): Result<Unit>
}

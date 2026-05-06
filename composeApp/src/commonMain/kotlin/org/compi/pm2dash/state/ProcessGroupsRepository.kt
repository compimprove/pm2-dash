package org.compi.pm2dash.state

import org.compi.pm2dash.model.CustomProcessGroup

interface ProcessGroupsRepository {
    suspend fun loadGroups(): Result<List<CustomProcessGroup>>
    suspend fun saveGroups(groups: List<CustomProcessGroup>): Result<Unit>
}

package org.compi.pm2dash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.compi.pm2dash.model.DashboardError
import org.compi.pm2dash.model.DashboardState
import org.compi.pm2dash.model.LogChannel
import org.compi.pm2dash.model.LogFilter
import org.compi.pm2dash.model.LogStreamEntry
import org.compi.pm2dash.model.CustomProcessGroup
import org.compi.pm2dash.model.Pm2DashboardUiState
import org.compi.pm2dash.model.Pm2ProcessDetails
import org.compi.pm2dash.model.Pm2ProcessGroup
import org.compi.pm2dash.model.Pm2ProcessStatus
import org.compi.pm2dash.model.ProcessLogsState
import org.compi.pm2dash.state.Pm2DashboardStore
import org.compi.pm2dash.state.Pm2Repository
import org.compi.pm2dash.state.ProcessGroupsRepository
import org.compi.pm2dash.theme.Pm2DashTheme
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun App(
    repository: Pm2Repository,
    processGroupsRepository: ProcessGroupsRepository,
) {
    val store = rememberDashboardStore(repository, processGroupsRepository)
    val uiState = store.uiState

    LaunchedEffect(store) {
        store.start()
    }

    DisposableEffect(store) {
        onDispose {
            store.dispose()
        }
    }

    Pm2DashTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            DashboardScreen(
                uiState = uiState,
                onSaveProcessList = store::saveProcessList,
                onResurrectProcessList = store::resurrectProcessList,
                onSelectProcess = store::selectProcess,
                onFilterChange = store::updateLogFilter,
                onFollowChange = store::setFollowLogs,
                onClearLogs = store::clearLogs,
                onRestartProcess = store::restartProcess,
                onStopProcess = store::stopProcess,
                onRestartGroup = store::restartGroup,
                onStopGroup = store::stopGroup,
                onCreateGroup = store::createGroup,
                onAssignToGroup = store::assignSelectedProcessToGroup,
                onRemoveFromCustomGroups = store::removeSelectedProcessFromCustomGroups,
                onDeleteGroup = store::deleteGroup,
            )
        }
    }
}

@Composable
private fun rememberDashboardStore(
    repository: Pm2Repository,
    processGroupsRepository: ProcessGroupsRepository,
): Pm2DashboardStore {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember(repository, processGroupsRepository, scope) {
        Pm2DashboardStore(
            repository = repository,
            processGroupsRepository = processGroupsRepository,
            scope = scope,
        )
    }
}

@Composable
private fun DashboardScreen(
    uiState: Pm2DashboardUiState,
    onSaveProcessList: () -> Unit,
    onResurrectProcessList: () -> Unit,
    onSelectProcess: (Int) -> Unit,
    onFilterChange: (LogFilter) -> Unit,
    onFollowChange: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onRestartProcess: (Int) -> Unit,
    onStopProcess: (Int) -> Unit,
    onRestartGroup: (List<Int>) -> Unit,
    onStopGroup: (List<Int>) -> Unit,
    onCreateGroup: (String) -> Unit,
    onAssignToGroup: (String) -> Unit,
    onRemoveFromCustomGroups: () -> Unit,
    onDeleteGroup: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(18.dp),
    ) {
        DashboardHeader(
            onSaveProcessList = onSaveProcessList,
            onResurrectProcessList = onResurrectProcessList,
        )
        Spacer(Modifier.height(16.dp))

        when (val state = uiState.dashboardState) {
            DashboardState.Loading -> LoadingBlock("Loading PM2 processes...")
            is DashboardState.Empty -> EmptyBlock(state.title, state.message)
            is DashboardState.Error -> ErrorBlock(state.error)
            is DashboardState.Ready -> {
                DashboardContent(
                    groups = state.groups,
                    uiState = uiState,
                    onSelectProcess = onSelectProcess,
                    onFilterChange = onFilterChange,
                    onFollowChange = onFollowChange,
                    onClearLogs = onClearLogs,
                    onRestartProcess = onRestartProcess,
                    onStopProcess = onStopProcess,
                    onRestartGroup = onRestartGroup,
                    onStopGroup = onStopGroup,
                    onCreateGroup = onCreateGroup,
                    onAssignToGroup = onAssignToGroup,
                    onRemoveFromCustomGroups = onRemoveFromCustomGroups,
                    onDeleteGroup = onDeleteGroup,
                )
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    onSaveProcessList: () -> Unit,
    onResurrectProcessList: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "PM2 Dash",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Dark-only local monitor for PM2 processes and logs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onSaveProcessList) {
                Text("Save")
            }
            OutlinedButton(onClick = onResurrectProcessList) {
                Text("Restore")
            }
        }
    }
}

@Composable
private fun DashboardContent(
    groups: List<Pm2ProcessGroup>,
    uiState: Pm2DashboardUiState,
    onSelectProcess: (Int) -> Unit,
    onFilterChange: (LogFilter) -> Unit,
    onFollowChange: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onRestartProcess: (Int) -> Unit,
    onStopProcess: (Int) -> Unit,
    onRestartGroup: (List<Int>) -> Unit,
    onStopGroup: (List<Int>) -> Unit,
    onCreateGroup: (String) -> Unit,
    onAssignToGroup: (String) -> Unit,
    onRemoveFromCustomGroups: () -> Unit,
    onDeleteGroup: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProcessSidebar(
            groups = groups,
            customGroups = uiState.customGroups,
            selectedProcessId = uiState.selectedProcessId,
            onSelectProcess = onSelectProcess,
            onAssignToGroup = onAssignToGroup,
            onRemoveFromCustomGroups = onRemoveFromCustomGroups,
            onRestartProcess = onRestartProcess,
            onStopProcess = onStopProcess,
            onRestartGroup = onRestartGroup,
            onStopGroup = onStopGroup,
            modifier = Modifier.width(310.dp).fillMaxHeight(),
        )

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProcessWorkspace(
                selectedProcess = uiState.selectedProcess,
                logsState = uiState.logsState,
                logFilter = uiState.logFilter,
                followLogs = uiState.followLogs,
                isClearingLogs = uiState.isClearingLogs,
                customGroups = uiState.customGroups,
                onFilterChange = onFilterChange,
                onFollowChange = onFollowChange,
                onClearLogs = onClearLogs,
                onCreateGroup = onCreateGroup,
                onAssignToGroup = onAssignToGroup,
                onRemoveFromCustomGroups = onRemoveFromCustomGroups,
                onDeleteGroup = onDeleteGroup,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ProcessSidebar(
    groups: List<Pm2ProcessGroup>,
    customGroups: List<CustomProcessGroup>,
    selectedProcessId: Int?,
    onSelectProcess: (Int) -> Unit,
    onAssignToGroup: (String) -> Unit,
    onRemoveFromCustomGroups: () -> Unit,
    onRestartProcess: (Int) -> Unit,
    onStopProcess: (Int) -> Unit,
    onRestartGroup: (List<Int>) -> Unit,
    onStopGroup: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Text(
                text = "Processes",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { "${if (it.isCustom) "custom" else "auto"}-${it.name}" }) { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = group.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = { onRestartGroup(group.processes.map { it.summary.pmId }) },
                                    enabled = group.processes.isNotEmpty(),
                                ) {
                                    Text(
                                        text = "↻",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                IconButton(
                                    onClick = { onStopGroup(group.processes.map { it.summary.pmId }) },
                                    enabled = group.processes.isNotEmpty(),
                                ) {
                                    Text(
                                        text = "■",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                StatusPill(
                                    text = "${group.processes.size}",
                                    container = MaterialTheme.colorScheme.surfaceVariant,
                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        group.processes.forEach { process ->
                            ProcessListItem(
                                process = process,
                                customGroups = customGroups,
                                selected = selectedProcessId == process.summary.pmId,
                                onClick = { onSelectProcess(process.summary.pmId) },
                                onAssignToGroup = { groupName ->
                                    onSelectProcess(process.summary.pmId)
                                    onAssignToGroup(groupName)
                                },
                                onRemoveFromGroup = {
                                    onSelectProcess(process.summary.pmId)
                                    onRemoveFromCustomGroups()
                                },
                                onRestart = { onRestartProcess(process.summary.pmId) },
                                onStop = { onStopProcess(process.summary.pmId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomGroupListItem(
    group: CustomProcessGroup,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                OutlinedButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (group.processNames.isEmpty()) {
                    Text(
                        text = "No process names assigned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    group.processNames.forEach { processName ->
                        StatusPill(
                            text = processName,
                            container = MaterialTheme.colorScheme.surface,
                            content = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${group.processNames.size} name${if (group.processNames.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ProcessListItem(
    process: Pm2ProcessDetails,
    customGroups: List<CustomProcessGroup>,
    selected: Boolean,
    onClick: () -> Unit,
    onAssignToGroup: (String) -> Unit,
    onRemoveFromGroup: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val assignedGroup = remember(customGroups, process.summary.name) {
        customGroups.firstOrNull { process.summary.name in it.processNames }?.name
    }
    var contextMenuExpanded by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, MaterialTheme.shapes.medium)
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.buttons.isSecondaryPressed) {
                        contextMenuExpanded = true
                    }
                },
            onClick = onClick,
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProcessStatusDot(process.summary.status)
                        Text(
                            text = process.summary.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompactIconButton(onClick = onRestart) {
                            Text(
                                text = "↻",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        CompactIconButton(onClick = onStop) {
                            Text(
                                text = "■",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Text(
                    text = "${formatCpu(process.summary.cpuPercent)} CPU  •  ${formatMemory(process.summary.memoryBytes)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = contextMenuExpanded,
            onDismissRequest = { contextMenuExpanded = false },
        ) {
            customGroups.forEach { group ->
                DropdownMenuItem(
                    text = { Text("Add to ${group.name}") },
                    onClick = {
                        contextMenuExpanded = false
                        onAssignToGroup(group.name)
                    },
                )
            }

            if (customGroups.isNotEmpty() && assignedGroup != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (assignedGroup != null) {
                DropdownMenuItem(
                    text = { Text("Remove from $assignedGroup") },
                    onClick = {
                        contextMenuExpanded = false
                        onRemoveFromGroup()
                    },
                )
            }
        }
    }
}

private enum class ProcessTab {
    Logs,
    Details,
    Groups,
}

@Composable
private fun ProcessWorkspace(
    selectedProcess: Pm2ProcessDetails?,
    logsState: ProcessLogsState,
    logFilter: LogFilter,
    followLogs: Boolean,
    isClearingLogs: Boolean,
    customGroups: List<CustomProcessGroup>,
    onFilterChange: (LogFilter) -> Unit,
    onFollowChange: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onCreateGroup: (String) -> Unit,
    onAssignToGroup: (String) -> Unit,
    onRemoveFromCustomGroups: () -> Unit,
    onDeleteGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(ProcessTab.Logs.name) }
    val activeTab = ProcessTab.valueOf(selectedTab)

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PrimaryTabRow(selectedTabIndex = activeTab.ordinal) {
                ProcessTab.entries.forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { selectedTab = tab.name },
                        text = {
                            Text(
                                when (tab) {
                                    ProcessTab.Logs -> "Logs"
                                    ProcessTab.Details -> "Details"
                                    ProcessTab.Groups -> "Groups"
                                },
                            )
                        },
                    )
                }
            }

            when (activeTab) {
                ProcessTab.Logs -> LogsPanelContent(
                    selectedProcess = selectedProcess,
                    logsState = logsState,
                    logFilter = logFilter,
                    followLogs = followLogs,
                    isClearingLogs = isClearingLogs,
                    onFilterChange = onFilterChange,
                    onFollowChange = onFollowChange,
                    onClearLogs = onClearLogs,
                    modifier = Modifier.fillMaxSize(),
                )

                ProcessTab.Details -> ProcessDetailsPanel(
                    selectedProcess = selectedProcess,
                    modifier = Modifier.fillMaxSize(),
                )

                ProcessTab.Groups -> GroupsPanelContent(
                    customGroups = customGroups,
                    onCreateGroup = onCreateGroup,
                    onDeleteGroup = onDeleteGroup,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun GroupsPanelContent(
    customGroups: List<CustomProcessGroup>,
    onCreateGroup: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newGroupName by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Custom Groups",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Create or delete groups here. Assign and remove services from the process list with right click.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newGroupName,
                onValueChange = { newGroupName = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("New group") },
            )
            Button(
                onClick = {
                    onCreateGroup(newGroupName)
                    newGroupName = ""
                },
                enabled = newGroupName.isNotBlank(),
            ) {
                Text("Create")
            }
        }

        if (customGroups.isEmpty()) {
            EmptyInlineCard(
                title = "No custom groups",
                message = "Create a group here, then right click a service in the process list to add it.",
                modifier = Modifier.fillMaxSize(),
            )
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(customGroups, key = { it.name }) { group ->
                CustomGroupListItem(
                    group = group,
                    onDelete = { onDeleteGroup(group.name) },
                )
            }
        }
    }
}

@Composable
private fun ProcessDetailsPanel(
    selectedProcess: Pm2ProcessDetails?,
    modifier: Modifier = Modifier,
) {
    if (selectedProcess == null) {
        Spacer(modifier)
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProcessStatGrid(selectedProcess)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ProcessStatGrid(
    process: Pm2ProcessDetails,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard("CPU", formatCpu(process.summary.cpuPercent))
        StatCard("Memory", formatMemory(process.summary.memoryBytes))
        StatCard("Restarts", process.summary.restartCount.toString())
        StatCard("PID", process.summary.pid?.toString() ?: "n/a")
        StatCard("Uptime", formatUptime(process.summary.uptimeStartedAtEpochMs))
        StatCard("Namespace", process.namespace ?: "default")
        StatCard("Interpreter", process.interpreter ?: "n/a")
        StatCard("Working Dir", process.workingDirectory ?: "n/a", wide = true)
        StatCard("Watch", if (process.watchEnabled) "Enabled" else "Disabled")
        StatCard("Instances", process.instanceCount?.toString() ?: "n/a")
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    wide: Boolean = false,
) {
    Surface(
        modifier = Modifier
            .width(if (wide) 320.dp else 150.dp)
            .clip(MaterialTheme.shapes.medium),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LogsPanelContent(
    selectedProcess: Pm2ProcessDetails?,
    logsState: ProcessLogsState,
    logFilter: LogFilter,
    followLogs: Boolean,
    isClearingLogs: Boolean,
    onFilterChange: (LogFilter) -> Unit,
    onFollowChange: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable(selectedProcess?.summary?.pmId) { mutableStateOf("") }
    var searchJumpCount by rememberSaveable(selectedProcess?.summary?.pmId) { mutableStateOf(0) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Logs",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = selectedProcess?.summary?.name ?: "Select a process to open PM2 logs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        searchJumpCount = 0
                    },
                    modifier = Modifier
                        .width(240.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && searchQuery.isNotBlank()) {
                                searchJumpCount += 1
                                true
                            } else {
                                false
                            }
                        },
                    singleLine = true,
                    label = { Text("Search logs") },
                )
                OutlinedButton(
                    onClick = onClearLogs,
                    enabled = selectedProcess != null && !isClearingLogs,
                ) {
                    Text(if (isClearingLogs) "Clearing..." else "Clear logs")
                }
                ToggleChip(
                    text = "All",
                    selected = logFilter == LogFilter.Combined,
                    onClick = { onFilterChange(LogFilter.Combined) },
                )
                ToggleChip(
                    text = "Stdout",
                    selected = logFilter == LogFilter.Stdout,
                    onClick = { onFilterChange(LogFilter.Stdout) },
                )
                ToggleChip(
                    text = "Stderr",
                    selected = logFilter == LogFilter.Stderr,
                    onClick = { onFilterChange(LogFilter.Stderr) },
                )
                ToggleChip(
                    text = if (followLogs) "Follow on" else "Follow off",
                    selected = followLogs,
                    onClick = { onFollowChange(!followLogs) },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when (logsState) {
            ProcessLogsState.Idle -> EmptyInlineCard(
                title = "Logs idle",
                message = "Choose a PM2 process to begin watching its PM2 log files.",
                modifier = Modifier.fillMaxSize(),
            )

            ProcessLogsState.Loading -> LoadingBlock("Reading PM2 log files...", modifier = Modifier.fillMaxSize())
            is ProcessLogsState.Missing -> EmptyInlineCard(
                title = "Logs not available",
                message = logsState.message,
                modifier = Modifier.fillMaxSize(),
            )

            is ProcessLogsState.Error -> ErrorInlineCard(logsState.error, modifier = Modifier.fillMaxSize())
            is ProcessLogsState.Ready -> LogEntriesList(
                entries = logsState.entries,
                followLogs = followLogs,
                searchQuery = searchQuery,
                searchJumpCount = searchJumpCount,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LogEntriesList(
    entries: List<LogStreamEntry>,
    followLogs: Boolean,
    searchQuery: String,
    searchJumpCount: Int,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val matchingIndices = remember(entries, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            entries.mapIndexedNotNull { index, entry ->
                if (entry.message.contains(query, ignoreCase = true)) index else null
            }
        }
    }
    val selectedMatchEntryIndex = if (matchingIndices.isNotEmpty() && searchJumpCount > 0) {
        val offset = (searchJumpCount - 1) % matchingIndices.size
        matchingIndices[matchingIndices.lastIndex - offset]
    } else {
        null
    }

    LaunchedEffect(entries.size, followLogs, searchQuery) {
        if (followLogs && entries.isNotEmpty() && searchQuery.isBlank()) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    LaunchedEffect(searchJumpCount, matchingIndices) {
        if (searchJumpCount > 0 && selectedMatchEntryIndex != null) {
            listState.animateScrollToItem(selectedMatchEntryIndex)
        }
    }

    if (entries.isEmpty()) {
        EmptyInlineCard(
            title = "No log lines yet",
            message = "The selected PM2 process has no recent log lines in the selected channel.",
            modifier = modifier,
        )
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF0A0F14),
        shape = MaterialTheme.shapes.large,
    ) {
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(entries, key = { _, entry -> entry.id }) { entryIndex, entry ->
                    val isActiveMatch = entryIndex == selectedMatchEntryIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isActiveMatch) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = MaterialTheme.shapes.small,
                            )
                            .clip(MaterialTheme.shapes.small)
                            .background(Color.Transparent)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = entry.observedAtLocalTime,
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (entry.channel == LogChannel.Stdout) "OUT" else "ERR",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                            color = if (entry.channel == LogChannel.Stdout) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        Text(
                            text = highlightSearchMatch(
                                text = entry.message,
                                query = searchQuery,
                                highlightColor = Color(0x66FFD54F),
                            ),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick) { Text(text) }
    }
}

@Composable
private fun ProcessStatusDot(
    status: Pm2ProcessStatus,
) {
    val color = when (status) {
        Pm2ProcessStatus.Online -> Color(0xFF54D179)
        Pm2ProcessStatus.Stopped -> Color(0xFF8D97A6)
        Pm2ProcessStatus.Errored -> Color(0xFFFF6B7A)
        Pm2ProcessStatus.Launching -> Color(0xFFFFD166)
        Pm2ProcessStatus.Unknown -> Color(0xFF7FA8E8)
    }

    Box(
        modifier = Modifier
            .width(8.dp)
            .height(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.width(28.dp).height(28.dp),
    ) {
        content()
    }
}

@Composable
private fun StatusPill(
    text: String,
    container: Color,
    content: Color,
) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = content,
        )
    }
}

@Composable
private fun LoadingBlock(
    text: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    EmptyInlineCard(
        title = "Loading",
        message = text,
        modifier = modifier,
    )
}

@Composable
private fun EmptyBlock(
    title: String,
    message: String,
) {
    EmptyInlineCard(
        title = title,
        message = message,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ErrorBlock(
    error: DashboardError,
) {
    ErrorInlineCard(error = error, modifier = Modifier.fillMaxSize())
}

@Composable
private fun EmptyInlineCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorInlineCard(
    error: DashboardError,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = error.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                error.details?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

private fun formatCpu(value: Double): String = "${value.roundToInt()}%"

private fun formatMemory(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val megabytes = bytes / (1024.0 * 1024.0)
    val rounded = (megabytes * 10).roundToInt() / 10.0
    return "${rounded} MB"
}

private fun formatUptime(startedAtEpochMs: Long?): String {
    if (startedAtEpochMs == null) return "n/a"
    val uptime = (System.currentTimeMillis() - startedAtEpochMs).coerceAtLeast(0L).milliseconds
    return buildString {
        val days = uptime.inWholeDays
        val hours = uptime.inWholeHours % 24
        val minutes = uptime.inWholeMinutes % 60
        if (days > 0) append("${days}d ")
        if (hours > 0 || days > 0) append("${hours}h ")
        append("${minutes}m")
    }
}

private fun formatRelativeTime(epochMs: Long): String {
    val delta = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L).milliseconds
    return when {
        delta.inWholeSeconds < 5 -> "just now"
        delta.inWholeMinutes < 1 -> "${delta.inWholeSeconds}s ago"
        delta.inWholeHours < 1 -> "${delta.inWholeMinutes}m ago"
        else -> "${delta.inWholeHours}h ago"
    }
}

private fun highlightSearchMatch(
    text: String,
    query: String,
    highlightColor: Color,
): AnnotatedString = buildAnnotatedString {
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }

    val needle = query.lowercase()
    val haystack = text.lowercase()
    var startIndex = 0

    while (startIndex < text.length) {
        val matchIndex = haystack.indexOf(needle, startIndex = startIndex)
        if (matchIndex < 0) {
            append(text.substring(startIndex))
            break
        }

        if (matchIndex > startIndex) {
            append(text.substring(startIndex, matchIndex))
        }

        pushStyle(
            SpanStyle(
                background = highlightColor,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        append(text.substring(matchIndex, matchIndex + query.length))
        pop()
        startIndex = matchIndex + query.length
    }
}

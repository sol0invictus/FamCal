package com.famcal.app.ui.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famcal.app.data.model.TaskArea
import com.famcal.app.util.TaskStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onAddTask: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenStarterPacks: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = if (message.undo != null) "Undo" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) message.undo?.let(viewModel::undo)
        }
    }

    val openRequest by viewModel.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest) {
        openRequest?.let { request ->
            viewModel.handleOpenRequest(request)?.let(onOpenTask)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                actions = {
                    IconButton(onClick = onOpenStarterPacks) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Starter packs")
                    }
                    IconButton(onClick = onOpenLeaderboard) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = "Points leaderboard")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Icon(Icons.Filled.Add, contentDescription = "New task")
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.hasAnyTasks) FilterRow(state, viewModel)
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                !state.hasAnyTasks -> EmptyTasks(onAddTask, onOpenStarterPacks)
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (state.sections.none { it.status != TaskStatus.DONE }) {
                        item {
                            Text(
                                "Nothing to do here. 🎉",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                            )
                        }
                    }
                    state.sections.forEach { section ->
                        item(key = "header-${section.status}") {
                            SectionHeader(section)
                        }
                        items(section.tasks, key = { it.id }) { task ->
                            TaskRow(
                                task = task,
                                today = state.today,
                                membersByUid = state.membersByUid,
                                currentUid = state.currentUid,
                                onCheck = { viewModel.complete(task) },
                                onClick = { onOpenTask(task.id) },
                                onClaim = { viewModel.claim(task) },
                            )
                        }
                    }
                    item(key = "done-toggle") {
                        TextButton(
                            onClick = viewModel::toggleShowDone,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 80.dp),
                        ) {
                            Text(if (state.showDone) "Hide finished one-off tasks" else "Show finished one-off tasks")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(section: TaskSection) {
    Column {
        HorizontalDivider()
        Text(
            text = "${section.title} · ${section.tasks.size}",
            style = MaterialTheme.typography.titleSmall,
            color = if (section.status == TaskStatus.OVERDUE) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(state: TasksUiState, viewModel: TasksViewModel) {
    var areaMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.filter == TaskFilter.EVERYONE,
            onClick = { viewModel.setFilter(TaskFilter.EVERYONE) },
            label = { Text("Everyone") },
        )
        FilterChip(
            selected = state.filter == TaskFilter.FOR_ME,
            onClick = { viewModel.setFilter(TaskFilter.FOR_ME) },
            label = { Text("For me") },
        )
        Box {
            FilterChip(
                selected = state.area != null,
                onClick = { areaMenu = true },
                label = {
                    Text(state.area?.let { "${TaskArea.emoji(it)} ${TaskArea.label(it)}" } ?: "All areas")
                },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = areaMenu, onDismissRequest = { areaMenu = false }) {
                DropdownMenuItem(
                    text = { Text("All areas") },
                    onClick = { viewModel.setArea(null); areaMenu = false },
                )
                state.areasInUse.forEach { area ->
                    DropdownMenuItem(
                        text = { Text("${TaskArea.emoji(area)} ${TaskArea.label(area)}") },
                        onClick = { viewModel.setArea(area); areaMenu = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTasks(onAddTask: () -> Unit, onOpenStarterPacks: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🧹", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text("Keep track of chores and upkeep", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add repeating jobs like the HVAC filter or weekly cleaning, share them out, " +
                "and check them off when they're done.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenStarterPacks) { Text("Browse starter packs") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAddTask) { Text("Add a task") }
    }
}

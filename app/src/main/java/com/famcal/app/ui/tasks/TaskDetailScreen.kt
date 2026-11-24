package com.famcal.app.ui.tasks

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famcal.app.data.model.HouseholdTask
import com.famcal.app.data.model.TaskArea
import com.famcal.app.data.model.TaskRepeat
import com.famcal.app.util.TaskSchedule
import com.famcal.app.util.TaskStatus
import com.famcal.app.util.toLocalDate
import com.famcal.app.util.toLocalDateTime
import java.time.format.DateTimeFormatter

private val historyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message.text,
                actionLabel = if (message.undo != null) "Undo" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) message.undo?.let(viewModel::undo)
        }
    }
    LaunchedEffect(state.isGone) { if (state.isGone) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(viewModel.taskId) }, enabled = state.task != null) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = state.task != null) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val task = state.task
        if (task == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            item { Overview(task, state, viewModel) }
            item { Details(task) }
            item {
                Text(
                    "History",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                )
            }
            if (state.history.isEmpty()) {
                item {
                    Text(
                        "Not done yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(state.history, key = { it.id }) { completion ->
                val member = state.membersByUid[completion.doneBy]
                val who = if (completion.doneBy == state.currentUid) "You" else member?.displayName ?: "Someone"
                val doneDate = completion.doneAt.toLocalDate()
                val due = TaskSchedule.parse(completion.dueDate)
                val lateness = when {
                    due == null -> ""
                    doneDate.isAfter(due) -> " · ${java.time.temporal.ChronoUnit.DAYS.between(due, doneDate)} days late"
                    else -> " · on time"
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(memberColor(member)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("$who · ${completion.doneAt.toLocalDateTime().toLocalDate().format(historyFormatter)}")
                        Text(
                            "⭐ ${completion.points}$lateness",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this task?") },
            text = { Text("Points already earned for it are kept.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Overview(task: HouseholdTask, state: TaskDetailUiState, viewModel: TaskDetailViewModel) {
    val status = task.status(state.today)
    val done = task.isDone()
    var snoozeMenu by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${TaskArea.emoji(task.area)} ${TaskArea.label(task.area)}", style = MaterialTheme.typography.labelLarge)
        Text(task.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            if (done) "Finished" else TaskSchedule.dueLabel(TaskSchedule.parse(task.dueDate), state.today),
            style = MaterialTheme.typography.titleMedium,
            color = if (status == TaskStatus.OVERDUE) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
        Text("👤 ${assigneeLabel(task, state.membersByUid, state.currentUid)}")
        if (task.rotation.size > 1) {
            val names = task.rotation.map { uid ->
                if (uid == state.currentUid) "You" else state.membersByUid[uid]?.displayName ?: "?"
            }
            Text(
                "🔄 Takes turns: ${names.joinToString(" → ")}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text("🔁 ${TaskSchedule.repeatLabel(task.repeatMode, task.repeatUnit, task.repeatInterval)}")
        Text("⭐ ${task.points} ${if (task.points == 1) "point" else "points"}")
        historyLine(task, state.membersByUid, state.currentUid, state.today)
            ?.takeIf { task.lastDoneAt != null }
            ?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        Spacer(Modifier.height(8.dp))
        if (done) {
            OutlinedButton(onClick = viewModel::reopen, modifier = Modifier.fillMaxWidth()) { Text("Reopen") }
        } else {
            Button(onClick = viewModel::complete, modifier = Modifier.fillMaxWidth()) { Text("Mark done") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (task.assignedTo.isBlank()) {
                    OutlinedButton(onClick = viewModel::claim, modifier = Modifier.weight(1f)) { Text("Claim") }
                }
                if (task.repeatMode != TaskRepeat.NONE) {
                    OutlinedButton(onClick = viewModel::skip, modifier = Modifier.weight(1f)) { Text("Skip once") }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { snoozeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Snooze") }
                    DropdownMenu(expanded = snoozeMenu, onDismissRequest = { snoozeMenu = false }) {
                        listOf(1L to "Tomorrow", 3L to "In 3 days", 7L to "In a week").forEach { (days, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { snoozeMenu = false; viewModel.snooze(days) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Details(task: HouseholdTask) {
    if (task.notes.isBlank() && task.cost.isBlank() && task.vendorName.isBlank() && task.vendorPhone.isBlank()) return
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (task.notes.isNotBlank()) Text(task.notes)
            if (task.cost.isNotBlank()) Text("💲 ${task.cost}")
            if (task.vendorName.isNotBlank() || task.vendorPhone.isNotBlank()) {
                if (task.notes.isNotBlank() || task.cost.isNotBlank()) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (task.vendorName.isNotBlank()) Text(task.vendorName, style = MaterialTheme.typography.titleSmall)
                        if (task.vendorPhone.isNotBlank()) Text(task.vendorPhone, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (task.vendorPhone.isNotBlank()) {
                        IconButton(
                            onClick = {
                                // Opens the dialer with the number filled in (no call permission needed).
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${task.vendorPhone}")))
                            },
                        ) { Icon(Icons.Filled.Call, contentDescription = "Call ${task.vendorName}") }
                    }
                }
            }
        }
    }
}

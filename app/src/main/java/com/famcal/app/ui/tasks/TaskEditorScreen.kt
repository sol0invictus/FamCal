package com.famcal.app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famcal.app.data.model.Recurrence
import com.famcal.app.data.model.TaskArea
import com.famcal.app.data.model.TaskRepeat
import com.famcal.app.ui.common.DatePickerSheet
import com.famcal.app.ui.common.OptionSelector
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dueFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val repeatUnits = listOf(Recurrence.DAILY, Recurrence.WEEKLY, Recurrence.MONTHLY, Recurrence.YEARLY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    onClose: () -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    var pickDate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit task" else "New task") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("What needs doing?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OptionSelector(
                label = "Area",
                options = TaskArea.all,
                optionLabel = { "${TaskArea.emoji(it)} ${TaskArea.label(it)}" },
                selected = state.area,
                onSelect = viewModel::onAreaChange,
            )

            HorizontalDivider()
            Text("Who", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Take turns")
                    Text(
                        "Passes to the next person each time it's done",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.rotate, onCheckedChange = viewModel::onRotateChange)
            }
            if (state.rotate) {
                Text(
                    "Tap people in the order they take turns:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                members.forEach { member ->
                    val position = state.rotation.indexOf(member.uid)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleInRotation(member.uid) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = position >= 0, onCheckedChange = { viewModel.toggleInRotation(member.uid) })
                        Box(
                            Modifier.size(12.dp).clip(CircleShape).background(memberColor(member)),
                        )
                        Text(
                            member.displayName.ifBlank { member.email },
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                        if (position >= 0) {
                            Text("#${position + 1}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else {
                OptionSelector(
                    label = "Assigned to",
                    options = listOf("") + members.map { it.uid },
                    optionLabel = { uid ->
                        if (uid.isBlank()) "Anyone (claimable)" else
                            members.firstOrNull { it.uid == uid }?.displayName ?: "Someone"
                    },
                    selected = state.assignedTo,
                    onSelect = viewModel::onAssigneeChange,
                )
            }

            HorizontalDivider()
            Text("When", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.repeatMode == TaskRepeat.NONE) "Due" else "Next due",
                    modifier = Modifier.weight(1f),
                )
                if (state.dueDate != null && state.repeatMode == TaskRepeat.NONE) {
                    IconButton(onClick = { viewModel.onDueDateChange(null) }) {
                        Icon(Icons.Filled.Close, contentDescription = "No due date")
                    }
                }
                OutlinedButton(onClick = { pickDate = true }) {
                    Text(state.dueDate?.format(dueFormatter) ?: "No date")
                }
            }
            OptionSelector(
                label = "Repeat",
                options = TaskRepeat.all,
                optionLabel = TaskRepeat::label,
                selected = state.repeatMode,
                onSelect = viewModel::onRepeatModeChange,
            )
            if (state.repeatMode != TaskRepeat.NONE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Every", modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = if (state.repeatInterval == 0) "" else state.repeatInterval.toString(),
                        onValueChange = viewModel::onIntervalChange,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                        modifier = Modifier.width(72.dp),
                    )
                    Spacer12()
                    OptionSelectorCompact(
                        options = repeatUnits,
                        label = { Recurrence.unit(it, state.repeatInterval.coerceAtLeast(1)) },
                        selected = state.repeatUnit,
                        onSelect = viewModel::onRepeatUnitChange,
                    )
                }
                Text(
                    if (state.repeatMode == TaskRepeat.AFTER_DONE) {
                        "Counted from the day it's actually done — good for filters, oil changes, haircuts."
                    } else {
                        "Stays on fixed dates even if done late — good for trash day or bills."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Points")
                    Text(
                        "Earned by whoever does it (see the leaderboard)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.onPointsChange(state.points - 1) }, enabled = state.points > 1) {
                    Icon(Icons.Filled.Remove, contentDescription = "Fewer points")
                }
                Text("⭐ ${state.points}", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { viewModel.onPointsChange(state.points + 1) }) {
                    Icon(Icons.Filled.Add, contentDescription = "More points")
                }
            }

            HorizontalDivider()
            Text("Details (optional)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes — e.g. filter size 16x25x1") },
                modifier = Modifier.fillMaxWidth().height(110.dp),
            )
            OutlinedTextField(
                value = state.cost,
                onValueChange = viewModel::onCostChange,
                label = { Text("Typical cost") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.vendorName,
                onValueChange = viewModel::onVendorNameChange,
                label = { Text("Who does it (company or person)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.vendorPhone,
                onValueChange = viewModel::onVendorPhoneChange,
                label = { Text("Their phone") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.rotate && state.rotation.isEmpty()) {
                Text(
                    "Pick at least one person to take turns.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = { viewModel.save(onClose) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) {
                Text(if (state.isEditing) "Save" else "Add task")
            }
        }
    }

    if (pickDate) {
        DatePickerSheet(
            initial = state.dueDate ?: LocalDate.now(),
            onConfirm = { viewModel.onDueDateChange(it); pickDate = false },
            onDismiss = { pickDate = false },
        )
    }
}

@Composable
private fun Spacer12() = Box(Modifier.width(12.dp))

@Composable
private fun <T> OptionSelectorCompact(
    options: List<T>,
    label: (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(label(selected)) }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

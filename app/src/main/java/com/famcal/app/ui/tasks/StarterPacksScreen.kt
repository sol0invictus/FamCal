package com.famcal.app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.model.TaskArea
import com.famcal.app.data.task.StarterPack
import com.famcal.app.data.task.StarterPacks
import com.famcal.app.data.task.TaskRepository
import com.famcal.app.data.task.TaskTemplate
import com.famcal.app.util.TaskSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class StarterPacksUiState(
    val selected: Set<TaskTemplate> = emptySet(),
    /** Titles already in the family's task list (shown as "added"). */
    val existingTitles: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class StarterPacksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
) : ViewModel() {
    private val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId required" }

    private val _state = MutableStateFlow(StarterPacksUiState())
    val state: StateFlow<StarterPacksUiState> = _state.asStateFlow()

    val existingTitles: StateFlow<Set<String>> = taskRepository.observeTasks(familyId)
        .map { tasks -> tasks.map { it.title.lowercase() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggle(template: TaskTemplate) = _state.value.let { s ->
        _state.value = s.copy(selected = if (template in s.selected) s.selected - template else s.selected + template)
    }

    /** Selects every not-yet-added task in [pack] (or clears them if all are selected). */
    fun togglePack(pack: StarterPack) = _state.value.let { s ->
        val addable = pack.tasks.filter { it.title.lowercase() !in existingTitles.value }
        val allOn = addable.all { it in s.selected }
        _state.value = s.copy(selected = if (allOn) s.selected - addable.toSet() else s.selected + addable)
    }

    fun add(onDone: () -> Unit) {
        val picks = _state.value.selected
        if (picks.isEmpty()) return
        _state.value = _state.value.copy(isSaving = true, error = null)
        val today = LocalDate.now()
        viewModelScope.launch {
            taskRepository.createTasks(familyId, picks.map { it.toTask(today) })
                .onSuccess { onDone() }
                .onFailure { _state.value = _state.value.copy(isSaving = false, error = "Couldn't add the tasks.") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarterPacksScreen(onBack: () -> Unit, viewModel: StarterPacksViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val existing by viewModel.existingTitles.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Starter packs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = { viewModel.add(onBack) },
                    enabled = state.selected.isNotEmpty() && !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when (val n = state.selected.size) {
                            0 -> "Pick tasks to add"
                            1 -> "Add 1 task"
                            else -> "Add $n tasks"
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(Modifier.fillMaxSize().padding(innerPadding)) {
            item {
                Text(
                    "Pick the ones that fit your home. You can change who does them, how often, " +
                        "and when afterwards.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(StarterPacks.all, key = { it.name }) { pack ->
                PackCard(pack, state, existing, viewModel)
            }
        }
    }
}

@Composable
private fun PackCard(
    pack: StarterPack,
    state: StarterPacksUiState,
    existing: Set<String>,
    viewModel: StarterPacksViewModel,
) {
    var expanded by remember { mutableStateOf(false) }
    val chosen = pack.tasks.count { it in state.selected }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(pack.emoji, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(pack.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (chosen > 0) "$chosen of ${pack.tasks.size} selected" else pack.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        if (expanded) {
            TextButton(onClick = { viewModel.togglePack(pack) }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Select all")
            }
            pack.tasks.forEach { template ->
                val added = template.title.lowercase() in existing
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !added) { viewModel.toggle(template) }
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = added || template in state.selected,
                        onCheckedChange = { viewModel.toggle(template) },
                        enabled = !added,
                    )
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text("${TaskArea.emoji(template.area)} ${template.title}")
                        Text(
                            if (added) "Already in your tasks" else
                                TaskSchedule.repeatLabel(template.repeatMode, template.unit, template.interval) +
                                    " · ⭐ ${template.points}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

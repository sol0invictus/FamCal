package com.famcal.app.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.data.model.HouseholdTask
import com.famcal.app.data.model.Member
import com.famcal.app.data.model.TaskCompletion
import com.famcal.app.data.task.CompletionUndo
import com.famcal.app.data.task.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TaskDetailUiState(
    val task: HouseholdTask? = null,
    val history: List<TaskCompletion> = emptyList(),
    val membersByUid: Map<String, Member> = emptyMap(),
    val currentUid: String = "",
    val isLoading: Boolean = true,
    /** The task no longer exists (deleted here or by someone else). */
    val isGone: Boolean = false,
    val today: LocalDate = LocalDate.now(),
)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    familyRepository: FamilyRepository,
    authRepository: AuthRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    private val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId required" }
    val taskId: String = checkNotNull(savedStateHandle["taskId"]) { "taskId required" }
    private val currentUid = authRepository.currentUser?.uid.orEmpty()

    private val _messages = MutableSharedFlow<TaskMessage>()
    val messages: SharedFlow<TaskMessage> = _messages.asSharedFlow()

    val uiState: StateFlow<TaskDetailUiState> = combine(
        taskRepository.observeTask(familyId, taskId),
        taskRepository.observeHistory(familyId, taskId),
        familyRepository.observeMembers(familyId),
    ) { task, history, members ->
        TaskDetailUiState(
            task = task,
            history = history,
            membersByUid = members.associateBy { it.uid },
            currentUid = currentUid,
            isLoading = false,
            isGone = task == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUiState())

    private fun launchAction(failure: String, block: suspend () -> Result<*>) {
        viewModelScope.launch { block().onFailure { _messages.emit(TaskMessage(failure)) } }
    }

    fun complete() {
        val task = uiState.value.task ?: return
        viewModelScope.launch {
            taskRepository.complete(familyId, task)
                .onSuccess { _messages.emit(TaskMessage(completionMessage(task), it)) }
                .onFailure { _messages.emit(TaskMessage("Couldn't mark that done.")) }
        }
    }

    fun undo(undo: CompletionUndo) = launchAction("Couldn't undo.") { taskRepository.undoComplete(familyId, undo) }

    fun skip() {
        val task = uiState.value.task ?: return
        viewModelScope.launch {
            taskRepository.skip(familyId, task)
                .onSuccess { _messages.emit(TaskMessage("Skipped this time.")) }
                .onFailure { _messages.emit(TaskMessage("Couldn't skip.")) }
        }
    }

    fun snooze(days: Long) {
        viewModelScope.launch {
            taskRepository.snooze(familyId, taskId, days)
                .onSuccess {
                    _messages.emit(TaskMessage(if (days == 1L) "Moved to tomorrow." else "Moved $days days out."))
                }
                .onFailure { _messages.emit(TaskMessage("Couldn't move the task.")) }
        }
    }

    fun claim() = launchAction("Couldn't claim the task.") { taskRepository.claim(familyId, taskId) }
    fun reopen() = launchAction("Couldn't reopen the task.") { taskRepository.reopen(familyId, taskId) }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            taskRepository.deleteTask(familyId, taskId)
                .onSuccess { onDone() }
                .onFailure { _messages.emit(TaskMessage("Couldn't delete the task.")) }
        }
    }
}

package com.famcal.app.ui.tasks

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.OpenRequest
import com.famcal.app.data.PendingNavigation
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.data.model.HouseholdTask
import com.famcal.app.data.model.Member
import com.famcal.app.data.task.CompletionUndo
import com.famcal.app.data.task.TaskRepository
import com.famcal.app.util.TaskSchedule
import com.famcal.app.util.TaskStatus
import com.famcal.app.widget.TasksWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class TaskFilter { FOR_ME, EVERYONE }

data class TaskSection(val status: TaskStatus, val tasks: List<HouseholdTask>) {
    val title: String
        get() = when (status) {
            TaskStatus.OVERDUE -> "Overdue"
            TaskStatus.TODAY -> "Today"
            TaskStatus.THIS_WEEK -> "This week"
            TaskStatus.LATER -> "Later"
            TaskStatus.NO_DATE -> "Anytime"
            TaskStatus.DONE -> "Done"
        }
}

data class TasksUiState(
    val sections: List<TaskSection> = emptyList(),
    val membersByUid: Map<String, Member> = emptyMap(),
    val currentUid: String = "",
    val filter: TaskFilter = TaskFilter.EVERYONE,
    val area: String? = null,
    /** Areas that have at least one task, for the filter menu. */
    val areasInUse: List<String> = emptyList(),
    val showDone: Boolean = false,
    /** Overdue + due-today tasks for me (or anyone), for the tab badge. */
    val myDueCount: Int = 0,
    val hasAnyTasks: Boolean = false,
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
)

/** A snackbar message, optionally with Undo for a completion. */
data class TaskMessage(val text: String, val undo: CompletionUndo? = null)

private data class TaskFilters(val filter: TaskFilter, val area: String?, val showDone: Boolean)

/**
 * The Tasks tab. Scoped to the home destination (shared with [com.famcal.app.ui.home.HomeScreen]
 * for the tab badge), reading familyId from its SavedStateHandle.
 */
@HiltViewModel
class TasksViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    familyRepository: FamilyRepository,
    authRepository: AuthRepository,
    private val taskRepository: TaskRepository,
    private val pendingNavigation: PendingNavigation,
) : ViewModel() {

    val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId required" }
    private val currentUid = authRepository.currentUser?.uid.orEmpty()

    private val filters = MutableStateFlow(TaskFilters(TaskFilter.EVERYONE, null, false))

    private val _messages = MutableSharedFlow<TaskMessage>()
    val messages: SharedFlow<TaskMessage> = _messages.asSharedFlow()

    val openRequest: StateFlow<OpenRequest?> = pendingNavigation.request

    private val tasksFlow = taskRepository.observeTasks(familyId)

    val uiState: StateFlow<TasksUiState> = combine(
        tasksFlow,
        familyRepository.observeMembers(familyId),
        filters,
    ) { tasks, members, f ->
        val today = LocalDate.now()
        val visible = tasks.filter { task ->
            (f.filter == TaskFilter.EVERYONE || task.assignedTo.isBlank() || task.assignedTo == currentUid) &&
                (f.area == null || task.area == f.area)
        }
        val byStatus = visible.groupBy { it.status(today) }
        val sections = TaskStatus.entries
            .filter { it != TaskStatus.DONE || f.showDone }
            .mapNotNull { status ->
                val list = byStatus[status].orEmpty()
                val sorted = if (status == TaskStatus.DONE) {
                    list.sortedByDescending { it.completedAt }
                } else {
                    list.sortedWith(compareBy({ it.dueDate.ifBlank { "9999" } }, { it.title.lowercase() }))
                }
                sorted.takeIf { it.isNotEmpty() }?.let { TaskSection(status, it) }
            }
        TasksUiState(
            sections = sections,
            membersByUid = members.associateBy { it.uid },
            currentUid = currentUid,
            filter = f.filter,
            area = f.area,
            areasInUse = tasks.map { it.area }.distinct().sorted(),
            showDone = f.showDone,
            myDueCount = tasks.count { task ->
                (task.assignedTo.isBlank() || task.assignedTo == currentUid) &&
                    task.status(today).let { it == TaskStatus.OVERDUE || it == TaskStatus.TODAY }
            },
            hasAnyTasks = tasks.isNotEmpty(),
            isLoading = false,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    init {
        // Keep the home-screen widget in step with changes made here or by others.
        viewModelScope.launch {
            tasksFlow.drop(1).collect { TasksWidget.requestUpdate(context) }
        }
    }

    fun setFilter(filter: TaskFilter) = filters.value.let { filters.value = it.copy(filter = filter) }
    fun setArea(area: String?) = filters.value.let { filters.value = it.copy(area = area) }
    fun toggleShowDone() = filters.value.let { filters.value = it.copy(showDone = !it.showDone) }

    fun complete(task: HouseholdTask) {
        viewModelScope.launch {
            taskRepository.complete(familyId, task)
                .onSuccess { undo ->
                    _messages.emit(TaskMessage(completionMessage(task), undo))
                }
                .onFailure { _messages.emit(TaskMessage("Couldn't mark that done.")) }
        }
    }

    fun undo(undo: CompletionUndo) {
        viewModelScope.launch {
            taskRepository.undoComplete(familyId, undo)
                .onFailure { _messages.emit(TaskMessage("Couldn't undo.")) }
        }
    }

    fun claim(task: HouseholdTask) {
        viewModelScope.launch {
            taskRepository.claim(familyId, task.id)
                .onSuccess { _messages.emit(TaskMessage("It's yours: ${task.title}")) }
                .onFailure { _messages.emit(TaskMessage("Couldn't claim the task.")) }
        }
    }

    /** Opens the Tasks tab for a notification/widget tap; returns the task to show, if any. */
    fun handleOpenRequest(request: OpenRequest): String? {
        if (request.target != OpenRequest.Target.TASKS) return null
        if (request.familyId != null && request.familyId != familyId) return null
        pendingNavigation.consume()
        return request.taskId
    }
}

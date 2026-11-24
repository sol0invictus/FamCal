package com.famcal.app.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.data.model.HouseholdTask
import com.famcal.app.data.model.Member
import com.famcal.app.data.model.Recurrence
import com.famcal.app.data.model.TaskArea
import com.famcal.app.data.model.TaskRepeat
import com.famcal.app.data.task.TaskRepository
import com.famcal.app.util.TaskSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TaskFormState(
    val title: String = "",
    val notes: String = "",
    val area: String = TaskArea.OTHER,
    val assignedTo: String = "",
    val rotate: Boolean = false,
    /** Rotation order (uids), only used when [rotate] is on. */
    val rotation: List<String> = emptyList(),
    val dueDate: LocalDate? = LocalDate.now(),
    val repeatMode: String = TaskRepeat.NONE,
    val repeatUnit: String = Recurrence.WEEKLY,
    /** 0 while the field is cleared mid-edit (saved as 1). */
    val repeatInterval: Int = 1,
    val points: Int = 1,
    val cost: String = "",
    val vendorName: String = "",
    val vendorPhone: String = "",
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = title.isNotBlank() && !isSaving && !isLoading &&
            (!rotate || rotation.isNotEmpty()) &&
            (repeatMode == TaskRepeat.NONE || dueDate != null)
}

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    familyRepository: FamilyRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    private val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId required" }
    private val taskId: String? = savedStateHandle.get<String>("taskId")?.takeIf { it.isNotBlank() }

    /** The task as loaded, so saving keeps fields the form doesn't edit (history, author). */
    private var original: HouseholdTask? = null

    private val _state = MutableStateFlow(TaskFormState(isEditing = taskId != null, isLoading = taskId != null))
    val state: StateFlow<TaskFormState> = _state.asStateFlow()

    val members: StateFlow<List<Member>> = familyRepository.observeMembers(familyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (taskId != null) {
            viewModelScope.launch {
                taskRepository.getTask(familyId, taskId)
                    .onSuccess { task ->
                        original = task
                        _state.update {
                            it.copy(
                                title = task.title,
                                notes = task.notes,
                                area = task.area,
                                assignedTo = task.assignedTo,
                                rotate = task.rotation.isNotEmpty(),
                                rotation = task.rotation,
                                dueDate = TaskSchedule.parse(task.dueDate),
                                repeatMode = task.repeatMode,
                                repeatUnit = task.repeatUnit,
                                repeatInterval = task.repeatInterval.coerceAtLeast(1),
                                points = task.points,
                                cost = task.cost,
                                vendorName = task.vendorName,
                                vendorPhone = task.vendorPhone,
                                isLoading = false,
                            )
                        }
                    }
                    .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
            }
        }
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v, error = null) }
    fun onNotesChange(v: String) = _state.update { it.copy(notes = v) }
    fun onAreaChange(v: String) = _state.update { it.copy(area = v) }
    fun onAssigneeChange(uid: String) = _state.update { it.copy(assignedTo = uid) }
    fun onCostChange(v: String) = _state.update { it.copy(cost = v) }
    fun onVendorNameChange(v: String) = _state.update { it.copy(vendorName = v) }
    fun onVendorPhoneChange(v: String) = _state.update { it.copy(vendorPhone = v) }
    fun onDueDateChange(date: LocalDate?) = _state.update { it.copy(dueDate = date) }
    fun onRepeatUnitChange(unit: String) = _state.update { it.copy(repeatUnit = unit) }
    fun onPointsChange(points: Int) = _state.update { it.copy(points = points.coerceIn(1, MAX_POINTS)) }

    fun onRepeatModeChange(mode: String) = _state.update {
        // A repeating task needs a first due date.
        it.copy(repeatMode = mode, dueDate = it.dueDate ?: LocalDate.now().takeIf { mode != TaskRepeat.NONE })
    }

    fun onIntervalChange(text: String) = _state.update {
        it.copy(repeatInterval = text.filter(Char::isDigit).take(3).toIntOrNull() ?: 0)
    }

    fun onRotateChange(on: Boolean) = _state.update {
        if (on) {
            val start = listOfNotNull(it.assignedTo.takeIf { uid -> uid.isNotBlank() })
            it.copy(rotate = true, rotation = start)
        } else {
            it.copy(rotate = false, rotation = emptyList())
        }
    }

    /** Adds [uid] to the end of the rotation, or removes it. */
    fun toggleInRotation(uid: String) = _state.update {
        val rotation = if (uid in it.rotation) it.rotation - uid else it.rotation + uid
        it.copy(rotation = rotation)
    }

    fun save(onDone: () -> Unit) {
        val form = _state.value
        if (!form.canSave) return
        _state.update { it.copy(isSaving = true, error = null) }
        val rotation = if (form.rotate) form.rotation else emptyList()
        val assignee = when {
            rotation.isEmpty() -> form.assignedTo
            form.assignedTo in rotation -> form.assignedTo
            else -> rotation.first()
        }
        val base = original ?: HouseholdTask()
        val task = base.copy(
            title = form.title.trim(),
            notes = form.notes.trim(),
            area = form.area,
            assignedTo = assignee,
            rotation = rotation,
            dueDate = form.dueDate?.toString().orEmpty(),
            repeatMode = form.repeatMode,
            repeatUnit = form.repeatUnit,
            repeatInterval = form.repeatInterval.coerceAtLeast(1),
            points = form.points,
            cost = form.cost.trim(),
            vendorName = form.vendorName.trim(),
            vendorPhone = form.vendorPhone.trim(),
            // Turning a finished one-off into a repeating task re-opens it.
            completedAt = base.completedAt.takeIf { form.repeatMode == TaskRepeat.NONE },
        )
        viewModelScope.launch {
            val result = if (taskId == null) {
                taskRepository.createTask(familyId, task)
            } else {
                taskRepository.updateTask(familyId, task)
            }
            result
                .onSuccess { onDone() }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    private companion object {
        const val MAX_POINTS = 20
    }
}

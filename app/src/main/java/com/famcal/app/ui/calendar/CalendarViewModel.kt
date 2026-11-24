package com.famcal.app.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.OpenRequest
import com.famcal.app.data.PendingNavigation
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.calendar.CalendarMirror
import com.famcal.app.data.event.EventRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.data.model.CalendarEvent
import com.famcal.app.data.model.HouseholdTask
import com.famcal.app.data.model.Member
import com.famcal.app.data.task.CompletionUndo
import com.famcal.app.data.task.TaskRepository
import com.famcal.app.notifications.ReminderScheduler
import com.famcal.app.ui.tasks.TaskMessage
import com.famcal.app.ui.tasks.completionMessage
import com.famcal.app.ui.tasks.isDone
import com.famcal.app.util.TaskSchedule
import com.famcal.app.util.EventOccurrence
import com.famcal.app.util.RecurrenceExpander
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class CalendarUiState(
    val familyName: String = "",
    val membersByUid: Map<String, Member> = emptyMap(),
    val eventsByDay: Map<LocalDate, List<EventOccurrence>> = emptyMap(),
    /** Open tasks by due date; overdue ones are shown on today. */
    val tasksByDay: Map<LocalDate, List<HouseholdTask>> = emptyMap(),
    val currentUid: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
) {
    val selectedDayEvents: List<EventOccurrence>
        get() = eventsByDay[selectedDate].orEmpty()

    val selectedDayTasks: List<HouseholdTask>
        get() = tasksByDay[selectedDate].orEmpty()
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    familyRepository: FamilyRepository,
    private val authRepository: AuthRepository,
    private val eventRepository: EventRepository,
    private val reminderScheduler: ReminderScheduler,
    private val calendarMirror: CalendarMirror,
    private val pendingNavigation: PendingNavigation,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId is required" }

    private val selectedDate = MutableStateFlow(LocalDate.now())

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _taskMessages = MutableSharedFlow<TaskMessage>()
    val taskMessages: SharedFlow<TaskMessage> = _taskMessages.asSharedFlow()

    /**
     * A tapped notification waiting to be shown. The screen handles it (only while visible),
     * so it isn't consumed while the app is still in the background.
     */
    val openRequest: StateFlow<OpenRequest?> = pendingNavigation.request

    val uiState: StateFlow<CalendarUiState> = combine(
        familyRepository.observeFamily(familyId),
        familyRepository.observeMembers(familyId),
        eventRepository.observeEvents(familyId),
        selectedDate,
        // Starts empty so events never wait on the tasks listener (e.g. rules not yet published).
        taskRepository.observeTasks(familyId).onStart { emit(emptyList()) },
    ) { family, members, events, date, tasks ->
        val today = LocalDate.now()
        val occurrences = RecurrenceExpander.expand(
            events = events,
            windowStart = today.minusMonths(2),
            windowEnd = today.plusMonths(12),
        )
        // Place each occurrence on every day it spans (multi-day events show on all their days).
        val eventsByDay = mutableMapOf<LocalDate, MutableList<EventOccurrence>>()
        for (occ in occurrences) {
            var day = occ.start.toLocalDate()
            val lastDay = occ.end.toLocalDate()
            var guard = 0
            while (!day.isAfter(lastDay) && guard < MAX_SPAN_DAYS) {
                eventsByDay.getOrPut(day) { mutableListOf() }.add(occ)
                day = day.plusDays(1)
                guard++
            }
        }
        val tasksByDay = tasks
            .filter { !it.isDone() }
            .mapNotNull { task -> TaskSchedule.parse(task.dueDate)?.let { due -> maxOf(due, today) to task } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.sortedBy { it.dueDate } }
        CalendarUiState(
            familyName = family?.name.orEmpty(),
            membersByUid = members.associateBy { it.uid },
            eventsByDay = eventsByDay,
            tasksByDay = tasksByDay,
            currentUid = authRepository.currentUser?.uid.orEmpty(),
            selectedDate = date,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(),
    )

    init {
        // Keep upcoming reminders scheduled as events change.
        viewModelScope.launch {
            uiState.collect { state ->
                if (!state.isLoading) {
                    reminderScheduler.sync(familyId, state.eventsByDay.values.flatten())
                }
            }
        }
        // Mirror raw events into the device calendar when that sync is enabled.
        viewModelScope.launch {
            eventRepository.observeEvents(familyId).collect { events ->
                calendarMirror.sync(familyId, events)
            }
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    /** Selects the notification's day if it's for this family; returns the day if handled. */
    fun handleOpenRequest(request: OpenRequest): LocalDate? {
        if (request.target != OpenRequest.Target.CALENDAR) return null
        if (request.familyId != null && request.familyId != familyId) return null
        val date = request.date ?: return null
        pendingNavigation.consume()
        selectDate(date)
        return date
    }

    fun completeTask(task: HouseholdTask) {
        viewModelScope.launch {
            taskRepository.complete(familyId, task)
                .onSuccess { _taskMessages.emit(TaskMessage(completionMessage(task), it)) }
                .onFailure { _taskMessages.emit(TaskMessage("Couldn't mark that done.")) }
        }
    }

    fun undoTask(undo: CompletionUndo) {
        viewModelScope.launch { taskRepository.undoComplete(familyId, undo) }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            eventRepository.deleteEvent(familyId, eventId)
                .onFailure { _messages.emit("Couldn't delete the event.") }
        }
    }

    /**
     * Re-creates a deleted event (for Undo). A fresh id is assigned. Firestore rules require
     * createdBy == the current user on create, so the restorer becomes the author; the original
     * color owner is kept via assignedTo so the event looks the same.
     */
    fun restoreEvent(event: CalendarEvent) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            val restored = event.copy(id = "", createdBy = uid, assignedTo = event.colorUid)
            eventRepository.createEvent(familyId, restored)
                .onFailure { _messages.emit("Couldn't restore the event.") }
        }
    }

    /** Deletes a single occurrence of a recurring event (skips that date). */
    fun deleteOccurrence(eventId: String, date: LocalDate) {
        viewModelScope.launch {
            eventRepository.excludeDate(familyId, eventId, date.toString())
                .onFailure { _messages.emit("Couldn't remove that occurrence.") }
        }
    }

    /** Restores a skipped occurrence (undo). */
    fun restoreOccurrence(eventId: String, date: LocalDate) {
        viewModelScope.launch { eventRepository.includeDate(familyId, eventId, date.toString()) }
    }

    private companion object {
        const val MAX_SPAN_DAYS = 62
    }
}

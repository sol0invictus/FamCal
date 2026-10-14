package com.famcal.app.ui.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.event.EventRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.data.model.CalendarEvent
import com.famcal.app.data.model.Member
import com.famcal.app.data.model.Recurrence
import com.famcal.app.data.model.Reminders
import com.famcal.app.util.toLocalDateTime
import com.famcal.app.util.toTimestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class EventFormState(
    val title: String = "",
    val location: String = "",
    val notes: String = "",
    val allDay: Boolean = false,
    val assignedTo: String = "",
    val reminderMinutes: Int = Reminders.NONE,
    val recurrence: String = Recurrence.NONE,
    val start: LocalDateTime = defaultStart(),
    val end: LocalDateTime = defaultStart().plusHours(1),
    val isEditing: Boolean = false,
    /** Editing one occurrence of a recurring event; saving splits it off the series. */
    val isSingleOccurrence: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = title.isNotBlank() && !end.isBefore(start) && !isSaving && !isLoading

    companion object {
        fun defaultStart(): LocalDateTime =
            LocalDateTime.of(LocalDate.now(), LocalTime.of(9, 0))
    }
}

@HiltViewModel
class EventEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val eventRepository: EventRepository,
    familyRepository: FamilyRepository,
) : ViewModel() {

    private val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId required" }

    /** Family members available to assign an event to. */
    val members: StateFlow<List<Member>> = familyRepository.observeMembers(familyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val eventId: String? = savedStateHandle.get<String>("eventId")?.takeIf { it.isNotBlank() }
    private val dateMillis: Long = savedStateHandle.get<Long>("dateMillis") ?: -1L
    private val occurrenceDate: LocalDate? = savedStateHandle.get<String>("occurrenceDate")
        ?.takeIf { it.isNotBlank() && eventId != null }
        ?.let(LocalDate::parse)

    private val currentUid: String = authRepository.currentUser?.uid.orEmpty()

    // Preserved across an edit so we keep the original author (and thus the event color).
    private var createdBy: String = currentUid

    // Preserved across a series edit so individually-deleted/edited days stay excluded.
    private var excludedDates: List<String> = emptyList()

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<EventFormState> = _state.asStateFlow()

    init {
        if (eventId != null) loadExisting(eventId)
    }

    private fun initialState(): EventFormState {
        val date = if (dateMillis >= 0) {
            java.time.Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        } else {
            LocalDate.now()
        }
        val start = LocalDateTime.of(date, LocalTime.of(9, 0))
        return EventFormState(
            start = start,
            end = start.plusHours(1),
            isEditing = eventId != null,
            isSingleOccurrence = occurrenceDate != null,
            isLoading = eventId != null,
        )
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            eventRepository.getEvent(familyId, id)
                .onSuccess { event ->
                    createdBy = event.createdBy
                    excludedDates = event.excludedDates
                    var start = event.startAt.toLocalDateTime()
                    var end = event.endAt.toLocalDateTime()
                    if (occurrenceDate != null) {
                        // Show this occurrence's own date, keeping the series' time and length.
                        val duration = java.time.Duration.between(start, end)
                        start = occurrenceDate.atTime(start.toLocalTime())
                        end = start.plus(duration)
                    }
                    _state.update {
                        it.copy(
                            title = event.title,
                            location = event.location,
                            notes = event.notes,
                            allDay = event.allDay,
                            // A split-off occurrence keeps the series' color via assignedTo,
                            // since its author becomes the current user.
                            assignedTo = if (occurrenceDate != null) event.colorUid else event.assignedTo,
                            reminderMinutes = event.reminderMinutes,
                            recurrence = if (occurrenceDate != null) Recurrence.NONE else event.recurrence,
                            start = start,
                            end = end,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value, error = null) }
    fun onLocationChange(value: String) = _state.update { it.copy(location = value) }
    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }

    fun onAllDayChange(value: Boolean) = _state.update { it.copy(allDay = value) }
    fun onAssignedToChange(uid: String) = _state.update { it.copy(assignedTo = uid) }
    fun onReminderChange(minutes: Int) = _state.update { it.copy(reminderMinutes = minutes) }
    fun onRecurrenceChange(recurrence: String) = _state.update { it.copy(recurrence = recurrence) }

    fun onStartDateChange(date: LocalDate) = _state.update {
        val newStart = it.start.with(date)
        // Keep the same duration when the start moves.
        val duration = java.time.Duration.between(it.start, it.end)
        it.copy(start = newStart, end = newStart.plus(duration))
    }

    fun onStartTimeChange(time: LocalTime) = _state.update {
        val newStart = it.start.with(time)
        val duration = java.time.Duration.between(it.start, it.end)
        it.copy(start = newStart, end = newStart.plus(duration))
    }

    fun onEndDateChange(date: LocalDate) = _state.update { it.copy(end = it.end.with(date)) }
    fun onEndTimeChange(time: LocalTime) = _state.update { it.copy(end = it.end.with(time)) }

    fun save(onDone: () -> Unit) {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val event = CalendarEvent(
                id = if (occurrenceDate != null) "" else eventId.orEmpty(),
                title = current.title.trim(),
                notes = current.notes.trim(),
                location = current.location.trim(),
                startAt = current.start.toTimestamp(),
                endAt = current.end.toTimestamp(),
                allDay = current.allDay,
                // Firestore rules require new events to be authored by the current user.
                createdBy = if (occurrenceDate != null) currentUid else createdBy,
                assignedTo = current.assignedTo,
                reminderMinutes = current.reminderMinutes,
                recurrence = current.recurrence,
                excludedDates = if (occurrenceDate != null) emptyList() else excludedDates,
            )
            val result = when {
                eventId == null -> eventRepository.createEvent(familyId, event)
                occurrenceDate != null -> eventRepository.detachOccurrence(
                    familyId, eventId, occurrenceDate.toString(), event,
                )
                else -> eventRepository.updateEvent(familyId, event)
            }
            result
                .onSuccess { onDone() }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = eventId ?: return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = if (occurrenceDate != null) {
                eventRepository.excludeDate(familyId, id, occurrenceDate.toString())
            } else {
                eventRepository.deleteEvent(familyId, id)
            }
            result
                .onSuccess { onDone() }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}

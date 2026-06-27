package com.famcal.app.ui.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.event.EventRepository
import com.famcal.app.data.model.CalendarEvent
import com.famcal.app.data.model.Recurrence
import com.famcal.app.data.model.Reminders
import com.famcal.app.util.toLocalDateTime
import com.famcal.app.util.toTimestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val reminderMinutes: Int = Reminders.NONE,
    val recurrence: String = Recurrence.NONE,
    val start: LocalDateTime = defaultStart(),
    val end: LocalDateTime = defaultStart().plusHours(1),
    val isEditing: Boolean = false,
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
) : ViewModel() {

    private val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId required" }
    private val eventId: String? = savedStateHandle.get<String>("eventId")?.takeIf { it.isNotBlank() }
    private val dateMillis: Long = savedStateHandle.get<Long>("dateMillis") ?: -1L

    // Preserved across an edit so we keep the original author (and thus the event color).
    private var createdBy: String = authRepository.currentUser?.uid.orEmpty()

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
            isLoading = eventId != null,
        )
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            eventRepository.getEvent(familyId, id)
                .onSuccess { event ->
                    createdBy = event.createdBy
                    _state.update {
                        it.copy(
                            title = event.title,
                            location = event.location,
                            notes = event.notes,
                            allDay = event.allDay,
                            reminderMinutes = event.reminderMinutes,
                            recurrence = event.recurrence,
                            start = event.startAt.toLocalDateTime(),
                            end = event.endAt.toLocalDateTime(),
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
                id = eventId.orEmpty(),
                title = current.title.trim(),
                notes = current.notes.trim(),
                location = current.location.trim(),
                startAt = current.start.toTimestamp(),
                endAt = current.end.toTimestamp(),
                allDay = current.allDay,
                createdBy = createdBy,
                reminderMinutes = current.reminderMinutes,
                recurrence = current.recurrence,
            )
            val result = if (eventId == null) {
                eventRepository.createEvent(familyId, event)
            } else {
                eventRepository.updateEvent(familyId, event)
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
            eventRepository.deleteEvent(familyId, id)
                .onSuccess { onDone() }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}

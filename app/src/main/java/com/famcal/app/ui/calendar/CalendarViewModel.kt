package com.famcal.app.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.calendar.CalendarMirror
import com.famcal.app.data.event.EventRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.data.model.CalendarEvent
import com.famcal.app.data.model.Member
import com.famcal.app.notifications.ReminderScheduler
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class CalendarUiState(
    val familyName: String = "",
    val membersByUid: Map<String, Member> = emptyMap(),
    val eventsByDay: Map<LocalDate, List<EventOccurrence>> = emptyMap(),
    val selectedDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
) {
    val selectedDayEvents: List<EventOccurrence>
        get() = eventsByDay[selectedDate].orEmpty()
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    familyRepository: FamilyRepository,
    private val eventRepository: EventRepository,
    private val reminderScheduler: ReminderScheduler,
    private val calendarMirror: CalendarMirror,
) : ViewModel() {

    val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId is required" }

    private val selectedDate = MutableStateFlow(LocalDate.now())

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<CalendarUiState> = combine(
        familyRepository.observeFamily(familyId),
        familyRepository.observeMembers(familyId),
        eventRepository.observeEvents(familyId),
        selectedDate,
    ) { family, members, events, date ->
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
        CalendarUiState(
            familyName = family?.name.orEmpty(),
            membersByUid = members.associateBy { it.uid },
            eventsByDay = eventsByDay,
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
                reminderScheduler.sync(state.eventsByDay.values.flatten())
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

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            eventRepository.deleteEvent(familyId, eventId)
                .onFailure { _messages.emit("Couldn't delete the event.") }
        }
    }

    /** Re-creates a deleted event (for Undo). A fresh id is assigned. */
    fun restoreEvent(event: CalendarEvent) {
        viewModelScope.launch {
            eventRepository.createEvent(familyId, event.copy(id = ""))
                .onFailure { _messages.emit("Couldn't restore the event.") }
        }
    }

    private companion object {
        const val MAX_SPAN_DAYS = 62
    }
}

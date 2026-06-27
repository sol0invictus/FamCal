package com.famcal.app.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.event.EventRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.data.model.Member
import com.famcal.app.notifications.ReminderScheduler
import com.famcal.app.util.EventOccurrence
import com.famcal.app.util.RecurrenceExpander
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
) {
    val selectedDayEvents: List<EventOccurrence>
        get() = eventsByDay[selectedDate].orEmpty()
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    familyRepository: FamilyRepository,
    eventRepository: EventRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId is required" }

    private val selectedDate = MutableStateFlow(LocalDate.now())

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
        CalendarUiState(
            familyName = family?.name.orEmpty(),
            membersByUid = members.associateBy { it.uid },
            eventsByDay = occurrences.groupBy { it.date },
            selectedDate = date,
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
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }
}

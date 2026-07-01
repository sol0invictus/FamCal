package com.famcal.app.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.event.EventRepository
import com.famcal.app.data.model.CalendarEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    eventRepository: EventRepository,
) : ViewModel() {

    private val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId required" }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val allEvents = eventRepository.observeEvents(familyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val results: StateFlow<List<CalendarEvent>> = combine(allEvents, _query) { events, q ->
        val term = q.trim()
        if (term.isBlank()) {
            emptyList()
        } else {
            events.filter {
                it.title.contains(term, ignoreCase = true) ||
                    it.notes.contains(term, ignoreCase = true) ||
                    it.location.contains(term, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }
}

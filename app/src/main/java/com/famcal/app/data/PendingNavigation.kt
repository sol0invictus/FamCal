package com.famcal.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** A request (from a tapped notification) to show a family's calendar on a given day. */
data class OpenRequest(val familyId: String?, val date: LocalDate)

/**
 * Hands a tapped notification's target from [com.famcal.app.MainActivity] to the calendar.
 * Held until the calendar for that family consumes it, so it survives a family switch.
 */
@Singleton
class PendingNavigation @Inject constructor() {
    private val _request = MutableStateFlow<OpenRequest?>(null)
    val request: StateFlow<OpenRequest?> = _request.asStateFlow()

    fun open(request: OpenRequest) {
        _request.value = request
    }

    fun consume() {
        _request.value = null
    }
}

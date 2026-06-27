package com.famcal.app.util

import com.famcal.app.data.model.CalendarEvent
import com.famcal.app.data.model.Recurrence
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** A single dated instance of an event (a non-recurring event has exactly one). */
data class EventOccurrence(
    val event: CalendarEvent,
    val start: LocalDateTime,
    val end: LocalDateTime,
) {
    val date: LocalDate get() = start.toLocalDate()
}

/**
 * Expands events (including recurring ones) into concrete [EventOccurrence]s that fall
 * within a date window. Recurrence is series-wide with no per-instance exceptions —
 * good enough for an MVP family calendar.
 */
object RecurrenceExpander {

    private const val MAX_ITERATIONS = 5000

    fun expand(
        events: List<CalendarEvent>,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): List<EventOccurrence> {
        val result = mutableListOf<EventOccurrence>()
        for (event in events) {
            val baseStart = event.startAt.toLocalDateTime()
            val duration = Duration.between(baseStart, event.endAt.toLocalDateTime())
                .let { if (it.isNegative) Duration.ZERO else it }

            if (event.recurrence == Recurrence.NONE) {
                if (!baseStart.toLocalDate().isBefore(windowStart) &&
                    !baseStart.toLocalDate().isAfter(windowEnd)
                ) {
                    result += EventOccurrence(event, baseStart, baseStart.plus(duration))
                }
                continue
            }

            var occStart = baseStart
            var iterations = 0
            // Fast-forward to the window start.
            while (occStart.toLocalDate().isBefore(windowStart) && iterations < MAX_ITERATIONS) {
                occStart = advance(occStart, event.recurrence)
                iterations++
            }
            // Emit occurrences across the window.
            while (!occStart.toLocalDate().isAfter(windowEnd) && iterations < MAX_ITERATIONS) {
                result += EventOccurrence(event, occStart, occStart.plus(duration))
                occStart = advance(occStart, event.recurrence)
                iterations++
            }
        }
        return result.sortedBy { it.start }
    }

    private fun advance(from: LocalDateTime, recurrence: String): LocalDateTime = when (recurrence) {
        Recurrence.DAILY -> from.plusDays(1)
        Recurrence.WEEKLY -> from.plusWeeks(1)
        Recurrence.MONTHLY -> from.plusMonths(1)
        else -> from.plusYears(1000) // unreachable; defensively avoid an infinite loop
    }
}

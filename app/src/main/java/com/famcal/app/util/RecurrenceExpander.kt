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
 * within a date window. Individually deleted or edited days are skipped via
 * [CalendarEvent.excludedDates] (an edited day lives on as its own standalone event).
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

            // Stop at the series' end date if it comes before the window's end.
            val lastDate = event.repeatUntil?.toLocalDate()
                ?.takeIf { it.isBefore(windowEnd) }
                ?: windowEnd

            // Occurrence n is computed from the series start (not the previous occurrence) so
            // e.g. a monthly event on the 31st doesn't drift to the 28th after February.
            var n = 0L
            var occStart = baseStart
            // Fast-forward to the window start.
            while (occStart.toLocalDate().isBefore(windowStart) && n < MAX_ITERATIONS) {
                occStart = nth(baseStart, event.recurrence, ++n)
            }
            // Emit occurrences across the window, skipping individually-deleted dates.
            while (!occStart.toLocalDate().isAfter(lastDate) && n < MAX_ITERATIONS) {
                if (occStart.toLocalDate().toString() !in event.excludedDates) {
                    result += EventOccurrence(event, occStart, occStart.plus(duration))
                }
                occStart = nth(baseStart, event.recurrence, ++n)
            }
        }
        return result.sortedBy { it.start }
    }

    private fun nth(start: LocalDateTime, recurrence: String, n: Long): LocalDateTime = when (recurrence) {
        Recurrence.DAILY -> start.plusDays(n)
        Recurrence.WEEKLY -> start.plusWeeks(n)
        Recurrence.MONTHLY -> start.plusMonths(n)
        Recurrence.YEARLY -> start.plusYears(n)
        else -> start.plusYears(1000 * n) // unreachable; defensively avoid an infinite loop
    }
}

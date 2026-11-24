package com.famcal.app.util

import com.famcal.app.data.model.Recurrence
import com.famcal.app.data.model.TaskRepeat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Where a task falls relative to today, in display order. */
enum class TaskStatus { OVERDUE, TODAY, THIS_WEEK, LATER, NO_DATE, DONE }

/**
 * Pure date logic for household tasks (no Firebase types, so it's unit-tested in
 * `TaskScheduleTest`). Dates are [LocalDate]s; "today" is always passed in.
 */
object TaskSchedule {

    private val shortDate = DateTimeFormatter.ofPattern("EEE, MMM d")

    fun parse(iso: String): LocalDate? =
        iso.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** [date] moved forward by [units] repeat units. */
    fun step(date: LocalDate, unit: String, units: Long): LocalDate = when (unit) {
        Recurrence.DAILY -> date.plusDays(units)
        Recurrence.WEEKLY -> date.plusWeeks(units)
        Recurrence.MONTHLY -> date.plusMonths(units)
        Recurrence.YEARLY -> date.plusYears(units)
        else -> date.plusWeeks(units)
    }

    /**
     * The next due date once the current one is done on [today]; null when the task doesn't
     * repeat (it's then finished).
     *
     * - [TaskRepeat.AFTER_DONE]: [interval] units after [today].
     * - [TaskRepeat.SCHEDULE]: the first scheduled date after today, counted from the current
     *   due date (so missed weeks are skipped, doing it early moves on one step, and a monthly
     *   task on the 31st doesn't drift after February).
     */
    fun nextDue(
        dueDate: LocalDate?,
        mode: String,
        unit: String,
        interval: Int,
        today: LocalDate,
    ): LocalDate? {
        val every = interval.coerceAtLeast(1).toLong()
        return when (mode) {
            TaskRepeat.AFTER_DONE -> step(today, unit, every)
            TaskRepeat.SCHEDULE -> {
                val anchor = dueDate ?: return step(today, unit, every)
                var k = 1L
                var next = step(anchor, unit, every * k)
                while (!next.isAfter(today) && k < MAX_STEPS) {
                    k++
                    next = step(anchor, unit, every * k)
                }
                next
            }
            else -> null
        }
    }

    /** Next person in a rotation after [current]; [current] unchanged when there's no rotation. */
    fun nextAssignee(current: String, rotation: List<String>): String {
        if (rotation.isEmpty()) return current
        val index = rotation.indexOf(current)
        return if (index == -1) rotation.first() else rotation[(index + 1) % rotation.size]
    }

    fun status(dueDate: LocalDate?, done: Boolean, today: LocalDate): TaskStatus = when {
        done -> TaskStatus.DONE
        dueDate == null -> TaskStatus.NO_DATE
        dueDate.isBefore(today) -> TaskStatus.OVERDUE
        dueDate == today -> TaskStatus.TODAY
        dueDate.isBefore(today.plusDays(7)) -> TaskStatus.THIS_WEEK
        else -> TaskStatus.LATER
    }

    /** "Due today", "Due tomorrow", "Due in 3 days", "Due Mon, Dec 1", "2 days overdue". */
    fun dueLabel(dueDate: LocalDate?, today: LocalDate): String {
        if (dueDate == null) return "No due date"
        val days = ChronoUnit.DAYS.between(today, dueDate)
        return when {
            days < -1L -> "${-days} days overdue"
            days == -1L -> "Due yesterday"
            days == 0L -> "Due today"
            days == 1L -> "Due tomorrow"
            days < 7L -> "Due in $days days"
            else -> "Due ${dueDate.format(shortDate)}"
        }
    }

    /** "today", "yesterday", "12 days ago", "3 months ago". */
    fun sinceLabel(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days <= 0L -> "today"
            days == 1L -> "yesterday"
            days < 60L -> "$days days ago"
            days < 730L -> "${days / 30} months ago"
            else -> "${days / 365} years ago"
        }
    }

    /** "Every week", "Every 3 months", "Every 90 days after it's done", "Doesn't repeat". */
    fun repeatLabel(mode: String, unit: String, interval: Int): String {
        if (mode == TaskRepeat.NONE) return "Doesn't repeat"
        val n = interval.coerceAtLeast(1)
        val every = if (n == 1) "Every ${Recurrence.unit(unit, 1)}" else "Every $n ${Recurrence.unit(unit, n)}"
        return if (mode == TaskRepeat.AFTER_DONE) "$every after it's done" else every
    }

    private const val MAX_STEPS = 10_000L
}

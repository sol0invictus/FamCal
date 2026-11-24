package com.famcal.app.util

import com.famcal.app.data.model.Recurrence
import com.famcal.app.data.model.TaskRepeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TaskScheduleTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 23) // a Monday

    @Test
    fun nonRepeatingTaskHasNoNextDate() {
        assertNull(TaskSchedule.nextDue(today, TaskRepeat.NONE, Recurrence.WEEKLY, 1, today))
    }

    @Test
    fun scheduleDoneOnTimeMovesOneStep() {
        assertEquals(
            LocalDate.of(2026, 11, 30),
            TaskSchedule.nextDue(today, TaskRepeat.SCHEDULE, Recurrence.WEEKLY, 1, today),
        )
    }

    @Test
    fun scheduleSkipsMissedPeriods() {
        // Due three weeks ago, done today: next is the next Monday after today, not a backlog.
        val due = today.minusWeeks(3)
        assertEquals(
            LocalDate.of(2026, 11, 30),
            TaskSchedule.nextDue(due, TaskRepeat.SCHEDULE, Recurrence.WEEKLY, 1, today),
        )
    }

    @Test
    fun scheduleDoneEarlyStillMovesOneStep() {
        val due = today.plusDays(3)
        assertEquals(
            due.plusWeeks(2),
            TaskSchedule.nextDue(due, TaskRepeat.SCHEDULE, Recurrence.WEEKLY, 2, today),
        )
    }

    @Test
    fun monthlyScheduleKeepsMonthEndAnchor() {
        // Anchored on Jan 31: Feb clamps to the 28th but March goes back to the 31st.
        val due = LocalDate.of(2026, 1, 31)
        val doneOn = LocalDate.of(2026, 2, 28)
        assertEquals(
            LocalDate.of(2026, 3, 31),
            TaskSchedule.nextDue(due, TaskRepeat.SCHEDULE, Recurrence.MONTHLY, 1, doneOn),
        )
    }

    @Test
    fun afterDoneCountsFromCompletion() {
        val due = today.minusDays(10)
        assertEquals(
            today.plusDays(90),
            TaskSchedule.nextDue(due, TaskRepeat.AFTER_DONE, Recurrence.DAILY, 90, today),
        )
        assertEquals(
            today.plusMonths(3),
            TaskSchedule.nextDue(null, TaskRepeat.AFTER_DONE, Recurrence.MONTHLY, 3, today),
        )
    }

    @Test
    fun scheduleWithoutDueDateStartsFromToday() {
        assertEquals(
            today.plusDays(1),
            TaskSchedule.nextDue(null, TaskRepeat.SCHEDULE, Recurrence.DAILY, 1, today),
        )
    }

    @Test
    fun rotationAdvancesAndWraps() {
        val rotation = listOf("a", "b", "c")
        assertEquals("b", TaskSchedule.nextAssignee("a", rotation))
        assertEquals("a", TaskSchedule.nextAssignee("c", rotation))
        assertEquals("a", TaskSchedule.nextAssignee("", rotation))
        assertEquals("x", TaskSchedule.nextAssignee("x", emptyList()))
    }

    @Test
    fun statusBuckets() {
        assertEquals(TaskStatus.DONE, TaskSchedule.status(today, true, today))
        assertEquals(TaskStatus.NO_DATE, TaskSchedule.status(null, false, today))
        assertEquals(TaskStatus.OVERDUE, TaskSchedule.status(today.minusDays(1), false, today))
        assertEquals(TaskStatus.TODAY, TaskSchedule.status(today, false, today))
        assertEquals(TaskStatus.THIS_WEEK, TaskSchedule.status(today.plusDays(6), false, today))
        assertEquals(TaskStatus.LATER, TaskSchedule.status(today.plusDays(7), false, today))
    }

    @Test
    fun dueLabels() {
        assertEquals("No due date", TaskSchedule.dueLabel(null, today))
        assertEquals("3 days overdue", TaskSchedule.dueLabel(today.minusDays(3), today))
        assertEquals("Due yesterday", TaskSchedule.dueLabel(today.minusDays(1), today))
        assertEquals("Due today", TaskSchedule.dueLabel(today, today))
        assertEquals("Due tomorrow", TaskSchedule.dueLabel(today.plusDays(1), today))
        assertEquals("Due in 4 days", TaskSchedule.dueLabel(today.plusDays(4), today))
    }

    @Test
    fun sinceLabels() {
        assertEquals("today", TaskSchedule.sinceLabel(today, today))
        assertEquals("yesterday", TaskSchedule.sinceLabel(today.minusDays(1), today))
        assertEquals("12 days ago", TaskSchedule.sinceLabel(today.minusDays(12), today))
        assertEquals("3 months ago", TaskSchedule.sinceLabel(today.minusDays(95), today))
    }

    @Test
    fun parseRejectsBlankAndGarbage() {
        assertNull(TaskSchedule.parse(""))
        assertNull(TaskSchedule.parse("not a date"))
        assertEquals(today, TaskSchedule.parse("2026-11-23"))
    }
}

package com.famcal.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A household task / chore. Stored at `families/{familyId}/tasks/{taskId}`.
 *
 * Tasks are day-granular: [dueDate] is an ISO date ("yyyy-MM-dd", blank = no date), so a
 * chore is due on the same day for everyone regardless of time zone. Repeating tasks keep a
 * single live document whose [dueDate] moves forward each time it's done or skipped (see
 * [com.famcal.app.util.TaskSchedule]); each completion is also logged as a [TaskCompletion].
 */
data class HouseholdTask(
    @DocumentId val id: String = "",
    val title: String = "",
    val notes: String = "",
    /** One of [TaskArea] keys. */
    val area: String = TaskArea.OTHER,
    /** uid responsible for the next occurrence; blank = anyone can claim it. */
    val assignedTo: String = "",
    /** Ordered uids to take turns; when non-empty the assignee advances on each completion. */
    val rotation: List<String> = emptyList(),
    val dueDate: String = "",
    /** [TaskRepeat.NONE], [TaskRepeat.SCHEDULE] (fixed dates) or [TaskRepeat.AFTER_DONE]. */
    val repeatMode: String = TaskRepeat.NONE,
    /** A [Recurrence] unit: DAILY / WEEKLY / MONTHLY / YEARLY. */
    val repeatUnit: String = Recurrence.WEEKLY,
    val repeatInterval: Int = 1,
    /** Points credited to whoever completes it (family leaderboard / kids' chores). */
    val points: Int = 1,
    /** One-off tasks only: when it was finished (null = still open). */
    val completedAt: Timestamp? = null,
    val lastDoneAt: Timestamp? = null,
    val lastDoneBy: String = "",
    /** Free-form details for upkeep jobs (e.g. "$45", "Joe's Plumbing", "555-0100"). */
    val cost: String = "",
    val vendorName: String = "",
    val vendorPhone: String = "",
    val createdBy: String = "",
    /** uid of whoever last wrote the task; push notifications skip this person. */
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Date? = null,
)

/**
 * One time a task was done, at `families/{familyId}/taskCompletions/{id}`. Kept flat (not
 * under the task) so the points leaderboard is a single query, and it survives the task
 * being deleted.
 */
data class TaskCompletion(
    @DocumentId val id: String = "",
    val taskId: String = "",
    val taskTitle: String = "",
    val doneBy: String = "",
    val doneAt: Timestamp = Timestamp.now(),
    /** The due date that was completed (blank for undated tasks). */
    val dueDate: String = "",
    val points: Int = 1,
)

/** How a task repeats. */
object TaskRepeat {
    const val NONE = "NONE"
    /** Fixed calendar schedule (trash every Tuesday): the next date ignores when it was done. */
    const val SCHEDULE = "SCHEDULE"
    /** Interval counted from completion (filter every 90 days after the last change). */
    const val AFTER_DONE = "AFTER_DONE"

    val all = listOf(NONE, SCHEDULE, AFTER_DONE)

    fun label(mode: String): String = when (mode) {
        SCHEDULE -> "On a schedule"
        AFTER_DONE -> "After it's done"
        else -> "Doesn't repeat"
    }
}

/** Rooms / categories used to group and filter tasks. */
object TaskArea {
    const val KITCHEN = "KITCHEN"
    const val BATHROOM = "BATHROOM"
    const val BEDROOM = "BEDROOM"
    const val LIVING = "LIVING"
    const val LAUNDRY = "LAUNDRY"
    const val YARD = "YARD"
    const val CAR = "CAR"
    const val UTILITIES = "UTILITIES"
    const val PETS = "PETS"
    const val ERRANDS = "ERRANDS"
    const val OTHER = "OTHER"

    val all = listOf(KITCHEN, BATHROOM, BEDROOM, LIVING, LAUNDRY, YARD, CAR, UTILITIES, PETS, ERRANDS, OTHER)

    fun label(area: String): String = when (area) {
        KITCHEN -> "Kitchen"
        BATHROOM -> "Bathroom"
        BEDROOM -> "Bedrooms"
        LIVING -> "Living areas"
        LAUNDRY -> "Laundry"
        YARD -> "Yard & garden"
        CAR -> "Car"
        UTILITIES -> "HVAC & utilities"
        PETS -> "Pets"
        ERRANDS -> "Errands & admin"
        else -> "Other"
    }

    fun emoji(area: String): String = when (area) {
        KITCHEN -> "🍳"
        BATHROOM -> "🛁"
        BEDROOM -> "🛏️"
        LIVING -> "🛋️"
        LAUNDRY -> "🧺"
        YARD -> "🌿"
        CAR -> "🚗"
        UTILITIES -> "🔧"
        PETS -> "🐾"
        ERRANDS -> "📋"
        else -> "📌"
    }
}

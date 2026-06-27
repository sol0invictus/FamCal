package com.famcal.app.data.model

/** Supported repeat rules for an event, plus display labels. */
object Recurrence {
    const val NONE = "NONE"
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"

    val all = listOf(NONE, DAILY, WEEKLY, MONTHLY)

    fun label(value: String): String = when (value) {
        DAILY -> "Daily"
        WEEKLY -> "Weekly"
        MONTHLY -> "Monthly"
        else -> "Does not repeat"
    }
}

/** Minutes-before options offered for reminders, with display labels. */
object Reminders {
    const val NONE = -1

    val options = listOf(NONE, 0, 10, 30, 60, 1440)

    fun label(minutes: Int): String = when (minutes) {
        NONE -> "No reminder"
        0 -> "At time of event"
        10 -> "10 minutes before"
        30 -> "30 minutes before"
        60 -> "1 hour before"
        1440 -> "1 day before"
        else -> "$minutes minutes before"
    }
}

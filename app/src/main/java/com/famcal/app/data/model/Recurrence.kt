package com.famcal.app.data.model

/** Supported repeat rules for an event, plus display labels. */
object Recurrence {
    const val NONE = "NONE"
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val YEARLY = "YEARLY"

    val all = listOf(NONE, DAILY, WEEKLY, MONTHLY, YEARLY)

    fun label(value: String): String = when (value) {
        DAILY -> "Daily"
        WEEKLY -> "Weekly"
        MONTHLY -> "Monthly"
        YEARLY -> "Yearly"
        else -> "Does not repeat"
    }

    /** "Monthly" for an interval of 1, otherwise e.g. "Every 3 months". */
    fun label(value: String, interval: Int): String =
        if (value == NONE || interval <= 1) label(value) else "Every $interval ${unit(value, interval)}"

    /** The repeat unit as a word, e.g. "month" / "months". */
    fun unit(value: String, count: Int): String {
        val singular = when (value) {
            DAILY -> "day"
            WEEKLY -> "week"
            MONTHLY -> "month"
            YEARLY -> "year"
            else -> return ""
        }
        return if (count == 1) singular else singular + "s"
    }
}

/** Minutes-before options offered for reminders, with display labels. */
object Reminders {
    const val NONE = -1

    val options = listOf(NONE, 0, 10, 30, 60, 1440, 2 * 1440, 7 * 1440)

    fun label(minutes: Int): String = when (minutes) {
        NONE -> "No reminder"
        0 -> "At time of event"
        10 -> "10 minutes before"
        30 -> "30 minutes before"
        60 -> "1 hour before"
        1440 -> "1 day before"
        2 * 1440 -> "2 days before"
        7 * 1440 -> "1 week before"
        else -> "$minutes minutes before"
    }
}

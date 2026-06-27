package com.famcal.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A calendar event. Stored at `families/{familyId}/events/{eventId}`.
 * [createdBy] is the author's uid — the UI colors the event using that member's color.
 */
data class CalendarEvent(
    @DocumentId val id: String = "",
    val title: String = "",
    val notes: String = "",
    val location: String = "",
    val startAt: Timestamp = Timestamp.now(),
    val endAt: Timestamp = Timestamp.now(),
    val allDay: Boolean = false,
    val createdBy: String = "",
    /** Minutes before start to remind; -1 means no reminder, 0 means at start time. */
    val reminderMinutes: Int = -1,
    /** One of [Recurrence] values: NONE / DAILY / WEEKLY / MONTHLY. */
    val recurrence: String = Recurrence.NONE,
    @ServerTimestamp val updatedAt: Date? = null,
)

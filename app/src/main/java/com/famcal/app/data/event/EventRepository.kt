package com.famcal.app.data.event

import com.famcal.app.data.model.CalendarEvent
import com.famcal.app.data.retryListen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes events within a family's `events` subcollection. */
@Singleton
class EventRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private val currentUid: String get() = auth.currentUser?.uid.orEmpty()

    private fun events(familyId: String) =
        firestore.collection("families").document(familyId).collection("events")

    /** Streams all events for a family, ordered by start time, updating live. */
    fun observeEvents(familyId: String): Flow<List<CalendarEvent>> = callbackFlow {
        val registration = events(familyId)
            .orderBy("startAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(CalendarEvent::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }.retryListen("events")

    /** One-shot fetch of all events for a family (used by the device-calendar mirror). */
    suspend fun getEventsOnce(familyId: String): Result<List<CalendarEvent>> = runCatching {
        events(familyId).orderBy("startAt", Query.Direction.ASCENDING).get().await()
            .toObjects(CalendarEvent::class.java)
    }

    suspend fun getEvent(familyId: String, eventId: String): Result<CalendarEvent> = runCatching {
        events(familyId).document(eventId).get().await()
            .toObject(CalendarEvent::class.java)
            ?: error("Event not found")
    }

    suspend fun createEvent(familyId: String, event: CalendarEvent): Result<Unit> = runCatching {
        events(familyId).add(event.copy(updatedBy = currentUid)).await()
    }

    suspend fun updateEvent(familyId: String, event: CalendarEvent): Result<Unit> = runCatching {
        require(event.id.isNotBlank()) { "Cannot update an event without an id" }
        events(familyId).document(event.id).set(event.copy(updatedBy = currentUid)).await()
    }

    suspend fun deleteEvent(familyId: String, eventId: String): Result<Unit> = runCatching {
        events(familyId).document(eventId).delete().await()
    }

    /** Skips a single occurrence of a recurring event (delete "this event only"). */
    suspend fun excludeDate(familyId: String, eventId: String, dateIso: String): Result<Unit> =
        runCatching {
            events(familyId).document(eventId)
                .update(
                    mapOf("excludedDates" to FieldValue.arrayUnion(dateIso), "updatedBy" to currentUid),
                ).await()
        }

    /**
     * Edits a single occurrence of a recurring event ("this event only"): skips [dateIso] in
     * the series and saves [replacement] as a standalone event, atomically.
     */
    suspend fun detachOccurrence(
        familyId: String,
        seriesId: String,
        dateIso: String,
        replacement: CalendarEvent,
    ): Result<Unit> = runCatching {
        firestore.runBatch { batch ->
            batch.set(events(familyId).document(), replacement.copy(updatedBy = currentUid))
            batch.update(
                events(familyId).document(seriesId),
                mapOf(
                    "excludedDates" to FieldValue.arrayUnion(dateIso),
                    "updatedBy" to currentUid,
                ),
            )
        }.await()
    }

    /** Restores a previously-skipped occurrence (undo). */
    suspend fun includeDate(familyId: String, eventId: String, dateIso: String): Result<Unit> =
        runCatching {
            events(familyId).document(eventId)
                .update(
                    mapOf("excludedDates" to FieldValue.arrayRemove(dateIso), "updatedBy" to currentUid),
                ).await()
        }
}

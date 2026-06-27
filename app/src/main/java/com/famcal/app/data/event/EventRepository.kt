package com.famcal.app.data.event

import com.famcal.app.data.model.CalendarEvent
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
) {
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
    }

    suspend fun getEvent(familyId: String, eventId: String): Result<CalendarEvent> = runCatching {
        events(familyId).document(eventId).get().await()
            .toObject(CalendarEvent::class.java)
            ?: error("Event not found")
    }

    suspend fun createEvent(familyId: String, event: CalendarEvent): Result<Unit> = runCatching {
        events(familyId).add(event).await()
    }

    suspend fun updateEvent(familyId: String, event: CalendarEvent): Result<Unit> = runCatching {
        require(event.id.isNotBlank()) { "Cannot update an event without an id" }
        events(familyId).document(event.id).set(event).await()
    }

    suspend fun deleteEvent(familyId: String, eventId: String): Result<Unit> = runCatching {
        events(familyId).document(eventId).delete().await()
    }
}

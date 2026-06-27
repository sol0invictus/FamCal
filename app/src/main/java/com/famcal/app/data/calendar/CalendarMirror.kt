package com.famcal.app.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.famcal.app.data.model.CalendarEvent
import com.famcal.app.data.model.Recurrence
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** A writable calendar account present on the device. */
data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
)

/**
 * Mirrors a family's FamCal events into a chosen on-device calendar (which the OS then
 * syncs up to that Google/Outlook account). One-way; recurrence is written as an RRULE so
 * the device calendar expands it. A local mapping (in [CalendarSyncStore]) tracks which
 * device event each FamCal event maps to so updates and deletes stay in sync.
 */
@Singleton
class CalendarMirror @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: CalendarSyncStore,
) {
    fun hasPermission(): Boolean =
        granted(Manifest.permission.READ_CALENDAR) && granted(Manifest.permission.WRITE_CALENDAR)

    suspend fun listCalendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val result = mutableListOf<DeviceCalendar>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val access = cursor.getInt(3)
                // Only calendars we can write to.
                if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                result += DeviceCalendar(
                    id = cursor.getLong(0),
                    displayName = cursor.getString(1) ?: "Calendar",
                    accountName = cursor.getString(2) ?: "",
                )
            }
        }
        result
    }

    suspend fun sync(familyId: String, events: List<CalendarEvent>) = withContext(Dispatchers.IO) {
        val settings = store.settings.value
        if (!settings.enabled || settings.calendarId == CalendarSyncSettings.NO_CALENDAR) return@withContext
        if (!hasPermission()) return@withContext

        val resolver = context.contentResolver
        val mapping = store.getMapping()
        val prefix = "$familyId:"
        val seen = mutableSetOf<String>()

        for (event in events) {
            if (event.id.isBlank()) continue
            val key = prefix + event.id
            val hash = contentHash(event)
            val current = mapping[key]
            if (current != null && current.substringAfterLast(':') == hash) {
                seen += key
                continue
            }
            // Changed or new: drop the stale row (if any) and re-insert.
            current?.substringBefore(':')?.toLongOrNull()?.let { deleteEvent(it) }
            val deviceId = insertEvent(settings.calendarId, event)
            if (deviceId != null) {
                mapping[key] = "$deviceId:$hash"
                seen += key
            }
        }

        // Remove device events for this family whose FamCal source is gone.
        val stale = mapping.keys.filter { it.startsWith(prefix) && it !in seen }
        for (key in stale) {
            mapping[key]?.substringBefore(':')?.toLongOrNull()?.let { deleteEvent(it) }
            mapping.remove(key)
        }

        store.saveMapping(mapping)
    }

    /** Removes every mirrored event from the device calendar (used when turning sync off). */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val mapping = store.getMapping()
        if (hasPermission()) {
            for (value in mapping.values) {
                value.substringBefore(':').toLongOrNull()?.let { deleteEvent(it) }
            }
        }
        store.saveMapping(emptyMap())
    }

    private fun insertEvent(calendarId: Long, event: CalendarEvent): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title.ifBlank { "(untitled)" })
            put(CalendarContract.Events.DESCRIPTION, event.notes)
            put(CalendarContract.Events.EVENT_LOCATION, event.location)

            val recurring = event.recurrence != Recurrence.NONE
            val rrule = toRrule(event.recurrence)

            if (event.allDay) {
                val startUtc = event.startAt.toDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                put(CalendarContract.Events.DTSTART, startUtc)
                if (recurring) {
                    put(CalendarContract.Events.DURATION, "P1D")
                    put(CalendarContract.Events.RRULE, rrule)
                } else {
                    put(CalendarContract.Events.DTEND, startUtc + DAY_MILLIS)
                }
            } else {
                val startMillis = event.startAt.toDate().time
                val endMillis = event.endAt.toDate().time
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.DTSTART, startMillis)
                if (recurring) {
                    val seconds = Duration.ofMillis((endMillis - startMillis).coerceAtLeast(0)).seconds
                    put(CalendarContract.Events.DURATION, "PT${seconds}S")
                    put(CalendarContract.Events.RRULE, rrule)
                } else {
                    put(CalendarContract.Events.DTEND, endMillis)
                }
            }
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return uri?.lastPathSegment?.toLongOrNull()
    }

    private fun deleteEvent(deviceEventId: Long) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, deviceEventId)
        context.contentResolver.delete(uri, null, null)
    }

    private fun toRrule(recurrence: String): String? = when (recurrence) {
        Recurrence.DAILY -> "FREQ=DAILY"
        Recurrence.WEEKLY -> "FREQ=WEEKLY"
        Recurrence.MONTHLY -> "FREQ=MONTHLY"
        else -> null
    }

    private fun contentHash(event: CalendarEvent): String {
        val basis = listOf(
            event.title, event.notes, event.location,
            event.startAt.seconds, event.endAt.seconds,
            event.allDay, event.recurrence,
        ).joinToString("|")
        return basis.hashCode().toString()
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}

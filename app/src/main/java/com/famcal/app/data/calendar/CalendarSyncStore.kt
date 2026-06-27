package com.famcal.app.data.calendar

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Device-calendar mirror settings (per-device, local). */
data class CalendarSyncSettings(
    val enabled: Boolean = false,
    val calendarId: Long = NO_CALENDAR,
    val calendarName: String = "",
) {
    companion object {
        const val NO_CALENDAR = -1L
    }
}

/**
 * Persists which on-device calendar FamCal mirrors into, plus the mapping from
 * FamCal events to the device calendar event rows (so we can update/delete them).
 */
@Singleton
class CalendarSyncStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("famcal_calsync", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<CalendarSyncSettings> = _settings.asStateFlow()

    fun enable(calendarId: Long, calendarName: String) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putLong(KEY_CAL_ID, calendarId)
            .putString(KEY_CAL_NAME, calendarName)
            .apply()
        _settings.value = readSettings()
    }

    fun disable() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        _settings.value = readSettings()
    }

    /** Maps "familyId:eventId" -> "deviceEventId:contentHash". */
    fun getMapping(): MutableMap<String, String> {
        val json = prefs.getString(KEY_MAPPING, null) ?: return mutableMapOf()
        val obj = JSONObject(json)
        val map = mutableMapOf<String, String>()
        obj.keys().forEach { key -> map[key] = obj.getString(key) }
        return map
    }

    fun saveMapping(map: Map<String, String>) {
        prefs.edit().putString(KEY_MAPPING, JSONObject(map as Map<*, *>).toString()).apply()
    }

    private fun readSettings() = CalendarSyncSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        calendarId = prefs.getLong(KEY_CAL_ID, CalendarSyncSettings.NO_CALENDAR),
        calendarName = prefs.getString(KEY_CAL_NAME, "").orEmpty(),
    )

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_CAL_ID = "calendarId"
        const val KEY_CAL_NAME = "calendarName"
        const val KEY_MAPPING = "mapping"
    }
}

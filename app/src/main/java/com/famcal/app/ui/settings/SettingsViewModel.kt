package com.famcal.app.ui.settings

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.auth.AccountRepository
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.calendar.CalendarMirror
import com.famcal.app.data.calendar.CalendarSyncStore
import com.famcal.app.data.calendar.DeviceCalendar
import com.famcal.app.data.event.EventRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.notifications.NotificationPrefs
import com.famcal.app.ui.auth.PasswordResetController
import com.famcal.app.notifications.ReminderRescheduleWorker
import com.famcal.app.notifications.ReminderScheduler
import com.famcal.app.notifications.TaskDigest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val displayName: String = "",
    val email: String = "",
    val familyName: String = "",
    val inviteCode: String = "",
    val feedUrl: String? = null,
    val calendarSyncEnabled: Boolean = false,
    val calendarSyncName: String = "",
    val remindersEnabled: Boolean = true,
    val updatesEnabled: Boolean = true,
)

/** Task notification settings (kept apart from [SettingsUiState] to stay within combine's arity). */
data class TaskNotificationSettings(
    val reminders: Boolean = true,
    val updates: Boolean = true,
    /** Summary time as minutes after midnight. */
    val digestMinutes: Int = 8 * 60,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository,
    private val calendarMirror: CalendarMirror,
    private val calendarSyncStore: CalendarSyncStore,
    private val notificationPrefs: NotificationPrefs,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    private val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId required" }
    private val displayName = authRepository.currentUser?.displayName.orEmpty()
    private val email = authRepository.currentUser?.email.orEmpty()
    private val projectId = readProjectId(context)

    private val _availableCalendars = MutableStateFlow<List<DeviceCalendar>>(emptyList())
    val availableCalendars: StateFlow<List<DeviceCalendar>> = _availableCalendars.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        familyRepository.observeFamily(familyId),
        calendarSyncStore.settings,
        notificationPrefs.reminders,
        notificationPrefs.updates,
    ) { family, sync, reminders, updates ->
        SettingsUiState(
            displayName = displayName,
            email = email,
            familyName = family?.name.orEmpty(),
            inviteCode = family?.inviteCode.orEmpty(),
            feedUrl = feedUrl(family?.feedToken),
            calendarSyncEnabled = sync.enabled,
            calendarSyncName = sync.calendarName,
            remindersEnabled = reminders,
            updatesEnabled = updates,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(displayName = displayName, email = email),
    )

    init {
        viewModelScope.launch { familyRepository.ensureFeedToken(familyId) }
    }

    fun hasCalendarPermission(): Boolean = calendarMirror.hasPermission()

    /** Loads the device's writable calendars for the picker (call after permission granted). */
    fun loadCalendars() {
        viewModelScope.launch { _availableCalendars.value = calendarMirror.listCalendars() }
    }

    fun enableCalendarSync(calendar: DeviceCalendar) {
        calendarSyncStore.enable(calendar.id, "${calendar.displayName} (${calendar.accountName})")
        // Push the current events immediately.
        viewModelScope.launch {
            eventRepository.getEventsOnce(familyId).onSuccess { events ->
                calendarMirror.sync(familyId, events)
            }
        }
    }

    fun disableCalendarSync() {
        viewModelScope.launch {
            calendarMirror.clearAll()
            calendarSyncStore.disable()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            eventRepository.getEventsOnce(familyId).onSuccess { calendarMirror.sync(familyId, it) }
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        notificationPrefs.setReminders(enabled)
        // Off: cancel what's armed now. On: re-arm upcoming reminders for every family.
        if (enabled) ReminderRescheduleWorker.refreshNow(context) else reminderScheduler.cancelAll()
    }

    fun setUpdatesEnabled(enabled: Boolean) = notificationPrefs.setUpdates(enabled)

    val taskNotifications: StateFlow<TaskNotificationSettings> = combine(
        notificationPrefs.taskReminders,
        notificationPrefs.taskUpdates,
        notificationPrefs.digestMinutes,
    ) { reminders, updates, minutes -> TaskNotificationSettings(reminders, updates, minutes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskNotificationSettings())

    fun setTaskRemindersEnabled(enabled: Boolean) {
        notificationPrefs.setTaskReminders(enabled)
        TaskDigest.schedule(context) // arms or cancels the daily alarm
    }

    fun setTaskUpdatesEnabled(enabled: Boolean) = notificationPrefs.setTaskUpdates(enabled)

    fun setDigestTime(hour: Int, minute: Int) {
        notificationPrefs.setDigestMinutes(hour * 60 + minute)
        TaskDigest.schedule(context)
    }

    fun renameFamily(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { familyRepository.renameFamily(familyId, name) }
    }

    /** Only email/password accounts have a password to change (Google-only ones don't). */
    val canChangePassword: Boolean = authRepository.hasPasswordSignIn && email.isNotBlank()

    /** Change password = email a reset link to the signed-in account's own address. */
    val passwordReset = PasswordResetController(viewModelScope, authRepository)

    fun openChangePassword() = passwordReset.open(email, locked = true)

    fun signOut() = authRepository.signOut()

    fun deleteAccount() {
        viewModelScope.launch {
            // On success, auth state changes and the app routes back to sign-in.
            accountRepository.deleteAccount().onFailure { _deleteError.value = it.message }
        }
    }

    fun clearDeleteError() {
        _deleteError.value = null
    }

    private fun feedUrl(token: String?): String? {
        if (token.isNullOrBlank() || projectId.isNullOrBlank()) return null
        return "https://$FUNCTION_REGION-$projectId.cloudfunctions.net/calendarFeed" +
            "?familyId=$familyId&token=$token"
    }

    private fun readProjectId(context: Context): String? {
        val resId = context.resources.getIdentifier("project_id", "string", context.packageName)
        return if (resId == 0) null else context.getString(resId)
    }

    companion object {
        private const val FUNCTION_REGION = "us-central1"
    }
}

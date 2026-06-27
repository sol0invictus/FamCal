package com.famcal.app.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.famcal.app.data.ActiveFamilyStore
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.event.EventRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.util.RecurrenceExpander
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Reschedules upcoming reminders after a reboot (alarms don't survive restarts).
 * Enqueued by [BootReceiver]; reads the active family's events and re-arms the
 * near-term reminder alarms.
 */
@HiltWorker
class ReminderRescheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository,
    private val activeFamilyStore: ActiveFamilyStore,
    private val reminderScheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = authRepository.currentUser?.uid ?: return Result.success()
        val familyId = activeFamilyStore.activeFamilyId.value
            ?: familyRepository.observeFamilies(uid).first().firstOrNull()?.id
            ?: return Result.success()

        val events = eventRepository.getEventsOnce(familyId).getOrElse { return Result.success() }
        val today = LocalDate.now()
        val occurrences = RecurrenceExpander.expand(events, today, today.plusDays(8))
        reminderScheduler.sync(occurrences)
        return Result.success()
    }
}

package com.famcal.app.data.task

import com.famcal.app.data.model.HouseholdTask
import com.famcal.app.data.model.TaskCompletion
import com.famcal.app.data.model.TaskRepeat
import com.famcal.app.data.retryListen
import com.famcal.app.util.TaskSchedule
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What [TaskRepository.complete] changed, so the UI can offer Undo. */
data class CompletionUndo(val previous: HouseholdTask, val completionId: String)

/**
 * Household tasks (`families/{f}/tasks`) and their completion log
 * (`families/{f}/taskCompletions`). Writes that touch a task's schedule use field updates
 * rather than whole-document sets, so two people editing different fields don't clobber
 * each other.
 */
@Singleton
class TaskRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private val currentUid: String get() = auth.currentUser?.uid.orEmpty()

    private fun family(familyId: String) = firestore.collection("families").document(familyId)
    private fun tasks(familyId: String) = family(familyId).collection("tasks")
    private fun completions(familyId: String) = family(familyId).collection("taskCompletions")

    fun observeTasks(familyId: String): Flow<List<HouseholdTask>> = callbackFlow {
        val registration = tasks(familyId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(HouseholdTask::class.java).orEmpty())
        }
        awaitClose { registration.remove() }
    }.retryListen("tasks")

    fun observeTask(familyId: String, taskId: String): Flow<HouseholdTask?> = callbackFlow {
        val registration = tasks(familyId).document(taskId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.toObject(HouseholdTask::class.java))
        }
        awaitClose { registration.remove() }
    }.retryListen("task")

    /** A task's history, newest first (sorted here to avoid needing a composite index). */
    fun observeHistory(familyId: String, taskId: String): Flow<List<TaskCompletion>> = callbackFlow {
        val registration = completions(familyId).whereEqualTo("taskId", taskId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.toObjects(TaskCompletion::class.java).orEmpty()
                        .sortedByDescending { it.doneAt },
                )
            }
        awaitClose { registration.remove() }
    }.retryListen("task history")

    /** Every completion in the family since [since] (for the points leaderboard). */
    fun observeCompletionsSince(familyId: String, since: Timestamp): Flow<List<TaskCompletion>> =
        callbackFlow {
            val registration = completions(familyId).whereGreaterThanOrEqualTo("doneAt", since)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(
                        snapshot?.toObjects(TaskCompletion::class.java).orEmpty()
                            .sortedByDescending { it.doneAt },
                    )
                }
            awaitClose { registration.remove() }
        }.retryListen("completions")

    suspend fun getTasksOnce(familyId: String): Result<List<HouseholdTask>> = runCatching {
        tasks(familyId).get().await().toObjects(HouseholdTask::class.java)
    }

    suspend fun getTask(familyId: String, taskId: String): Result<HouseholdTask> = runCatching {
        tasks(familyId).document(taskId).get().await().toObject(HouseholdTask::class.java)
            ?: error("Task not found")
    }

    suspend fun createTask(familyId: String, task: HouseholdTask): Result<Unit> = runCatching {
        tasks(familyId).add(task.copy(createdBy = currentUid, updatedBy = currentUid)).await()
    }

    /** Adds several tasks at once (starter packs). */
    suspend fun createTasks(familyId: String, newTasks: List<HouseholdTask>): Result<Unit> = runCatching {
        for (chunk in newTasks.chunked(MAX_BATCH_WRITES)) {
            firestore.runBatch { batch ->
                chunk.forEach { task ->
                    batch.set(tasks(familyId).document(), task.copy(createdBy = currentUid, updatedBy = currentUid))
                }
            }.await()
        }
    }

    suspend fun updateTask(familyId: String, task: HouseholdTask): Result<Unit> = runCatching {
        require(task.id.isNotBlank()) { "Cannot update a task without an id" }
        tasks(familyId).document(task.id).set(task.copy(updatedBy = currentUid)).await()
    }

    /** Deletes the task; its completions stay so points already earned still count. */
    suspend fun deleteTask(familyId: String, taskId: String): Result<Unit> = runCatching {
        tasks(familyId).document(taskId).delete().await()
    }

    /**
     * Marks the current occurrence done by the signed-in user: logs a [TaskCompletion] and, in
     * the same batch, moves a repeating task to its next due date (and next person in its
     * rotation) or closes a one-off task.
     */
    suspend fun complete(
        familyId: String,
        task: HouseholdTask,
        today: LocalDate = LocalDate.now(),
    ): Result<CompletionUndo> = runCatching {
        val uid = currentUid
        val now = Timestamp.now()
        val completionRef = completions(familyId).document()
        val completion = TaskCompletion(
            taskId = task.id,
            taskTitle = task.title,
            doneBy = uid,
            doneAt = now,
            dueDate = task.dueDate,
            points = task.points,
        )
        val next = TaskSchedule.nextDue(
            TaskSchedule.parse(task.dueDate), task.repeatMode, task.repeatUnit, task.repeatInterval, today,
        )
        val changes = mutableMapOf<String, Any?>(
            "lastDoneAt" to now,
            "lastDoneBy" to uid,
            "updatedBy" to uid,
        )
        if (task.repeatMode == TaskRepeat.NONE) {
            changes["completedAt"] = now
        } else {
            changes["dueDate"] = next?.toString().orEmpty()
            changes["assignedTo"] = TaskSchedule.nextAssignee(task.assignedTo, task.rotation)
        }
        firestore.runBatch { batch ->
            batch.set(completionRef, completion)
            batch.update(tasks(familyId).document(task.id), changes)
        }.await()
        CompletionUndo(previous = task, completionId = completionRef.id)
    }

    /** Reverses [complete]: restores the schedule fields and removes the logged completion. */
    suspend fun undoComplete(familyId: String, undo: CompletionUndo): Result<Unit> = runCatching {
        val task = undo.previous
        firestore.runBatch { batch ->
            batch.update(
                tasks(familyId).document(task.id),
                mapOf(
                    "dueDate" to task.dueDate,
                    "assignedTo" to task.assignedTo,
                    "completedAt" to task.completedAt,
                    "lastDoneAt" to task.lastDoneAt,
                    "lastDoneBy" to task.lastDoneBy,
                    "updatedBy" to currentUid,
                ),
            )
            batch.delete(completions(familyId).document(undo.completionId))
        }.await()
    }

    /** Skips this occurrence of a repeating task: moves on without logging a completion. */
    suspend fun skip(familyId: String, task: HouseholdTask, today: LocalDate = LocalDate.now()): Result<Unit> =
        runCatching {
            val next = TaskSchedule.nextDue(
                TaskSchedule.parse(task.dueDate), task.repeatMode, task.repeatUnit, task.repeatInterval, today,
            ) ?: return@runCatching
            tasks(familyId).document(task.id)
                .update(mapOf("dueDate" to next.toString(), "updatedBy" to currentUid)).await()
        }

    /** Pushes the due date to [days] from today. */
    suspend fun snooze(familyId: String, taskId: String, days: Long, today: LocalDate = LocalDate.now()): Result<Unit> =
        runCatching {
            tasks(familyId).document(taskId)
                .update(mapOf("dueDate" to today.plusDays(days).toString(), "updatedBy" to currentUid)).await()
        }

    /** Takes an unassigned task. */
    suspend fun claim(familyId: String, taskId: String): Result<Unit> = runCatching {
        tasks(familyId).document(taskId)
            .update(mapOf("assignedTo" to currentUid, "updatedBy" to currentUid)).await()
    }

    /** Re-opens a finished one-off task. */
    suspend fun reopen(familyId: String, taskId: String): Result<Unit> = runCatching {
        tasks(familyId).document(taskId)
            .update(mapOf("completedAt" to FieldValue.delete(), "updatedBy" to currentUid)).await()
    }

    private companion object {
        const val MAX_BATCH_WRITES = 450
    }
}

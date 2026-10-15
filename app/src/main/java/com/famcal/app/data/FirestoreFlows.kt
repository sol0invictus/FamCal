package com.famcal.app.data

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen

private const val MAX_LISTEN_RETRIES = 5

/**
 * Firestore permanently stops a snapshot listener after an error. Right after creating or
 * joining a family the app already shows it from the local cache, so listeners can reach the
 * server before the write has committed and get PERMISSION_DENIED. Re-subscribe a few times
 * (with backoff) before giving up; a final failure is logged rather than crashing the caller.
 */
internal fun <T> Flow<T>.retryListen(label: String): Flow<T> = this
    .retryWhen { cause, attempt ->
        val transient = cause is FirebaseFirestoreException &&
            (cause.code == Code.PERMISSION_DENIED || cause.code == Code.UNAVAILABLE)
        if (transient && attempt < MAX_LISTEN_RETRIES) {
            delay(1_000L shl attempt.toInt()) // 1s, 2s, 4s, 8s, 16s
            true
        } else {
            false
        }
    }
    .catch { android.util.Log.w("FamCal", "Firestore listen failed ($label)", it) }

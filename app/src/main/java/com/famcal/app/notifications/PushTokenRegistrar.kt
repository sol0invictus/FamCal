package com.famcal.app.notifications

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Stores the current device's FCM token on the user's profile so they can be notified. */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    suspend fun register(uid: String) {
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            firestore.collection("users").document(uid)
                .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
                .await()
        }
    }
}

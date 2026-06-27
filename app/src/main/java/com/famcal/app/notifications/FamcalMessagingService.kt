package com.famcal.app.notifications

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM messages (sent by the `notifyOnEventWrite` Cloud Function when a family
 * member adds or changes an event) and keeps the user's FCM token stored in Firestore.
 */
class FamcalMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.collection("users").document(uid)
            .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "FamCal"
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        postNotification(
            context = this,
            channelId = CHANNEL_UPDATES,
            id = System.currentTimeMillis().toInt(),
            title = title,
            text = body,
        )
    }
}

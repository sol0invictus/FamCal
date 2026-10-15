package com.famcal.app.data.auth

import com.famcal.app.data.family.FamilyRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Handles full account + data deletion (required by Google Play for apps with sign-in). */
@Singleton
class AccountRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val familyRepository: FamilyRepository,
) {
    /**
     * Removes the user from all families, deletes their profile document, then deletes the
     * Firebase Auth account. Returns a friendly failure if Firebase requires a recent login.
     *
     * Checked up front, before any data is touched, so a stale sign-in can't leave the user
     * removed from their families but with the account still alive. Runs [NonCancellable]:
     * leaving the last family re-routes the app and clears the calling screen's scope, which
     * must not abort the deletion halfway.
     */
    suspend fun deleteAccount(): Result<Unit> = withContext(NonCancellable) {
        runCatching<Unit> {
            val user = auth.currentUser ?: error("You're not signed in.")
            val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
            if (System.currentTimeMillis() - lastSignIn > RECENT_LOGIN_MILLIS) error(REAUTH_MESSAGE)

            val families = firestore.collection("families")
                .whereArrayContains("memberUids", user.uid)
                .get().await()
            for (doc in families.documents) {
                familyRepository.leaveFamily(doc.id, user).getOrThrow()
            }

            firestore.collection("users").document(user.uid).delete().await()

            try {
                user.delete().await()
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                error(REAUTH_MESSAGE)
            }
        }
    }

    companion object {
        // Firebase requires a sign-in within roughly the last 5 minutes to delete an account.
        private const val RECENT_LOGIN_MILLIS = 5 * 60 * 1000L
        private const val REAUTH_MESSAGE =
            "For security, please sign out and sign back in, then delete your account."
    }
}

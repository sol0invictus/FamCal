package com.famcal.app.data.auth

import com.famcal.app.data.family.FamilyRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
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
     */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("You're not signed in.")

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
            error("For security, please sign out and sign back in, then delete your account.")
        }
    }
}

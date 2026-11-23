package com.famcal.app.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Firebase Authentication. Exposes the current user as a [Flow] so the rest
 * of the app can react to sign-in / sign-out, plus suspend helpers for the actual
 * auth operations.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Emits the signed-in user (or null) and updates whenever auth state changes. */
    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
    ): Result<FirebaseUser> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = requireNotNull(result.user) { "Sign-up returned no user" }
        if (displayName.isNotBlank()) {
            user.updateProfile(
                UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build(),
            ).await()
        }
        user
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            requireNotNull(result.user) { "Sign-in returned no user" }
        }

    /**
     * Emails a Firebase password-reset link. The link opens Firebase's hosted page where the
     * user picks a new password; they then sign in here with it. The email is sent in the
     * device's language. With Firebase's email-enumeration protection on (the default for
     * newer projects) this succeeds even when no account uses [email], and sends nothing.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.useAppLanguage()
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** True when the signed-in user can sign in with an email + password (vs. Google only). */
    val hasPasswordSignIn: Boolean
        get() = auth.currentUser?.providerData?.any { it.providerId == "password" } == true

    fun signOut() = auth.signOut()
}

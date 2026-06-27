package com.famcal.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the "Sign in with Google" flow via Credential Manager and returns a Google
 * ID token that [AuthRepository.signInWithGoogle] can exchange for a Firebase session.
 *
 * The web client ID is read at runtime from the `default_web_client_id` string that
 * the google-services plugin generates *only when the Google sign-in provider is
 * enabled* in Firebase. If it's absent we surface a clear error instead of failing
 * to compile, so Google sign-in is effectively optional.
 */
@Singleton
class GoogleSignInClient @Inject constructor() {

    /** @param context an Activity context (e.g. from `LocalContext.current` in Compose). */
    suspend fun signIn(context: Context): Result<String> = runCatching {
        val serverClientId = webClientId(context)
            ?: error("Google sign-in isn't configured yet. Enable the Google provider in Firebase.")

        val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val response = CredentialManager.create(context).getCredential(context, request)
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            error("Unexpected credential type from Google sign-in")
        }
    }

    fun isConfigured(context: Context): Boolean = webClientId(context) != null

    private fun webClientId(context: Context): String? {
        val resId = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName,
        )
        return if (resId == 0) null else context.getString(resId)
    }
}

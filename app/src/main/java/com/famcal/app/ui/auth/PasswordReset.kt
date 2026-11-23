package com.famcal.app.ui.auth

import android.util.Patterns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.famcal.app.data.auth.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the "reset your password" dialog. */
data class PasswordResetState(
    val isOpen: Boolean = false,
    val email: String = "",
    /** Signed-in "change password": the address is the account's and can't be edited. */
    val emailLocked: Boolean = false,
    val isSending: Boolean = false,
    /** Address the link was last sent to; non-null switches the dialog to its "sent" view. */
    val sentTo: String? = null,
    val error: String? = null,
    /** Seconds until "Resend" is allowed again (Firebase rate-limits reset emails). */
    val resendInSeconds: Int = 0,
) {
    val canSend: Boolean get() = email.isNotBlank() && !isSending && resendInSeconds == 0
}

/**
 * Drives the password-reset dialog for both the sign-in screen (user types their email)
 * and Settings → Change password (the signed-in account's email). Owned by a view model,
 * running in its [scope].
 */
class PasswordResetController(
    private val scope: CoroutineScope,
    private val authRepository: AuthRepository,
) {
    private val _state = MutableStateFlow(PasswordResetState())
    val state: StateFlow<PasswordResetState> = _state.asStateFlow()

    private var cooldownJob: Job? = null

    fun open(prefillEmail: String, locked: Boolean = false) = _state.update {
        // Keep the cooldown across reopenings so the dialog can't be used to spam.
        PasswordResetState(
            isOpen = true,
            email = prefillEmail.trim(),
            emailLocked = locked,
            resendInSeconds = it.resendInSeconds,
        )
    }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun close() = _state.update { it.copy(isOpen = false) }

    /** Back from the "sent" view to fix a mistyped address. */
    fun editEmail() = _state.update { it.copy(sentTo = null, error = null) }

    fun send() {
        val email = _state.value.email.trim()
        if (!_state.value.canSend) return
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _state.update { it.copy(error = "That doesn't look like an email address.") }
            return
        }
        _state.update { it.copy(isSending = true, error = null) }
        scope.launch {
            authRepository.sendPasswordReset(email)
                .onSuccess {
                    _state.update { it.copy(isSending = false, sentTo = email) }
                    startCooldown()
                }
                .onFailure { e ->
                    _state.update { it.copy(isSending = false, error = e.resetMessage()) }
                }
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = scope.launch {
            for (left in RESEND_COOLDOWN_SECONDS downTo 0) {
                _state.update { it.copy(resendInSeconds = left) }
                if (left > 0) delay(1_000)
            }
        }
    }

    private fun Throwable.resetMessage(): String = when (this) {
        is FirebaseAuthInvalidUserException ->
            "No FamCal account uses that email. Check the spelling, or create an account."
        is FirebaseAuthInvalidCredentialsException -> "That doesn't look like an email address."
        is FirebaseTooManyRequestsException ->
            "Too many reset requests. Please wait a few minutes and try again."
        is FirebaseNetworkException -> "No internet connection. Connect and try again."
        else -> "Couldn't send the reset email. Please try again."
    }

    private companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}

/** The reset dialog: enter email → "check your inbox" with resend. */
@Composable
fun PasswordResetDialog(
    state: PasswordResetState,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit,
    onEditEmail: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.isOpen) return
    val sentTo = state.sentTo

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (sentTo == null) "Reset your password" else "Check your email") },
        text = {
            Column {
                if (sentTo == null) {
                    Text(
                        if (state.emailLocked) {
                            "We'll email a link to this address. Open it to choose a new password."
                        } else {
                            "Enter the email you signed up with and we'll send you a link to " +
                                "choose a new password."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !state.emailLocked && !state.isSending,
                        isError = state.error != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("We sent a password reset link to", style = MaterialTheme.typography.bodyMedium)
                    Text(sentTo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (!state.emailLocked) {
                        Text(
                            "Wrong address? Change it",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable(onClick = onEditEmail)
                                .padding(vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "1. Open the email and tap the link.\n" +
                            "2. Choose a new password on the page that opens.\n" +
                            "3. Come back here and sign in with it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Don't see it within a few minutes? Check your spam or junk folder. " +
                            "No email at all usually means there's no account for that address.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (sentTo == null) {
                TextButton(onClick = onSend, enabled = state.canSend) {
                    if (state.isSending) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (state.resendInSeconds > 0) "Send (${state.resendInSeconds}s)" else "Send link")
                    }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (sentTo == null) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            } else {
                TextButton(onClick = onSend, enabled = state.canSend) {
                    Text(if (state.resendInSeconds > 0) "Resend in ${state.resendInSeconds}s" else "Resend")
                }
            }
        },
    )
}

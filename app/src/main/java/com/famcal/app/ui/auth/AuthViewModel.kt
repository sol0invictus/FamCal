package com.famcal.app.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.auth.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthFormState(
    val isSignUp: Boolean = false,
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() &&
            password.length >= MIN_PASSWORD_LENGTH &&
            (!isSignUp || displayName.isNotBlank()) &&
            !isSubmitting

    companion object {
        const val MIN_PASSWORD_LENGTH = 6
    }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val googleSignInClient: GoogleSignInClient,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthFormState())
    val state: StateFlow<AuthFormState> = _state.asStateFlow()

    fun onDisplayNameChange(value: String) = _state.update { it.copy(displayName = value, error = null, info = null) }
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null, info = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null, info = null) }

    fun toggleMode() = _state.update {
        it.copy(isSignUp = !it.isSignUp, error = null)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = if (current.isSignUp) {
                authRepository.signUpWithEmail(current.email, current.password, current.displayName)
            } else {
                authRepository.signInWithEmail(current.email, current.password)
            }
            // On success, AppViewModel observes the auth state and routes away.
            result.onFailure { e -> _state.update { it.copy(error = e.friendlyMessage()) } }
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    fun signInWithGoogle(context: Context) {
        _state.update { it.copy(isSubmitting = true, error = null, info = null) }
        viewModelScope.launch {
            googleSignInClient.signIn(context)
                .mapCatching { idToken -> authRepository.signInWithGoogle(idToken).getOrThrow() }
                .onFailure { e -> _state.update { it.copy(error = e.friendlyMessage()) } }
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    fun sendPasswordReset() {
        val email = _state.value.email
        if (email.isBlank()) {
            _state.update { it.copy(error = "Enter your email above first, then tap this.") }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null, info = null) }
        viewModelScope.launch {
            authRepository.sendPasswordReset(email)
                .onSuccess { _state.update { it.copy(info = "Password reset email sent to $email.") } }
                .onFailure { e -> _state.update { it.copy(error = e.friendlyMessage()) } }
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    private fun Throwable.friendlyMessage(): String = when (this) {
        is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters."
        is FirebaseAuthUserCollisionException -> "An account with that email already exists. Try signing in."
        is FirebaseAuthInvalidUserException -> "No account found with that email."
        is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
        else -> "Something went wrong. Please check your connection and try again."
    }
}

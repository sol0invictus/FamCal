package com.famcal.app.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.ActiveFamilyStore
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.family.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FamilySetupState(
    val isJoining: Boolean = false,
    val familyName: String = "",
    val joinCode: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && if (isJoining) joinCode.trim().length >= CODE_LENGTH else familyName.isNotBlank()

    companion object {
        const val CODE_LENGTH = 6
    }
}

@HiltViewModel
class FamilySetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val activeFamilyStore: ActiveFamilyStore,
) : ViewModel() {

    private val _state = MutableStateFlow(FamilySetupState())
    val state: StateFlow<FamilySetupState> = _state.asStateFlow()

    fun onFamilyNameChange(value: String) = _state.update { it.copy(familyName = value, error = null) }
    fun onJoinCodeChange(value: String) =
        _state.update { it.copy(joinCode = value.uppercase(), error = null) }

    fun setJoining(joining: Boolean) = _state.update { it.copy(isJoining = joining, error = null) }

    fun submit(onDone: () -> Unit = {}) {
        val current = _state.value
        val user = authRepository.currentUser
        if (!current.canSubmit || user == null) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = if (current.isJoining) {
                familyRepository.joinFamily(current.joinCode, user)
            } else {
                familyRepository.createFamily(current.familyName, user)
            }
            result
                .onSuccess { family ->
                    // Make the new family active; the initial-setup case also re-routes
                    // automatically once it appears in the live family query.
                    activeFamilyStore.setActiveFamilyId(family.id)
                    onDone()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(error = e.message ?: "Couldn't complete that. Please try again.")
                    }
                }
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    fun signOut() = authRepository.signOut()
}

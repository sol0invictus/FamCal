package com.famcal.app.ui.onboarding

import androidx.lifecycle.ViewModel
import com.famcal.app.data.OnboardingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val store: OnboardingStore,
) : ViewModel() {
    val seen: StateFlow<Boolean> = store.seen
    fun markSeen() = store.markSeen()
}

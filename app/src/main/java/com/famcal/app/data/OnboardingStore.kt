package com.famcal.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers whether the first-run onboarding has been shown. */
@Singleton
class OnboardingStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("famcal_onboarding", Context.MODE_PRIVATE)

    private val _seen = MutableStateFlow(prefs.getBoolean(KEY, false))
    val seen: StateFlow<Boolean> = _seen.asStateFlow()

    fun markSeen() {
        prefs.edit().putBoolean(KEY, true).apply()
        _seen.value = true
    }

    private companion object {
        const val KEY = "seen"
    }
}

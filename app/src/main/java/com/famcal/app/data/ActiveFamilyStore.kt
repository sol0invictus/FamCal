package com.famcal.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which family is currently active (when a user belongs to more than one).
 * Backed by SharedPreferences and exposed as a [StateFlow] so both [com.famcal.app.ui.AppViewModel]
 * and the family-management screen stay in sync.
 */
@Singleton
class ActiveFamilyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("famcal_prefs", Context.MODE_PRIVATE)

    private val _activeFamilyId = MutableStateFlow(prefs.getString(KEY, null))
    val activeFamilyId: StateFlow<String?> = _activeFamilyId.asStateFlow()

    fun setActiveFamilyId(id: String?) {
        prefs.edit().putString(KEY, id).apply()
        _activeFamilyId.value = id
    }

    private companion object {
        const val KEY = "activeFamilyId"
    }
}

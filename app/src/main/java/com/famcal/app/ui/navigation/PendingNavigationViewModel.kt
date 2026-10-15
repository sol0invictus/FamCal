package com.famcal.app.ui.navigation

import androidx.lifecycle.ViewModel
import com.famcal.app.data.OpenRequest
import com.famcal.app.data.PendingNavigation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Exposes a pending notification tap so the nav graph can return to the calendar. */
@HiltViewModel
class PendingNavigationViewModel @Inject constructor(
    pendingNavigation: PendingNavigation,
) : ViewModel() {
    val openRequest: StateFlow<OpenRequest?> = pendingNavigation.request
}

package com.famcal.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.famcal.app.data.ActiveFamilyStore
import com.famcal.app.data.OpenRequest
import com.famcal.app.data.PendingNavigation
import com.famcal.app.notifications.EXTRA_DATE
import com.famcal.app.notifications.EXTRA_FAMILY_ID
import com.famcal.app.ui.FamCalRoot
import com.famcal.app.ui.theme.FamCalTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pendingNavigation: PendingNavigation
    @Inject lateinit var activeFamilyStore: ActiveFamilyStore

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        if (savedInstanceState == null) handleNotificationTap(intent)
        setContent {
            FamCalApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationTap(intent)
    }

    /** A tapped reminder/update opens that family's calendar on the event's day. */
    private fun handleNotificationTap(intent: Intent?) {
        val date = intent?.getStringExtra(EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return
        val familyId = intent.getStringExtra(EXTRA_FAMILY_ID)
        // AppViewModel falls back to another family if this one is no longer valid.
        familyId?.let(activeFamilyStore::setActiveFamilyId)
        pendingNavigation.open(OpenRequest(familyId, date))
        intent.removeExtra(EXTRA_DATE)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun FamCalApp() {
    FamCalTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            FamCalRoot()
        }
    }
}

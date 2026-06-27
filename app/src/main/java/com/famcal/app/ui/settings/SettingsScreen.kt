package com.famcal.app.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageFamilies: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var showCalendarPicker by remember { mutableStateOf(false) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            viewModel.loadCalendars()
            showCalendarPicker = true
        }
    }

    fun startCalendarSync() {
        if (viewModel.hasCalendarPermission()) {
            viewModel.loadCalendars()
            showCalendarPicker = true
        } else {
            calendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section("Account") {
                LabeledValue("Name", state.displayName.ifBlank { "—" })
                LabeledValue("Email", state.email.ifBlank { "—" })
            }

            Section("Family") {
                LabeledValue("Name", state.familyName.ifBlank { "—" })
                CopyableRow(
                    label = "Invite code",
                    value = state.inviteCode.ifBlank { "—" },
                    onCopy = { clipboard.setText(AnnotatedString(state.inviteCode)) },
                    enabled = state.inviteCode.isNotBlank(),
                )
            }

            Section("Add to Google or Outlook") {
                Text(
                    "Subscribe to this private calendar URL from Google Calendar (Other " +
                        "calendars → From URL) or Outlook (Add calendar → Subscribe from web). " +
                        "Your FamCal events will appear there and refresh automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val feedUrl = state.feedUrl
                if (feedUrl != null) {
                    Text(
                        text = feedUrl,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(feedUrl)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.height(0.dp))
                        Text("  Copy subscription URL")
                    }
                    Text(
                        "Note: the feed only works after the calendar Cloud Function is " +
                            "deployed (see README).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text(
                        "Subscription URL isn't available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Section("Sync to this phone's calendar") {
                Text(
                    "Automatically copy family events into a calendar account on this phone " +
                        "(your Google/Outlook account), so they appear alongside your other " +
                        "calendars. One-way; set up once per phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Mirror events to this phone", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.calendarSyncEnabled,
                        onCheckedChange = { on ->
                            if (on) startCalendarSync() else viewModel.disableCalendarSync()
                        },
                    )
                }
                if (state.calendarSyncEnabled && state.calendarSyncName.isNotBlank()) {
                    Text(
                        "Syncing to: ${state.calendarSyncName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            OutlinedButton(
                onClick = onManageFamilies,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Manage families")
            }

            OutlinedButton(
                onClick = viewModel::signOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign out")
            }
        }
    }

    if (showCalendarPicker) {
        val calendars by viewModel.availableCalendars.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showCalendarPicker = false },
            title = { Text("Choose a calendar") },
            text = {
                Column {
                    if (calendars.isEmpty()) {
                        Text(
                            "No writable calendars found on this phone. Add a Google or " +
                                "Outlook account in Android Settings first, then try again.",
                        )
                    } else {
                        calendars.forEach { calendar ->
                            Text(
                                text = "${calendar.displayName} (${calendar.accountName})",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.enableCalendarSync(calendar)
                                        showCalendarPicker = false
                                    }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCalendarPicker = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CopyableRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onCopy, enabled = enabled) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $label")
        }
    }
    HorizontalDivider()
}

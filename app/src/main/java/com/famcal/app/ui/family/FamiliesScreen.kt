package com.famcal.app.ui.family

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famcal.app.data.model.Family

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamiliesScreen(
    onBack: () -> Unit,
    onAddFamily: () -> Unit,
    viewModel: FamiliesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var leaving by remember { mutableStateOf<Family?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Families") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Tap a family to switch to it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.families.forEach { family ->
                FamilyRow(
                    family = family,
                    isActive = family.id == state.activeFamilyId,
                    onClick = { viewModel.switchTo(family.id) },
                    onLeave = { leaving = family },
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onAddFamily, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Create or join another family")
            }
        }
    }

    leaving?.let { family ->
        AlertDialog(
            onDismissRequest = { leaving = null },
            title = { Text("Leave \"${family.name}\"?") },
            text = { Text("You'll stop seeing this family's calendar. You can rejoin later with its invite code.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.leave(family.id)
                        leaving = null
                    },
                ) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { leaving = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FamilyRow(
    family: Family,
    isActive: Boolean,
    onClick: () -> Unit,
    onLeave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(family.name.ifBlank { "(unnamed family)" }, style = MaterialTheme.typography.titleMedium)
                if (isActive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(16.dp),
                        )
                        Text(
                            "  Active",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            TextButton(onClick = onLeave) {
                Text("Leave", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

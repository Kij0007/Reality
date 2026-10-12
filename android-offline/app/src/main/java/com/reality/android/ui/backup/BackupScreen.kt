package com.reality.android.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reality.android.R
import java.time.LocalDate

@Composable
fun BackupScreen(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { stringResource(it) }
    LaunchedEffect(message) {
        if (message != null) { snackbar.showSnackbar(message); viewModel.dismissMessage() }
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::export)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::inspect)
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.backup_description))
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.backup_export_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.backup_export_help))
                    Button(
                        onClick = { create.launch("Reality-${LocalDate.now()}.json") },
                        enabled = !state.busy && !state.mutationBusy, modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.backup_export)) }
                    Text(stringResource(R.string.backup_plaintext), style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.backup_restore_help))
                    OutlinedButton(
                        onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        enabled = !state.busy && !state.mutationBusy, modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.backup_choose)) }
                }
            }
            Text(stringResource(R.string.backup_migration_help), style = MaterialTheme.typography.bodyMedium)
            if (state.busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text(stringResource(R.string.backup_working))
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(72.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    state.pending?.let { backup ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.backup_counts, backup.activities.size, backup.sessions.size, backup.breaks.size))
                    Text(stringResource(R.string.backup_zone, backup.timeZone))
                    Text(stringResource(R.string.backup_confirm_warning))
                }
            },
            dismissButton = { TextButton(viewModel::cancelRestore, enabled = !state.busy) { Text(stringResource(R.string.backup_cancel)) } },
            confirmButton = {
                TextButton(viewModel::restore, enabled = !state.busy && !state.mutationBusy) {
                    Text(stringResource(R.string.backup_replace), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

package com.reality.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reality.android.R
import com.reality.android.data.repository.ThemePreference
import com.reality.android.ui.auth.AccountCard

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { stringResource(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            AccountCard()
            SettingsCard(R.string.settings_server) {
                OutlinedTextField(
                    value = state.url, onValueChange = viewModel::changeUrl,
                    label = { Text(stringResource(R.string.settings_url)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    enabled = !state.saving && !state.testing && !state.mutationBusy
                )
                Text(stringResource(R.string.settings_url_help), style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.mutationBusy) Text(
                    stringResource(R.string.settings_mutation_busy),
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = viewModel::saveConnection,
                    enabled = !state.saving && !state.testing && !state.mutationBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(if (state.saving) R.string.settings_saving else R.string.settings_save)) }
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = !state.saving && !state.testing,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(if (state.testing) R.string.settings_testing else R.string.settings_test)) }
            }
            SettingsCard(R.string.settings_theme) {
                ThemePreference.entries.forEach { preference ->
                    val title = when (preference) {
                        ThemePreference.SYSTEM -> R.string.theme_system
                        ThemePreference.LIGHT -> R.string.theme_light
                        ThemePreference.DARK -> R.string.theme_dark
                    }
                    FilterChip(
                        selected = state.settings?.theme == preference,
                        onClick = { viewModel.setTheme(preference) },
                        label = { Text(stringResource(title)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            SettingsCard(R.string.settings_clock) {
                OutlinedTextField(
                    value = state.zone, onValueChange = viewModel::changeZone,
                    label = { Text(stringResource(R.string.settings_zone)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    enabled = !state.saving && !state.mutationBusy
                )
                Text(stringResource(R.string.settings_zone_help), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = viewModel::saveZone,
                    enabled = !state.saving && !state.testing && !state.mutationBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_zone_save)) }
            }
            SettingsCard(R.string.settings_about) {
                Text(stringResource(R.string.settings_about_description))
            }
            Spacer(Modifier.height(56.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }
}

@Composable
private fun SettingsCard(title: Int, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

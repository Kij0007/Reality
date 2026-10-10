package com.reality.android.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reality.android.R
import com.reality.android.ui.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(model: AuthViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    var settings by rememberSaveable { mutableStateOf(false) }
    BackHandler(settings && !state.submitting) { settings = false }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(if (settings) R.string.nav_settings else R.string.app_name)) }, actions = {
            if (!settings) IconButton(onClick = { settings = true }, enabled = !state.submitting) {
                Icon(Icons.Default.Settings, stringResource(R.string.nav_settings))
            } else TextButton(onClick = { settings = false }, enabled = !state.mutationBusy) { Text(stringResource(R.string.go_back)) }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (settings) { SettingsScreen(); return@Box }
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(stringResource(R.string.auth_heading), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.auth_description), style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(state.username, model::username, label = { Text(stringResource(R.string.auth_username)) },
                    singleLine = true, enabled = !state.submitting, modifier = Modifier.fillMaxWidth())
                if (state.registering) OutlinedTextField(state.displayName, model::displayName,
                    label = { Text(stringResource(R.string.auth_display_name)) }, singleLine = true,
                    enabled = !state.submitting, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(state.password, model::password, label = { Text(stringResource(R.string.auth_password)) },
                    singleLine = true, enabled = !state.submitting, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (state.registering) Text(stringResource(R.string.auth_password_help), style = MaterialTheme.typography.bodySmall)
                (state.error ?: session.notice)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.submitting) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(if (state.waking) R.string.auth_waking else R.string.auth_working))
                }
                Button(onClick = model::submit, enabled = !state.submitting && !state.mutationBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (state.registering) R.string.auth_register else R.string.auth_login))
                }
                TextButton(onClick = model::toggleRegistration, enabled = !state.submitting, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (state.registering) R.string.auth_existing_account else R.string.auth_new_account))
                }
            }
        }
    }
}

@Composable
fun AccountCard(model: AuthViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val user = session.session?.auth?.user ?: return
    var confirm by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.auth_account), style = MaterialTheme.typography.titleMedium)
            Text(user.displayName)
            Text("@${user.username}", style = MaterialTheme.typography.bodySmall)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = { confirm = true }, enabled = !state.submitting && !state.mutationBusy) {
                Text(stringResource(R.string.auth_logout))
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(stringResource(R.string.auth_logout)) },
        text = { Text(stringResource(R.string.auth_logout_confirm)) },
        confirmButton = { TextButton(onClick = { confirm = false; model.logout() }) { Text(stringResource(R.string.auth_logout)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.auth_cancel)) } })
}

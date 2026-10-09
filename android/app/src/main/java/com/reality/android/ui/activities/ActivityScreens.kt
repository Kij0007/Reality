package com.reality.android.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reality.android.R
import com.reality.android.core.util.formatDate
import com.reality.android.core.util.formatDateTime
import com.reality.android.core.util.formatDuration
import com.reality.android.data.remote.dto.ActivityCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen(
    onCreate: () -> Unit,
    onDetail: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ActivitiesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkflowForegroundRefresh(viewModel::refresh)
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.wf_your_activities), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.wf_activities_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onCreate) { Text(stringResource(R.string.wf_create_activity)) }
                    OutlinedTextField(
                        value = state.query, onValueChange = viewModel::setQuery,
                        label = { Text(stringResource(R.string.wf_search_activities)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    var categoryMenu by remember { mutableStateOf(false) }
                    var sortMenu by remember { mutableStateOf(false) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column {
                            OutlinedButton(onClick = { categoryMenu = true }) {
                                Text(state.category?.let { stringResource(categoryLabel(it)) } ?: stringResource(R.string.wf_all_categories))
                            }
                            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.wf_all_categories)) },
                                    onClick = { viewModel.setCategory(null); categoryMenu = false })
                                ActivityCategory.entries.forEach { category ->
                                    DropdownMenuItem(text = { Text(stringResource(categoryLabel(category))) },
                                        onClick = { viewModel.setCategory(category); categoryMenu = false })
                                }
                            }
                        }
                        Column {
                            OutlinedButton(onClick = { sortMenu = true }) { Text(stringResource(R.string.wf_sort)) }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                ActivitySort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(when (sort) {
                                                ActivitySort.NAME -> R.string.wf_sort_name
                                                ActivitySort.NEWEST -> R.string.wf_sort_newest
                                                ActivitySort.DURATION -> R.string.wf_sort_duration
                                            }))
                                        }, onClick = { viewModel.setSort(sort); sortMenu = false },
                                    )
                                }
                            }
                        }
                    }
                    WorkflowError(state.error, viewModel::refresh)
                }
            }
            if (state.visible.isEmpty() && !state.loading) {
                item {
                    WorkflowEmpty(
                        stringResource(if (state.activities.isEmpty()) R.string.wf_no_activities else R.string.wf_no_matches),
                        stringResource(if (state.activities.isEmpty()) R.string.wf_no_activities_body else R.string.wf_no_matches_body),
                        if (state.activities.isEmpty()) stringResource(R.string.wf_create_activity) else null,
                        if (state.activities.isEmpty()) onCreate else null,
                    )
                }
            }
            items(state.visible, key = { it.id }) { activity ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { onDetail(activity.id) }) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(activity.name ?: stringResource(R.string.wf_unnamed_activity), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(categoryLabel(activity.category)), color = MaterialTheme.colorScheme.primary)
                        Text(activity.minimumDuration?.let { stringResource(R.string.wf_minutes_target, it) } ?: stringResource(R.string.wf_target_unavailable))
                        Text(
                            if (activity.startDate.isNullOrBlank() || activity.scheduledDays.isNullOrEmpty())
                                stringResource(R.string.wf_incomplete_schedule)
                            else stringResource(R.string.wf_starts_on, formatDate(activity.startDate)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { onEdit(activity.id) }) { Text(stringResource(R.string.wf_edit)) }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityFormScreen(
    activityId: Long?,
    onSaved: () -> Unit,
    viewModel: ActivityFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(activityId) { viewModel.load(activityId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(if (activityId == null) R.string.wf_create_activity else R.string.wf_edit_activity),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (state.loading) {
            CircularProgressIndicator()
        } else {
            val enabled = !state.saving && !state.uncertainWrite
            WorkflowError(state.error, if (!state.uncertainWrite && activityId != null) ({ viewModel.load(activityId, true) }) else null)
            if (state.uncertainWrite) Text(
                stringResource(R.string.wf_uncertain_create),
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedTextField(
                value = state.name, onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.wf_name)) },
                supportingText = { Text(stringResource(R.string.wf_name_hint)) },
                enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = state.duration, onValueChange = viewModel::setDuration,
                label = { Text(stringResource(R.string.wf_minimum_minutes)) },
                enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            Text(stringResource(R.string.wf_category), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityCategory.entries.forEach { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { viewModel.setCategory(category) },
                        label = { Text(stringResource(categoryLabel(category))) }, enabled = enabled,
                    )
                }
            }
            WorkflowDateInput(
                label = stringResource(R.string.wf_commitment_start),
                value = state.startDate, enabled = enabled, onValueChange = viewModel::setStartDate,
            )
            Text(stringResource(R.string.wf_start_date_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.wf_repeat_days), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                workflowDays.forEach { (day, label) ->
                    FilterChip(
                        selected = day in state.days, onClick = { viewModel.toggleDay(day) },
                        label = { Text(stringResource(label)) }, enabled = enabled,
                    )
                }
            }
            state.fieldErrors.values.distinct().forEach { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = viewModel::save, enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (state.saving) R.string.wf_saving else R.string.wf_save_activity)) }
            if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    activityId: Long,
    onEdit: (Long) -> Unit,
    onTrack: (Long) -> Unit,
    onProgress: (Long) -> Unit,
    onReport: (Long) -> Unit,
    onDeleted: () -> Unit,
    viewModel: ActivityDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmDelete by rememberSaveable(activityId) { mutableStateOf(false) }
    LaunchedEffect(activityId) { viewModel.load(activityId) }
    WorkflowForegroundRefresh(viewModel::refresh)
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(context.getString(it)); viewModel.clearNotice() }
    }
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WorkflowError(state.error, viewModel::refresh)
            state.activity?.let { activity ->
                Text(activity.name ?: stringResource(R.string.wf_unnamed_activity), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(categoryLabel(activity.category)), color = MaterialTheme.colorScheme.primary)
                Text(stringResource(if (activity.active == false) R.string.wf_inactive else R.string.wf_active))
                Text(activity.minimumDuration?.let { stringResource(R.string.wf_minutes_target, it) } ?: stringResource(R.string.wf_target_unavailable))
                Text(stringResource(R.string.wf_starts_on, formatDate(activity.startDate)))
                Text(stringResource(R.string.wf_repeat_days), style = MaterialTheme.typography.titleMedium)
                val labels = workflowDays.filter { it.first in activity.scheduledDays.orEmpty() }
                    .map { stringResource(it.second) }
                Text(labels.joinToString(", ").ifBlank { stringResource(R.string.wf_no_repeat_days) })
                if (activity.startDate.isNullOrBlank() || activity.scheduledDays.isNullOrEmpty()) {
                    Text(stringResource(R.string.wf_incomplete_schedule_body), color = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.wf_created, formatDateTime(activity.createdAt)))
                Text(stringResource(R.string.wf_updated, formatDateTime(activity.updatedAt)))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::start, enabled = state.canStart) {
                        Text(stringResource(if (state.busy) R.string.wf_working else R.string.wf_start_session))
                    }
                    OutlinedButton(onClick = { onTrack(activity.id) }, enabled = !state.busy) {
                        Text(stringResource(R.string.wf_view_sessions))
                    }
                    OutlinedButton(onClick = { onEdit(activity.id) }, enabled = !state.busy) {
                        Text(stringResource(R.string.wf_edit_activity))
                    }
                    OutlinedButton(onClick = { onProgress(activity.id) }) { Text(stringResource(R.string.wf_progress)) }
                    OutlinedButton(onClick = { onReport(activity.id) }) { Text(stringResource(R.string.wf_report)) }
                }
                WorkflowError(state.sessionsError, viewModel::refresh)
                if (state.openSessions.isNotEmpty()) Text(
                    stringResource(R.string.wf_session_already_open),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.wf_sessions_count, state.sessions.size), style = MaterialTheme.typography.titleMedium)
                state.sessions.filter { it.endTime != null }.sortedByDescending { it.startTime }.take(5).forEach { session ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(formatDateTime(session.startTime))
                            Text(session.duration?.let { formatDuration(it) } ?: stringResource(R.string.wf_duration_unavailable))
                        }
                    }
                }
                TextButton(
                    onClick = { confirmDelete = true }, enabled = state.canDelete,
                ) { Text(stringResource(R.string.wf_delete_activity), color = MaterialTheme.colorScheme.error) }
                if (!state.sessionsKnown) Text(stringResource(R.string.wf_verify_sessions_before_actions))
                if (state.openSessions.isNotEmpty()) Text(stringResource(R.string.wf_stop_before_delete))
                Text(stringResource(R.string.wf_activity_delete_hint), style = MaterialTheme.typography.bodySmall)
            }
            SnackbarHost(snackbar)
        }
    }
    if (confirmDelete) DestructiveConfirmation(
        title = stringResource(R.string.wf_delete_activity),
        body = stringResource(R.string.wf_activity_delete_confirm),
        busy = state.busy, onDismiss = { confirmDelete = false },
        onConfirm = { confirmDelete = false; viewModel.delete() },
    )
}

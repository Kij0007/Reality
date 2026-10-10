package com.reality.android.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.reality.android.R
import com.reality.android.core.util.formatDateTime
import com.reality.android.core.util.formatDuration
import com.reality.android.core.util.liveWorkingSeconds
import com.reality.android.core.util.today
import com.reality.android.data.remote.dto.BreakDto
import com.reality.android.data.remote.dto.SessionDto
import com.reality.android.ui.activities.DestructiveConfirmation
import com.reality.android.ui.activities.WorkflowDateInput
import com.reality.android.ui.activities.WorkflowEmpty
import com.reality.android.ui.activities.WorkflowError
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    initialActivityId: Long? = null,
    onSession: (Long) -> Unit,
    viewModel: TrackingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val noticeText = state.notice?.let { stringResource(it) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var breakSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(initialActivityId) { viewModel.load(initialActivityId) }
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refresh()
            while (true) {
                delay(15_000)
                viewModel.refresh()
            }
        }
    }
    LaunchedEffect(noticeText) {
        noticeText?.let { snackbar.showSnackbar(it); viewModel.clearNotice() }
    }
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.wf_tracking), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.wf_tracking_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.wf_server_zone, state.zoneId), style = MaterialTheme.typography.bodySmall)
                    WorkflowError(state.error, viewModel::refresh)
                    WorkflowError(state.activitiesError, viewModel::refresh)
                    var activityMenu by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { activityMenu = true }, enabled = !state.loading && !state.busy) {
                        Text(
                            state.activityId?.let { id ->
                                state.activities.firstOrNull { it.id == id }?.name
                                    ?: stringResource(R.string.wf_activity_number, id)
                            } ?: stringResource(R.string.wf_all_activities),
                        )
                    }
                    DropdownMenu(expanded = activityMenu, onDismissRequest = { activityMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.wf_all_activities)) },
                            onClick = { activityMenu = false; viewModel.selectActivity(null) },
                            enabled = !state.loading && !state.busy,
                        )
                        state.activities.forEach { activity ->
                            DropdownMenuItem(
                                text = { Text(activity.name ?: stringResource(R.string.wf_activity_number, activity.id)) },
                                onClick = { activityMenu = false; viewModel.selectActivity(activity.id) },
                                enabled = !state.loading && !state.busy,
                            )
                        }
                    }
                    Button(onClick = viewModel::start, enabled = state.canStart) {
                        Text(stringResource(if (state.busy) R.string.wf_working else R.string.wf_start_session))
                    }
                    if (state.activityId == null) Text(stringResource(R.string.wf_choose_activity_to_start))
                    if (state.activityId != null && state.open.any { it.activityId == state.activityId })
                        Text(stringResource(R.string.wf_session_already_open), color = MaterialTheme.colorScheme.primary)
                    WorkflowDateInput(
                        label = stringResource(R.string.wf_history_date),
                        value = state.date ?: today(state.zoneId).toString(),
                        enabled = state.activityId != null && !state.loading && !state.busy,
                        onValueChange = viewModel::selectDate,
                    )
                    if (state.date != null) TextButton(onClick = { viewModel.selectDate(null) }, enabled = !state.loading && !state.busy) {
                        Text(stringResource(R.string.wf_clear_date))
                    } else Text(stringResource(
                        if (state.activityId == null) R.string.wf_date_requires_activity else R.string.wf_all_dates_hint
                    ), style = MaterialTheme.typography.bodySmall)
                    state.dayTotal?.let { Text(stringResource(R.string.wf_day_work, formatDuration(it))) }
                    if (state.date != null) Text(stringResource(R.string.wf_day_history_hint), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SessionStatus.entries.forEach { status ->
                            FilterChip(
                                selected = state.status == status, onClick = { viewModel.selectStatus(status) },
                                label = { Text(stringResource(statusLabel(status))) },
                            )
                        }
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (state.open.isNotEmpty()) {
                item { Text(stringResource(R.string.wf_open_sessions), style = MaterialTheme.typography.titleLarge) }
                items(state.open, key = { "open-" + it.id }) { session ->
                    SessionCard(
                        session = session,
                        activityName = state.activities.firstOrNull { it.id == session.activityId }?.name,
                        breaks = state.breaks[session.id], zoneId = state.zoneId,
                        enabled = !state.loading && !state.busy && state.sessionsKnown,
                        breakError = state.breakErrors[session.id],
                        onDetail = { onSession(session.id) },
                        onBreak = { breakSessionId = session.id },
                        onResume = { viewModel.resume(session) }, onStop = { viewModel.stop(session) },
                    )
                }
            }
            item { Text(stringResource(R.string.wf_session_history), style = MaterialTheme.typography.titleLarge) }
            if (state.visible.isEmpty() && !state.loading) {
                item { WorkflowEmpty(
                    stringResource(R.string.wf_no_sessions),
                    stringResource(R.string.wf_no_sessions_body),
                ) }
            }
            items(state.visible, key = { "history-" + it.id }) { session ->
                SessionCard(
                    session = session,
                    activityName = state.activities.firstOrNull { it.id == session.activityId }?.name,
                    breaks = state.breaks[session.id], zoneId = state.zoneId,
                    enabled = !state.loading && !state.busy && state.sessionsKnown,
                    breakError = state.breakErrors[session.id],
                    onDetail = { onSession(session.id) },
                    onBreak = { breakSessionId = session.id },
                    onResume = { viewModel.resume(session) }, onStop = { viewModel.stop(session) },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    breakSessionId?.let { id ->
        BreakNoteDialog(
            busy = state.busy || state.loading, onDismiss = { breakSessionId = null },
            onConfirm = { note ->
                breakSessionId = null
                state.allSessions.firstOrNull { it.id == id }?.let { viewModel.startBreak(it, note) }
            },
        )
    }
}

@Composable
private fun SessionCard(
    session: SessionDto,
    activityName: String?,
    breaks: List<BreakDto>?,
    zoneId: String,
    enabled: Boolean,
    breakError: String?,
    onDetail: () -> Unit,
    onBreak: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(activityName ?: stringResource(R.string.wf_activity_number, session.activityId), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(statusLabel(sessionStatus(session, breaks))), color = MaterialTheme.colorScheme.primary)
            Text(formatDateTime(session.startTime), style = MaterialTheme.typography.bodyMedium)
            SessionWorkingTime(session, breaks, zoneId)
            if (breakError != null && session.endTime == null) Text(breakError, color = MaterialTheme.colorScheme.error)
            SessionActionButtons(session, breaks, enabled, onBreak, onResume, onStop)
            TextButton(onClick = onDetail) { Text(stringResource(R.string.wf_session_details)) }
        }
    }
}

@Composable
internal fun SessionActionButtons(
    session: SessionDto,
    breaks: List<BreakDto>?,
    enabled: Boolean,
    onBreak: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    if (session.endTime != null) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (sessionStatus(session, breaks)) {
            SessionStatus.RUNNING -> OutlinedButton(onClick = onBreak, enabled = enabled) {
                Text(stringResource(R.string.wf_take_break))
            }
            SessionStatus.BREAK -> Button(onClick = onResume, enabled = enabled) {
                Text(stringResource(R.string.wf_resume))
            }
            SessionStatus.UNKNOWN -> Text(
                stringResource(R.string.wf_break_state_unknown),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Unit
        }
        OutlinedButton(onClick = onStop, enabled = enabled) { Text(stringResource(R.string.wf_stop_session)) }
    }
}

@Composable
private fun SessionWorkingTime(session: SessionDto, breaks: List<BreakDto>?, zoneId: String) {
    val now by produceState(initialValue = Instant.now(), session.id, session.endTime) {
        while (session.endTime == null) { value = Instant.now(); delay(1_000) }
    }
    val duration = when {
        session.endTime != null -> session.duration
        breaks != null -> liveWorkingSeconds(session, breaks, zoneId, now)
        else -> null
    }
    Text(
        duration?.let { formatDuration(it) } ?: stringResource(R.string.wf_duration_unavailable),
        style = MaterialTheme.typography.headlineSmall,
    )
    if (session.endTime == null) Text(
        stringResource(R.string.wf_live_timer_hint), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BreakNoteDialog(busy: Boolean, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.wf_take_break)) },
        text = {
            OutlinedTextField(
                value = note, onValueChange = { note = it }, enabled = !busy,
                label = { Text(stringResource(R.string.wf_break_note)) },
                supportingText = { Text(stringResource(R.string.wf_note_length, note.length)) },
                isError = note.length > 255, modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note.trim().ifEmpty { null }) }, enabled = !busy && note.length <= 255) {
                Text(stringResource(R.string.wf_start_break))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.wf_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    onDeleted: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val noticeText = state.notice?.let { stringResource(it) }
    val owner = LocalLifecycleOwner.current
    var breakDialog by rememberSaveable(sessionId) { mutableStateOf(false) }
    var deleteDialog by rememberSaveable(sessionId) { mutableStateOf(false) }
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }
    LaunchedEffect(owner, viewModel) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refresh()
            while (true) { delay(15_000); viewModel.refresh() }
        }
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    LaunchedEffect(noticeText) {
        noticeText?.let { snackbar.showSnackbar(it); viewModel.clearNotice() }
    }
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.wf_session_number, sessionId), style = MaterialTheme.typography.headlineMedium)
                    WorkflowError(state.error, viewModel::refresh)
                    state.session?.let { session ->
                        Text(state.activityName ?: stringResource(R.string.wf_activity_number, session.activityId), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(statusLabel(sessionStatus(session, state.breaks))))
                        Text(stringResource(R.string.wf_started_at, formatDateTime(session.startTime)))
                        session.endTime?.let { Text(stringResource(R.string.wf_stopped_at, formatDateTime(it))) }
                        Text(stringResource(R.string.wf_server_zone, state.zoneId), style = MaterialTheme.typography.bodySmall)
                        SessionWorkingTime(session, state.breaks, state.zoneId)
                        SessionActionButtons(
                            session, state.breaks, !state.busy && !state.loading && state.sessionKnown,
                            onBreak = { breakDialog = true }, onResume = viewModel::resume, onStop = viewModel::stop,
                        )
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(onClick = { deleteDialog = true }, enabled = !state.busy && !state.loading && state.sessionKnown) {
                            Text(stringResource(R.string.wf_delete_session), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Text(stringResource(R.string.wf_break_history), style = MaterialTheme.typography.titleLarge)
                    WorkflowError(state.breakError, viewModel::refresh)
                    if (state.breaks?.isEmpty() == true) Text(stringResource(R.string.wf_no_breaks))
                }
            }
            items(state.breaks.orEmpty().sortedBy { it.startTime }, key = { it.id }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.wf_started_at, formatDateTime(entry.startTime)))
                        Text(entry.endTime?.let { stringResource(R.string.wf_resumed_at, formatDateTime(it)) }
                            ?: stringResource(R.string.wf_break_in_progress))
                        entry.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        BreakDuration(entry, state.zoneId)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (breakDialog) BreakNoteDialog(
        busy = state.busy || state.loading, onDismiss = { breakDialog = false },
        onConfirm = { breakDialog = false; viewModel.startBreak(it) },
    )
    if (deleteDialog) DestructiveConfirmation(
        title = stringResource(R.string.wf_delete_session),
        body = stringResource(R.string.wf_session_delete_confirm),
        busy = state.busy || state.loading, onDismiss = { deleteDialog = false },
        onConfirm = { deleteDialog = false; viewModel.delete() },
    )
}

@Composable
private fun BreakDuration(entry: BreakDto, zoneId: String) {
    val now by produceState(initialValue = Instant.now(), entry.id, entry.endTime) {
        while (entry.endTime == null) { value = Instant.now(); delay(1_000) }
    }
    val seconds = if (entry.endTime != null) entry.duration else runCatching {
        Duration.between(LocalDateTime.parse(entry.startTime).atZone(ZoneId.of(zoneId)).toInstant(), now)
            .seconds.coerceAtLeast(0)
    }.getOrNull()
    Text(seconds?.let { formatDuration(it) } ?: stringResource(R.string.wf_duration_unavailable))
}

internal fun statusLabel(status: SessionStatus): Int = when (status) {
    SessionStatus.ALL -> R.string.wf_status_all
    SessionStatus.RUNNING -> R.string.wf_status_running
    SessionStatus.BREAK -> R.string.wf_status_break
    SessionStatus.STOPPED -> R.string.wf_status_stopped
    SessionStatus.UNKNOWN -> R.string.wf_status_unknown
}

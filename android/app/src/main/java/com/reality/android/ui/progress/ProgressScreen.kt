package com.reality.android.ui.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reality.android.R
import com.reality.android.core.util.formatDate
import com.reality.android.core.util.formatDateTime
import com.reality.android.core.util.formatDuration

@Composable
fun ProgressScreen(
    initialActivityId: Long? = null,
    onSession: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialActivityId) { viewModel.initialize(initialActivityId) }
    RefreshOnResume(viewModel::refresh)
    InsightsList(state.loading, viewModel::refresh) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.insights_progress_title), style = MaterialTheme.typography.headlineMedium)
                ActivitySelector(state.activities, state.selectedId, select = viewModel::selectActivity)
                state.date?.let { DateSelector(it, viewModel::selectDate) }
                Text(stringResource(R.string.insights_server_zone, state.serverZoneId), style = MaterialTheme.typography.bodySmall)
            }
        }
        state.error?.let { message -> item { InsightsError(message, state.loading, viewModel::refresh) } }
        if (state.error != null && (state.progress.data != null || state.sessions.data != null || state.streak.data != null)) item {
            Text(stringResource(R.string.insights_stale_note), color = MaterialTheme.colorScheme.error)
        }
        if (state.selectionMissing) item { Text(stringResource(R.string.insights_activity_unavailable), color = MaterialTheme.colorScheme.error) }
        if (state.activitiesLoaded && state.activities.isEmpty()) item { Text(stringResource(R.string.insights_no_activities_instruction)) }
        state.selected?.let { activity ->
            if (!hasCompleteSchedule(activity)) item { ScheduleRepair(activity, onEdit) }
            state.progress.error?.let { message -> item { InsightsError(message, state.loading, viewModel::refresh) } }
            state.progress.data?.let { day ->
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(activityName(activity), style = MaterialTheme.typography.titleLarge)
                            Text(formatDuration(day.totalDuration), style = MaterialTheme.typography.headlineLarge)
                            Text(pluralStringResource(R.plurals.insights_target_minutes, day.minimumDuration, day.minimumDuration))
                            LinearProgressIndicator(
                                progress = { if (day.minimumDuration > 0) (day.totalDuration.toFloat() / (day.minimumDuration.toLong() * 60)).coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(stringResource(if (day.completed) R.string.insights_target_completed else R.string.insights_target_pending))
                            if (state.progress.error != null) Text(stringResource(R.string.insights_stale_note), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.insights_current_streak), style = MaterialTheme.typography.titleLarge) }
            state.streak.error?.let { message -> item { InsightsError(message, state.loading, viewModel::refresh) } }
            state.streak.data?.let { streak ->
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(pluralStringResource(R.plurals.insights_streak_days, streak.currentStreak, streak.currentStreak), style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(R.string.insights_evaluated_through, formatDate(streak.lastEvaluatedDate)))
                            Text(stringResource(R.string.insights_streak_note), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.insights_sessions_for_date), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.insights_session_overlap_note), style = MaterialTheme.typography.bodySmall)
                }
            }
            state.sessions.error?.let { message -> item { InsightsError(message, state.loading, viewModel::refresh) } }
            state.sessions.data?.let { day ->
                item { Text(stringResource(R.string.insights_day_total, formatDuration(day.totalDuration))) }
                if (day.sessions.isEmpty()) item { Text(stringResource(R.string.insights_no_finished_sessions)) }
                items(day.sessions.sortedByDescending { it.startTime }, key = { it.id }) { session ->
                    OutlinedCard(onClick = { onSession(session.id) }) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.insights_session_number, session.id), style = MaterialTheme.typography.titleMedium)
                            Text(formatDateTime(session.startTime))
                            Text(stringResource(R.string.insights_ended_at, formatDateTime(session.endTime)))
                            Text(stringResource(R.string.insights_session_duration, session.duration?.let(::formatDuration) ?: stringResource(R.string.insights_unavailable)))
                            Text(stringResource(R.string.insights_view_session), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        item { Text(stringResource(R.string.insights_daily_note), style = MaterialTheme.typography.bodySmall) }
    }
}

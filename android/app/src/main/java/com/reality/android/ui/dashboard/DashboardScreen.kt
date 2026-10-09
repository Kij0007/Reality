package com.reality.android.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reality.android.R
import com.reality.android.core.util.formatDate
import com.reality.android.core.util.formatDuration
import com.reality.android.core.util.isScheduled
import com.reality.android.ui.progress.*

@Composable
fun DashboardScreen(
    onCreate: () -> Unit,
    onActivity: (Long) -> Unit,
    onTrack: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onSchedule: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    val scheduled = state.date?.let { date -> state.rows.filter { isScheduled(it.activity, date) } }.orEmpty()
    val work = state.activities?.let { dashboardRecordedSeconds(state.rows) }
    val allScheduledLoaded = scheduled.all { it.progress.isCurrent }
    InsightsList(state.loading, viewModel::refresh) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.insights_dashboard_title), style = MaterialTheme.typography.headlineMedium)
                state.date?.let { Text(formatDate(it.toString())) }
                Text(stringResource(R.string.insights_server_zone, state.serverZoneId), style = MaterialTheme.typography.bodySmall)
                Button(onClick = onCreate) { Text(stringResource(R.string.insights_create_activity)) }
            }
        }
        state.activityError?.let { message -> item { InsightsError(message, state.loading, viewModel::refresh) } }
        state.sessionError?.let { message -> item { InsightsError(message, state.loading, viewModel::refresh) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    stringResource(R.string.insights_active_activities),
                    state.activities?.size?.toString() ?: stringResource(R.string.insights_unavailable),
                    Modifier.weight(1f)
                )
                StatTile(
                    stringResource(R.string.insights_open_sessions),
                    state.sessions?.count { it.endTime == null }?.toString() ?: stringResource(R.string.insights_unavailable),
                    Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.insights_recorded_today), work?.let(::formatDuration) ?: stringResource(R.string.insights_unavailable), Modifier.weight(1f))
                StatTile(
                    stringResource(R.string.insights_targets_today),
                    if (state.activities == null || !allScheduledLoaded) stringResource(R.string.insights_unavailable)
                    else stringResource(R.string.insights_fraction, scheduled.count { it.progress.data?.completed == true }, scheduled.size),
                    Modifier.weight(1f)
                )
            }
        }
        if (state.activityError != null || state.sessionError != null || state.rows.any { it.progress.error != null || it.streak.error != null }) {
            item { Text(stringResource(R.string.insights_stale_note), color = MaterialTheme.colorScheme.error) }
        }
        if (state.activities?.isEmpty() == true) {
            item {
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.insights_no_activities))
                        Button(onClick = onCreate) { Text(stringResource(R.string.insights_create_activity)) }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.insights_today_commitments), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onSchedule) { Text(stringResource(R.string.insights_schedule)) }
            }
        }
        if (state.activities != null && scheduled.isEmpty()) {
            item { Text(stringResource(R.string.insights_no_schedule_today)) }
        }
        items(state.rows, key = { it.activity.id }) { row ->
            val activity = row.activity
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!hasCompleteSchedule(activity)) ScheduleRepair(activity, onEdit)
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(activityName(activity), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.date?.let { isScheduled(activity, it) } == true) stringResource(R.string.insights_scheduled_today)
                            else stringResource(R.string.insights_optional_today),
                            style = MaterialTheme.typography.labelMedium
                        )
                        row.progress.data?.let { day ->
                            Text(stringResource(R.string.insights_duration_target, formatDuration(day.totalDuration), day.minimumDuration))
                            LinearProgressIndicator(
                                progress = { if (day.minimumDuration > 0) (day.totalDuration.toFloat() / (day.minimumDuration.toLong() * 60)).coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(stringResource(if (day.completed) R.string.insights_target_completed else R.string.insights_target_pending))
                        }
                        row.progress.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        row.streak.data?.let { Text(stringResource(R.string.insights_streak_days, it.currentStreak)) }
                        row.streak.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onTrack(activity.id) }) { Text(stringResource(R.string.insights_track)) }
                            TextButton(onClick = { onActivity(activity.id) }) { Text(stringResource(R.string.insights_details)) }
                        }
                    }
                }
            }
        }
        item { Text(stringResource(R.string.insights_daily_note), style = MaterialTheme.typography.bodySmall) }
    }
}

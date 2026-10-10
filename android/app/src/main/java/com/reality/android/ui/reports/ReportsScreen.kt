package com.reality.android.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reality.android.R
import com.reality.android.core.util.formatDuration
import com.reality.android.ui.progress.*

@Composable
fun ReportsScreen(
    initialActivityId: Long? = null,
    onEdit: (Long) -> Unit,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val locale = LocalConfiguration.current.locales[0]
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialActivityId) { viewModel.initialize(initialActivityId) }
    RefreshOnResume(viewModel::refresh)
    InsightsList(state.loading, viewModel::refresh) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.insights_reports), style = MaterialTheme.typography.headlineMedium)
                ActivitySelector(state.activities, state.selectedId, select = viewModel::selectActivity)
                state.month?.let { MonthSelector(it, viewModel::selectMonth) }
                Text(stringResource(R.string.insights_server_zone, state.serverZoneId), style = MaterialTheme.typography.bodySmall)
            }
        }
        state.error?.let { message -> item { InsightsError(message, state.loading, viewModel::refresh) } }
        if (state.error != null && state.report.data != null) item {
            Text(stringResource(R.string.insights_stale_note), color = MaterialTheme.colorScheme.error)
        }
        if (state.selectionMissing) item { Text(stringResource(R.string.insights_activity_unavailable), color = MaterialTheme.colorScheme.error) }
        if (state.activitiesLoaded && state.activities.isEmpty()) item { Text(stringResource(R.string.insights_no_activities_instruction)) }
        state.selected?.let { activity -> if (!hasCompleteSchedule(activity)) item { ScheduleRepair(activity, onEdit) } }
        state.report.error?.let { message -> item { InsightsError(message, state.loading, viewModel::refresh) } }
        state.report.data?.let { report ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(report.activityName ?: stringResource(R.string.insights_activity_number, report.activityId), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.insights_report_identity, report.activityId, report.year, report.month), style = MaterialTheme.typography.bodySmall)
                    if (state.report.error != null) Text(stringResource(R.string.insights_stale_note), color = MaterialTheme.colorScheme.error)
                }
            }
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.insights_completion), style = MaterialTheme.typography.titleMedium)
                        Text(String.format(locale, "%.2f%%", report.completionPercentage), style = MaterialTheme.typography.headlineLarge)
                        LinearProgressIndicator(
                            progress = { (report.completionPercentage / 100.0).toFloat().let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f } },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(stringResource(R.string.insights_report_accountability_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.insights_total_duration), formatDuration(report.totalDuration), Modifier.weight(1f))
                    StatTile(stringResource(R.string.insights_finished_sessions), report.totalSessions.toString(), Modifier.weight(1f))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.insights_evaluated_scheduled_days), report.scheduledDays.toString())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(stringResource(R.string.insights_completed_days), report.completedDays.toString(), Modifier.weight(1f))
                        StatTile(stringResource(R.string.insights_missed_days), report.missedDays.toString(), Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.insights_current_streak), report.currentStreak.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.insights_month_longest_streak), report.longestStreak.toString(), Modifier.weight(1f))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.insights_report_streak_note), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.insights_report_duration_note), style = MaterialTheme.typography.bodySmall)
                    if (report.totalSessions == 0) Text(stringResource(R.string.insights_report_no_sessions))
                }
            }
        }
    }
}

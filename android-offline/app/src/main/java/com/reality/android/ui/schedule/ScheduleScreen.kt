package com.reality.android.ui.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reality.android.R
import com.reality.android.core.util.formatDate
import com.reality.android.core.util.isScheduled
import com.reality.android.ui.progress.*
import java.time.DayOfWeek
import java.time.format.TextStyle

@Composable
fun ScheduleScreen(
    onEdit: (Long) -> Unit,
    onActivity: (Long) -> Unit,
    onTrack: (Long) -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val locale = LocalConfiguration.current.locales[0]
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)
    InsightsList(state.loading, viewModel::refresh) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.insights_schedule), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.insights_schedule_note))
                Text(stringResource(R.string.insights_server_zone, state.serverZoneId), style = MaterialTheme.typography.bodySmall)
                state.date?.let { DateSelector(it, viewModel::selectDate) }
            }
        }
        state.error?.let { message -> item { InsightsError(message, state.loading, viewModel::refresh) } }
        if (state.error != null && state.activities != null) item {
            Text(stringResource(R.string.insights_stale_note), color = MaterialTheme.colorScheme.error)
        }
        if (state.activities?.isEmpty() == true) item { Text(stringResource(R.string.insights_no_activities_instruction)) }
        items(state.activities.orEmpty().filterNot(::hasCompleteSchedule), key = { "repair-" + it.id }) {
            ScheduleRepair(it, onEdit)
        }
        state.date?.let { start ->
            items((0L..6L).map { start.plusDays(it) }, key = { it.toString() }) { date ->
                val scheduled = state.activities.orEmpty().filter { isScheduled(it, date) }
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(formatDate(date.toString()), style = MaterialTheme.typography.titleMedium)
                        if (state.activities == null) {
                            Text(stringResource(R.string.insights_schedule_unavailable))
                        } else if (scheduled.isEmpty()) {
                            Text(stringResource(R.string.insights_no_schedule_date))
                        }
                        scheduled.forEach { activity ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(activityName(activity), style = MaterialTheme.typography.titleSmall)
                                Text(activity.minimumDuration?.let {
                                    pluralStringResource(R.plurals.insights_target_minutes, it, it)
                                } ?: stringResource(R.string.insights_target_unavailable))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onActivity(activity.id) }) { Text(stringResource(R.string.insights_details)) }
                                    TextButton(onClick = { onTrack(activity.id) }) { Text(stringResource(R.string.insights_track)) }
                                    TextButton(onClick = { onEdit(activity.id) }) { Text(stringResource(R.string.insights_edit)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!state.activities.isNullOrEmpty()) item { Text(stringResource(R.string.insights_repeat_schedules), style = MaterialTheme.typography.titleLarge) }
        items(state.activities.orEmpty(), key = { "rule-" + it.id }) { activity ->
            val days = activity.scheduledDays.orEmpty().mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
                .sortedBy { it.value }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(activityName(activity), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.insights_starts_on, formatDate(activity.startDate)))
                    Text(days.ifBlank { stringResource(R.string.insights_no_repeat_days) })
                    TextButton(onClick = { onEdit(activity.id) }) { Text(stringResource(R.string.insights_edit_schedule)) }
                }
            }
        }
    }
}

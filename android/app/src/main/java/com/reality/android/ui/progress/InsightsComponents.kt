package com.reality.android.ui.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reality.android.R
import com.reality.android.core.network.ApiResult
import com.reality.android.data.remote.dto.ActivityDto
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class RemoteValue<T>(val data: T? = null, val error: String? = null) {
    val isCurrent: Boolean get() = data != null && error == null
}

fun <T> ApiResult<T>.remote(previous: T? = null): RemoteValue<T> = when (this) {
    is ApiResult.Success -> RemoteValue(data)
    is ApiResult.Failure -> RemoteValue(previous, error.message)
}

fun hasCompleteSchedule(activity: ActivityDto): Boolean =
    activity.startDate?.let { runCatching { LocalDate.parse(it) }.isSuccess } == true &&
        activity.scheduledDays?.isNotEmpty() == true

@Composable
internal fun activityName(activity: ActivityDto): String =
    activity.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.insights_activity_number, activity.id)

@Composable
internal fun RefreshOnResume(action: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(action)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latest()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InsightsList(
    loading: Boolean,
    refresh: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    PullToRefreshBox(isRefreshing = loading, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
internal fun InsightsError(message: String, loading: Boolean = false, retry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = retry, enabled = !loading) { Text(stringResource(R.string.insights_retry)) }
        }
    }
}

@Composable
internal fun ScheduleRepair(activity: ActivityDto, onEdit: (Long) -> Unit) {
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.insights_complete_schedule), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.insights_schedule_repair_note, activityName(activity)))
            TextButton(onClick = { onEdit(activity.id) }) { Text(stringResource(R.string.insights_edit_schedule)) }
        }
    }
}

@Composable
internal fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
internal fun ActivitySelector(
    activities: List<ActivityDto>,
    selectedId: Long?,
    enabled: Boolean = true,
    select: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = activities.firstOrNull { it.id == selectedId }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && activities.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(selected?.let { activityName(it) } ?: stringResource(R.string.insights_choose_activity))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            activities.forEach { activity ->
                DropdownMenuItem(
                    text = { Text(activityName(activity)) },
                    onClick = { expanded = false; select(activity.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateSelector(date: LocalDate, onDate: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
    }
    if (open) {
        // Material date pickers represent calendar dates at UTC midnight, independently of the server zone.
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text(stringResource(R.string.insights_apply)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.insights_cancel)) } }
        ) { DatePicker(state = picker) }
    }
}

@Composable
internal fun MonthSelector(month: YearMonth, onMonth: (YearMonth) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { onMonth(month.minusMonths(1)) }) { Text(stringResource(R.string.insights_previous)) }
        TextButton(onClick = { open = true }, modifier = Modifier.weight(1f)) {
            Text(month.format(DateTimeFormatter.ofPattern("MMM uuuu", locale)))
        }
        TextButton(onClick = { onMonth(month.plusMonths(1)) }) { Text(stringResource(R.string.insights_next)) }
    }
    if (open) {
        var year by remember { mutableStateOf(month.year.toString()) }
        var selectedMonth by remember { mutableIntStateOf(month.monthValue) }
        var expanded by remember { mutableStateOf(false) }
        val validYear = year.toIntOrNull()?.takeIf { it in 1000..9999 }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.insights_choose_month)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) year = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.insights_year)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = validYear == null,
                        supportingText = { if (validYear == null) Text(stringResource(R.string.insights_year_error)) }
                    )
                    Box {
                        OutlinedButton(onClick = { expanded = true }) {
                            Text(Month.of(selectedMonth).getDisplayName(java.time.format.TextStyle.FULL, locale))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            Month.values().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.getDisplayName(java.time.format.TextStyle.FULL, locale)) },
                                    onClick = { selectedMonth = option.value; expanded = false }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = validYear != null,
                    onClick = { validYear?.let { onMonth(YearMonth.of(it, selectedMonth)) }; open = false }
                ) { Text(stringResource(R.string.insights_apply)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.insights_cancel)) } }
        )
    }
}

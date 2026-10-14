package com.reality.android.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reality.android.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
internal fun WorkflowError(message: String?, retry: (() -> Unit)? = null) {
    if (message == null) return
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        if (retry != null) TextButton(onClick = retry) { Text(stringResource(R.string.wf_retry)) }
    }
}

@Composable
internal fun WorkflowEmpty(title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
internal fun DestructiveConfirmation(
    title: String,
    body: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = { Text(body) },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.wf_cancel)) }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(
                    stringResource(if (busy) R.string.wf_deleting else R.string.wf_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkflowDateInput(
    label: String,
    value: String,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    var pickerVisible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = {}, readOnly = true, enabled = enabled,
        label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { pickerVisible = true }, enabled = enabled) {
                Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.wf_choose_date))
            }
        },
    )
    if (pickerVisible) {
        val initialMillis = remember(value) {
            runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { pickerVisible = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedDateMillis != null,
                    onClick = {
                        picker.selectedDateMillis?.let {
                            onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                        }
                        pickerVisible = false
                    },
                ) { Text(stringResource(R.string.wf_select)) }
            },
            dismissButton = {
                TextButton(onClick = { pickerVisible = false }) { Text(stringResource(R.string.wf_cancel)) }
            },
        ) { DatePicker(state = picker) }
    }
}

@Composable
internal fun WorkflowForegroundRefresh(onRefresh: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(onRefresh)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

internal val workflowDays = listOf(
    "MONDAY" to R.string.wf_monday, "TUESDAY" to R.string.wf_tuesday,
    "WEDNESDAY" to R.string.wf_wednesday, "THURSDAY" to R.string.wf_thursday,
    "FRIDAY" to R.string.wf_friday, "SATURDAY" to R.string.wf_saturday,
    "SUNDAY" to R.string.wf_sunday,
)

internal fun categoryLabel(category: com.reality.android.data.remote.dto.ActivityCategory?): Int = when (category) {
    com.reality.android.data.remote.dto.ActivityCategory.STUDY -> R.string.wf_category_study
    com.reality.android.data.remote.dto.ActivityCategory.FITNESS -> R.string.wf_category_fitness
    com.reality.android.data.remote.dto.ActivityCategory.WORK -> R.string.wf_category_work
    com.reality.android.data.remote.dto.ActivityCategory.LEARNING -> R.string.wf_category_learning
    com.reality.android.data.remote.dto.ActivityCategory.HEALTH -> R.string.wf_category_health
    com.reality.android.data.remote.dto.ActivityCategory.PERSONAL -> R.string.wf_category_personal
    com.reality.android.data.remote.dto.ActivityCategory.OTHER -> R.string.wf_category_other
    null -> R.string.wf_unspecified
}

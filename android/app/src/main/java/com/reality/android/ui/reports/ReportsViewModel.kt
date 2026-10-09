package com.reality.android.ui.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality.android.core.network.ApiResult
import com.reality.android.core.util.today
import com.reality.android.data.remote.dto.*
import com.reality.android.data.repository.*
import com.reality.android.ui.progress.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth

data class ReportsUiState(
    val loading: Boolean = true,
    val activities: List<ActivityDto> = emptyList(),
    val selectedId: Long? = null,
    val month: YearMonth? = null,
    val serverZoneId: String = "",
    val report: RemoteValue<MonthlyReportDto> = RemoteValue(),
    val error: String? = null,
    val selectionMissing: Boolean = false,
    val activitiesLoaded: Boolean = false
) {
    val selected: ActivityDto? get() = activities.firstOrNull { it.id == selectedId }
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val activities: ActivityRepository,
    private val reports: ReportRepository,
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(ReportsUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            settings.settings.map { it.backendUrl to it.serverZoneId }.distinctUntilChanged().collect { (_, zone) ->
                val month = savedState.get<String>("reportMonth")?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                    ?: YearMonth.from(today(zone))
                _state.update { it.copy(month = month, serverZoneId = zone) }
                restart()
            }
        }
    }

    fun initialize(activityId: Long?) {
        if (activityId != null && savedState.get<Long>("reportActivityId") != activityId) selectActivity(activityId)
    }

    fun selectActivity(id: Long) {
        savedState["reportActivityId"] = id
        _state.update { it.copy(selectedId = id, report = RemoteValue()) }
        restart()
    }

    fun selectMonth(month: YearMonth) {
        savedState["reportMonth"] = month.toString()
        _state.update { it.copy(month = month, report = RemoteValue()) }
        restart()
    }

    fun refresh() { if (loadJob?.isActive != true) load() }
    private fun restart() { loadJob?.cancel(); if (_state.value.month != null) load() }

    private fun load() {
        val month = _state.value.month ?: return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val result = activities.list()) {
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error.message) }
                is ApiResult.Success -> {
                    val requested = savedState.get<Long>("reportActivityId")
                    val id = requested ?: result.data.firstOrNull()?.id
                    val selected = result.data.firstOrNull { it.id == id }
                    val previous = _state.value.report.data
                    _state.update {
                        it.copy(
                            activities = result.data,
                            selectedId = selected?.id,
                            activitiesLoaded = true,
                            error = null,
                            selectionMissing = requested != null && selected == null
                        )
                    }
                    if (selected == null || !hasCompleteSchedule(selected)) {
                        _state.update { it.copy(loading = false, report = RemoteValue()) }
                        return@launch
                    }
                    savedState["reportActivityId"] = selected.id
                    val report = reports.month(selected.id, month.toString()).remote(previous)
                    _state.update { it.copy(loading = false, report = report) }
                }
            }
        }
    }
}

package com.reality.android.ui.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality.android.core.network.ApiResult
import com.reality.android.core.util.today
import com.reality.android.data.remote.dto.ActivityDto
import com.reality.android.data.repository.ActivityRepository
import com.reality.android.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ScheduleUiState(
    val loading: Boolean = true,
    val date: LocalDate? = null,
    val activities: List<ActivityDto>? = null,
    val serverZoneId: String = "",
    val error: String? = null
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ActivityRepository,
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(ScheduleUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var backendUrl: String? = null

    init {
        viewModelScope.launch {
            settings.settings.map { it.backendUrl to it.serverZoneId }.distinctUntilChanged().collect { (url, zone) ->
                loadJob?.cancel()
                if (backendUrl != null && backendUrl != url) _state.value = ScheduleUiState()
                backendUrl = url
                val selected = savedState.get<String>("scheduleDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: today(zone)
                _state.update { it.copy(date = selected, serverZoneId = zone) }
                load()
            }
        }
    }

    fun selectDate(date: LocalDate) {
        savedState["scheduleDate"] = date.toString()
        _state.update { it.copy(date = date) }
    }

    fun refresh() { if (loadJob?.isActive != true && _state.value.date != null) load() }

    private fun load() {
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val result = repository.list()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, activities = result.data, error = null) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error.message) }
            }
        }
    }
}

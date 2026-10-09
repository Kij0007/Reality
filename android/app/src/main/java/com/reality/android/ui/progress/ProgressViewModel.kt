package com.reality.android.ui.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality.android.core.network.ApiResult
import com.reality.android.core.util.today
import com.reality.android.data.remote.dto.*
import com.reality.android.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

data class ProgressUiState(
    val loading: Boolean = true,
    val activities: List<ActivityDto> = emptyList(),
    val selectedId: Long? = null,
    val date: LocalDate? = null,
    val serverZoneId: String = "",
    val progress: RemoteValue<DailyProgressDto> = RemoteValue(),
    val sessions: RemoteValue<SessionDayDto> = RemoteValue(),
    val streak: RemoteValue<StreakDto> = RemoteValue(),
    val error: String? = null,
    val selectionMissing: Boolean = false,
    val activitiesLoaded: Boolean = false
) {
    val selected: ActivityDto? get() = activities.firstOrNull { it.id == selectedId }
}

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val sessionRepository: SessionRepository,
    private val progressRepository: ProgressRepository,
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(ProgressUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var backendUrl: String? = null

    init {
        viewModelScope.launch {
            settings.settings.map { it.backendUrl to it.serverZoneId }.distinctUntilChanged().collect { (url, zone) ->
                loadJob?.cancel()
                if (backendUrl != null && backendUrl != url) _state.value = ProgressUiState()
                backendUrl = url
                val date = savedState.get<String>("progressDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today(zone)
                if (_state.value.date != date) _state.update {
                    it.copy(progress = RemoteValue(), sessions = RemoteValue())
                }
                _state.update { it.copy(date = date, serverZoneId = zone) }
                restart()
            }
        }
    }

    fun initialize(activityId: Long?) {
        if (activityId != null && savedState.get<Long>("progressActivityId") != activityId) selectActivity(activityId)
    }

    fun selectActivity(id: Long) {
        savedState["progressActivityId"] = id
        _state.update { it.copy(selectedId = id, progress = RemoteValue(), sessions = RemoteValue(), streak = RemoteValue()) }
        restart()
    }

    fun selectDate(date: LocalDate) {
        savedState["progressDate"] = date.toString()
        _state.update { it.copy(date = date, progress = RemoteValue(), sessions = RemoteValue()) }
        restart()
    }

    fun refresh() { if (loadJob?.isActive != true) load() }
    private fun restart() { loadJob?.cancel(); if (_state.value.date != null) load() }

    private fun load() {
        val date = _state.value.date ?: return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val activities = activityRepository.list()) {
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = activities.error.message) }
                is ApiResult.Success -> {
                    val requested = savedState.get<Long>("progressActivityId")
                    val id = requested ?: activities.data.firstOrNull()?.id
                    val selected = activities.data.firstOrNull { it.id == id }
                    val previous = _state.value
                    _state.update {
                        it.copy(
                            activities = activities.data,
                            selectedId = selected?.id,
                            activitiesLoaded = true,
                            error = null,
                            selectionMissing = requested != null && selected == null
                        )
                    }
                    if (selected == null) {
                        _state.update { it.copy(loading = false, progress = RemoteValue(), sessions = RemoteValue(), streak = RemoteValue()) }
                        return@launch
                    }
                    savedState["progressActivityId"] = selected.id
                    coroutineScope {
                        val daily = async { progressRepository.day(selected.id, date.toString()) }
                        val sessions = async { sessionRepository.forDay(selected.id, date.toString()) }
                        val streak = if (hasCompleteSchedule(selected)) async { progressRepository.streak(selected.id) } else null
                        val dailyValue = daily.await().remote(previous.progress.data)
                        val sessionsValue = sessions.await().remote(previous.sessions.data)
                        val streakValue = streak?.await()?.remote(previous.streak.data) ?: RemoteValue()
                        _state.update {
                            it.copy(loading = false, progress = dailyValue, sessions = sessionsValue, streak = streakValue)
                        }
                    }
                }
            }
        }
    }
}

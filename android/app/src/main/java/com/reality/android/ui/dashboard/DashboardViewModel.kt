package com.reality.android.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality.android.core.network.ApiResult
import com.reality.android.core.util.isScheduled
import com.reality.android.core.util.today
import com.reality.android.data.remote.dto.*
import com.reality.android.data.repository.*
import com.reality.android.ui.progress.RemoteValue
import com.reality.android.ui.progress.hasCompleteSchedule
import com.reality.android.ui.progress.remote
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

data class DashboardActivity(
    val activity: ActivityDto,
    val progress: RemoteValue<DailyProgressDto> = RemoteValue(),
    val streak: RemoteValue<StreakDto> = RemoteValue()
)

data class DashboardUiState(
    val loading: Boolean = true,
    val date: LocalDate? = null,
    val serverZoneId: String = "",
    val activities: List<ActivityDto>? = null,
    val sessions: List<SessionDto>? = null,
    val rows: List<DashboardActivity> = emptyList(),
    val activityError: String? = null,
    val sessionError: String? = null
)

/** A failed daily read must not be represented as a zero. Streak failures are independent. */
fun dashboardRecordedSeconds(rows: List<DashboardActivity>): Long? =
    if (rows.all { it.progress.isCurrent }) rows.sumOf { it.progress.data?.totalDuration ?: 0L } else null

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val activities: ActivityRepository,
    private val sessions: SessionRepository,
    private val progress: ProgressRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var zoneId: String? = null

    init {
        viewModelScope.launch {
            settings.settings.map { it.backendUrl to it.serverZoneId }.distinctUntilChanged().collect { (_, zone) ->
                zoneId = zone
                loadJob?.cancel()
                load()
            }
        }
    }

    fun refresh() { if (loadJob?.isActive != true) load() }

    private fun load() {
        val zone = zoneId ?: return
        loadJob = viewModelScope.launch {
            val date = today(zone)
            val previous = _state.value
            _state.update { it.copy(loading = true, date = date, serverZoneId = zone) }
            coroutineScope {
                val activityRequest = async { activities.list() }
                val sessionRequest = async { sessions.list() }
                val activityResult = activityRequest.await()
                val sessionResult = sessionRequest.await()
                val activityList = (activityResult as? ApiResult.Success<List<ActivityDto>>)?.data
                val rows = if (activityList != null) {
                    val limit = Semaphore(4)
                    activityList.map { activity ->
                        async {
                            limit.withPermit {
                                coroutineScope {
                                    val old = previous.rows.firstOrNull { it.activity.id == activity.id }
                                        ?.takeIf { previous.date == date }
                                    val dayRequest = async { progress.day(activity.id, date.toString()) }
                                    val streakRequest = if (hasCompleteSchedule(activity)) async { progress.streak(activity.id) } else null
                                    DashboardActivity(
                                        activity,
                                        dayRequest.await().remote(old?.progress?.data),
                                        streakRequest?.await()?.remote(old?.streak?.data) ?: RemoteValue()
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                } else previous.rows
                _state.update {
                    it.copy(
                        loading = false,
                        activities = activityList ?: previous.activities,
                        sessions = (sessionResult as? ApiResult.Success<List<SessionDto>>)?.data ?: previous.sessions,
                        rows = rows,
                        activityError = (activityResult as? ApiResult.Failure)?.error?.message,
                        sessionError = (sessionResult as? ApiResult.Failure)?.error?.message
                    )
                }
            }
        }
    }
}

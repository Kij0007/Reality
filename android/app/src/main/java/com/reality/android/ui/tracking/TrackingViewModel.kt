package com.reality.android.ui.tracking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality.android.R
import com.reality.android.core.network.ApiResult
import com.reality.android.data.remote.dto.ActivityDto
import com.reality.android.data.remote.dto.BreakDto
import com.reality.android.data.remote.dto.SessionDto
import com.reality.android.data.repository.ActivityRepository
import com.reality.android.data.repository.SessionRepository
import com.reality.android.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDate

data class TrackingUiState(
    val activities: List<ActivityDto> = emptyList(),
    val sessions: List<SessionDto> = emptyList(),
    val allSessions: List<SessionDto> = emptyList(),
    val breaks: Map<Long, List<BreakDto>> = emptyMap(),
    val breakErrors: Map<Long, String> = emptyMap(),
    val activityId: Long? = null,
    val date: String? = null,
    val dayTotal: Long? = null,
    val status: SessionStatus = SessionStatus.ALL,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val sessionsKnown: Boolean = false,
    val error: String? = null,
    val activitiesError: String? = null,
    val notice: Int? = null,
    val zoneId: String = "Asia/Kolkata",
    val activitiesKnown: Boolean = false,
) {
    val visible get() = sessions.filter {
        status == SessionStatus.ALL || sessionStatus(it, breaks[it.id]) == status
    }.sortedByDescending { it.startTime }
    val open get() = allSessions.filter { it.endTime == null }.sortedByDescending { it.startTime }
    val canStart get() = activityId != null && !loading && !busy && sessionsKnown && activitiesKnown &&
        activities.any { it.id == activityId && it.active != false } &&
        allSessions.none { it.activityId == activityId && it.endTime == null }
}

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val activities: ActivityRepository,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutable = MutableStateFlow(
        TrackingUiState(
            activityId = savedState["filterActivityId"],
            date = savedState["filterDate"],
            status = SessionStatus.entries.firstOrNull { it.name == savedState.get<String>("filterStatus") }
                ?: SessionStatus.ALL,
        )
    )
    val state = mutable.asStateFlow()
    private val gate = Mutex()
    private var initialized = false

    fun load(initialActivityId: Long?) {
        if (initialized) return
        initialized = true
        if (initialActivityId != null) {
            savedState["filterActivityId"] = initialActivityId
            mutable.update { it.copy(activityId = initialActivityId, date = null) }
        }
        refresh()
    }
    fun selectActivity(id: Long?) {
        if (mutable.value.loading || mutable.value.busy) return
        savedState["filterActivityId"] = id
        savedState["filterDate"] = null
        mutable.update { it.copy(activityId = id, date = null, dayTotal = null) }
        refresh()
    }
    fun selectDate(date: String?) {
        if (mutable.value.loading || mutable.value.busy || mutable.value.activityId == null) return
        if (date != null && runCatching { LocalDate.parse(date) }.isFailure) return
        savedState["filterDate"] = date
        mutable.update { it.copy(date = date, dayTotal = null) }
        refresh()
    }
    fun selectStatus(status: SessionStatus) {
        savedState["filterStatus"] = status.name
        mutable.update { it.copy(status = status) }
    }
    fun refresh() {
        if (!gate.tryLock()) return
        viewModelScope.launch {
            try { fetch() } finally { gate.unlock() }
        }
    }
    private suspend fun fetch() = coroutineScope {
        val filter = mutable.value
        mutable.update {
            it.copy(loading = true, error = null, activitiesError = null, sessionsKnown = false, activitiesKnown = false)
        }
        val zone = settings.settings.first().serverZoneId
        val activityResult = async { activities.list() }
        val allResult = async { sessions.list() }
        val filteredResult = async {
            when {
                filter.activityId == null -> null
                filter.date != null -> when (val day = sessions.forDay(filter.activityId, filter.date)) {
                    is ApiResult.Success -> {
                        mutable.update { it.copy(dayTotal = day.data.totalDuration) }
                        ApiResult.Success(day.data.sessions)
                    }
                    is ApiResult.Failure -> day
                }
                else -> sessions.forActivity(filter.activityId)
            }
        }
        when (val result = activityResult.await()) {
            is ApiResult.Success -> mutable.update { it.copy(activities = result.data, zoneId = zone, activitiesKnown = true) }
            is ApiResult.Failure -> mutable.update { it.copy(activitiesError = result.error.message, zoneId = zone) }
        }
        when (val result = allResult.await()) {
            is ApiResult.Success -> {
                mutable.update {
                    it.copy(allSessions = result.data, sessionsKnown = true,
                        sessions = if (filter.activityId == null) result.data else it.sessions)
                }
            }
            is ApiResult.Failure -> mutable.update { it.copy(error = result.error.message) }
        }
        when (val result = filteredResult.await()) {
            is ApiResult.Success -> mutable.update { it.copy(sessions = result.data) }
            is ApiResult.Failure -> mutable.update { it.copy(error = result.error.message, dayTotal = null) }
            null -> Unit
        }
        // Refresh every open session's break state independently; one failure does not discard others.
        val open = mutable.value.allSessions.filter { it.endTime == null }
        val breakResults = open.map { session ->
            async { session.id to sessions.breaks(session.id) }
        }.awaitAll()
        val known = mutableMapOf<Long, List<BreakDto>>()
        val failures = mutableMapOf<Long, String>()
        breakResults.forEach { (id, result) ->
            when (result) {
                is ApiResult.Success -> known[id] = result.data
                is ApiResult.Failure -> failures[id] = result.error.message
            }
        }
        mutable.update { it.copy(breaks = known, breakErrors = failures, loading = false) }
    }
    fun start() {
        val id = mutable.value.activityId ?: return
        if (!mutable.value.canStart || !gate.tryLock()) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                when (val latest = sessions.forActivity(id)) {
                    is ApiResult.Failure -> mutable.update {
                        it.copy(error = latest.error.message, sessionsKnown = false)
                    }
                    is ApiResult.Success -> {
                        if (latest.data.any { it.endTime == null }) {
                            mutable.update { it.copy(notice = R.string.wf_session_already_open) }
                            fetch()
                        } else when (val result = sessions.start(id)) {
                            is ApiResult.Success -> {
                                fetch()
                                mutable.update { it.copy(notice = R.string.wf_session_started) }
                            }
                            is ApiResult.Failure -> {
                                fetch()
                                mutable.update { it.copy(error = result.error.message) }
                            }
                        }
                    }
                }
            } finally {
                mutable.update { it.copy(busy = false) }
                gate.unlock()
            }
        }
    }
    fun stop(session: SessionDto) = act(session, SessionAction.STOP)
    fun resume(session: SessionDto) = act(session, SessionAction.RESUME)
    fun startBreak(session: SessionDto, description: String?) = act(session, SessionAction.BREAK, description)

    private fun act(session: SessionDto, action: SessionAction, description: String? = null) {
        val current = mutable.value
        val latestSession = current.allSessions.firstOrNull { it.id == session.id } ?: return
        if (current.loading || current.busy || !current.sessionsKnown ||
            !permitsAction(action, latestSession, current.breaks[session.id])) return
        if (description != null && description.length > 255) return
        if (!gate.tryLock()) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = when (action) {
                    SessionAction.STOP -> sessions.stop(session.id)
                    SessionAction.RESUME -> sessions.resume(session.id)
                    SessionAction.BREAK -> sessions.startBreak(session.id, description?.trim()?.ifEmpty { null })
                }
                fetch()
                when (result) {
                    is ApiResult.Success -> mutable.update { it.copy(notice = action.successString) }
                    is ApiResult.Failure -> mutable.update { it.copy(error = result.error.message) }
                }
            } finally {
                mutable.update { it.copy(busy = false) }
                gate.unlock()
            }
        }
    }
    fun clearNotice() = mutable.update { it.copy(notice = null) }
}

internal enum class SessionAction(val successString: Int) {
    STOP(R.string.wf_session_stopped), BREAK(R.string.wf_break_started), RESUME(R.string.wf_session_resumed)
}
internal fun permitsAction(action: SessionAction, session: SessionDto, breaks: List<BreakDto>?): Boolean =
    when (action) {
        SessionAction.STOP -> permitsStop(session)
        SessionAction.BREAK -> permitsBreak(session, breaks)
        SessionAction.RESUME -> permitsResume(session, breaks)
    }

package com.reality.android.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality.android.core.network.ApiResult
import com.reality.android.data.remote.dto.BreakDto
import com.reality.android.data.remote.dto.SessionDto
import com.reality.android.data.repository.ActivityRepository
import com.reality.android.data.repository.SessionRepository
import com.reality.android.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class SessionDetailUiState(
    val session: SessionDto? = null,
    val breaks: List<BreakDto>? = null,
    val activityName: String? = null,
    val zoneId: String = "Asia/Kolkata",
    val loading: Boolean = true,
    val busy: Boolean = false,
    val sessionKnown: Boolean = false,
    val error: String? = null,
    val breakError: String? = null,
    val notice: Int? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val activities: ActivityRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(SessionDetailUiState())
    val state = mutable.asStateFlow()
    private val gate = Mutex()
    private var sessionId: Long? = null

    fun load(id: Long) {
        if (id == sessionId) return
        sessionId = id
        refresh()
    }
    fun refresh() {
        val id = sessionId ?: return
        if (!gate.tryLock()) return
        viewModelScope.launch { try { fetch(id) } finally { gate.unlock() } }
    }
    private suspend fun fetch(id: Long) = coroutineScope {
        mutable.update {
            it.copy(loading = true, error = null, breakError = null, sessionKnown = false, breaks = null)
        }
        val breakResult = async { sessions.breaks(id) }
        val activityResult = async { activities.list() }
        val zone = settings.settings.first().serverZoneId
        when (val result = sessions.get(id)) {
            is ApiResult.Success -> {
                mutable.update { it.copy(session = result.data, sessionKnown = true, zoneId = zone) }
            }
            is ApiResult.Failure -> mutable.update { it.copy(error = result.error.message, zoneId = zone) }
        }
        when (val result = breakResult.await()) {
            is ApiResult.Success -> mutable.update { it.copy(breaks = result.data) }
            is ApiResult.Failure -> mutable.update { it.copy(breakError = result.error.message) }
        }
        when (val result = activityResult.await()) {
            is ApiResult.Success -> mutable.update { value ->
                value.copy(activityName = result.data.firstOrNull { it.id == value.session?.activityId }?.name)
            }
            is ApiResult.Failure -> Unit // History remains usable when an active-activity lookup is unavailable.
        }
        mutable.update { it.copy(loading = false) }
    }

    fun stop() = act(SessionAction.STOP)
    fun resume() = act(SessionAction.RESUME)
    fun startBreak(description: String?) = act(SessionAction.BREAK, description)
    private fun act(action: SessionAction, description: String? = null) {
        val current = mutable.value
        val session = current.session ?: return
        if (!current.sessionKnown || current.loading || current.busy ||
            !permitsAction(action, session, current.breaks) || (description?.length ?: 0) > 255 ||
            !gate.tryLock()) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = when (action) {
                    SessionAction.STOP -> sessions.stop(session.id)
                    SessionAction.BREAK -> sessions.startBreak(session.id, description?.trim()?.ifEmpty { null })
                    SessionAction.RESUME -> sessions.resume(session.id)
                }
                fetch(session.id)
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

    fun delete() {
        val current = mutable.value
        val id = sessionId ?: return
        if (!current.sessionKnown || current.loading || current.busy || !gate.tryLock()) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = sessions.delete(id)) {
                    is ApiResult.Success -> mutable.update { it.copy(deleted = true) }
                    is ApiResult.Failure -> {
                        if (result.error.uncertainWrite) {
                            when (val check = sessions.get(id)) {
                                is ApiResult.Success -> fetch(id)
                                is ApiResult.Failure -> {
                                    if (check.error.statusCode == 404) mutable.update { it.copy(deleted = true) }
                                    else fetch(id)
                                }
                            }
                        }
                        mutable.update { it.copy(error = result.error.message) }
                    }
                }
            } finally {
                mutable.update { it.copy(busy = false) }
                gate.unlock()
            }
        }
    }
    fun clearNotice() = mutable.update { it.copy(notice = null) }
}

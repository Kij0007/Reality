package com.reality.android.ui.activities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality.android.R
import com.reality.android.core.network.ApiResult
import com.reality.android.core.util.today
import com.reality.android.core.util.validateActivity
import com.reality.android.data.remote.dto.ActivityCategory
import com.reality.android.data.remote.dto.ActivityDto
import com.reality.android.data.remote.dto.ActivityRequest
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

enum class ActivitySort { NAME, NEWEST, DURATION }

data class ActivitiesUiState(
    val activities: List<ActivityDto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val query: String = "",
    val category: ActivityCategory? = null,
    val sort: ActivitySort = ActivitySort.NAME,
) {
    val visible: List<ActivityDto> get() {
        val filtered = activities.filter {
            (category == null || it.category == category) &&
                (it.name.orEmpty().contains(query, ignoreCase = true))
        }
        return when (sort) {
            ActivitySort.NAME -> filtered.sortedBy { it.name.orEmpty().lowercase() }
            ActivitySort.NEWEST -> filtered.sortedByDescending { it.createdAt.orEmpty() }
            ActivitySort.DURATION -> filtered.sortedByDescending { it.minimumDuration ?: 0 }
        }
    }
}

@HiltViewModel
class ActivitiesViewModel @Inject constructor(
    private val repository: ActivityRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutable = MutableStateFlow(
        ActivitiesUiState(
            query = savedState["query"] ?: "",
            category = savedState.get<String>("category")?.let { name ->
                ActivityCategory.entries.firstOrNull { it.name == name }
            },
            sort = ActivitySort.entries.firstOrNull { it.name == savedState.get<String>("sort") }
                ?: ActivitySort.NAME,
        )
    )
    val state = mutable.asStateFlow()
    private val loadGate = Mutex()
    init { refresh() }

    fun setQuery(value: String) {
        savedState["query"] = value
        mutable.update { it.copy(query = value) }
    }
    fun setCategory(value: ActivityCategory?) {
        savedState["category"] = value?.name
        mutable.update { it.copy(category = value) }
    }
    fun setSort(value: ActivitySort) {
        savedState["sort"] = value.name
        mutable.update { it.copy(sort = value) }
    }
    fun refresh() {
        if (!loadGate.tryLock()) return
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = repository.list()) {
                    is ApiResult.Success -> mutable.update {
                        it.copy(activities = result.data, loading = false)
                    }
                    is ApiResult.Failure -> mutable.update {
                        it.copy(error = result.error.message, loading = false)
                    }
                }
            } finally { loadGate.unlock() }
        }
    }
}

data class ActivityFormUiState(
    val id: Long? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val name: String = "",
    val duration: String = "",
    val category: ActivityCategory? = null,
    val days: Set<String> = emptySet(),
    val startDate: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val saved: Boolean = false,
    val uncertainWrite: Boolean = false,
)

@HiltViewModel
class ActivityFormViewModel @Inject constructor(
    private val repository: ActivityRepository,
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutable = MutableStateFlow(ActivityFormUiState())
    val state = mutable.asStateFlow()
    private var initialized = false
    private var requestedId: Long? = null
    private val saveGate = Mutex()

    fun load(id: Long?, force: Boolean = false) {
        if (initialized && id == requestedId && !force) return
        initialized = true
        requestedId = id
        mutable.update { it.copy(id = id, loading = true, error = null) }
        viewModelScope.launch {
            val storedId = savedState.get<String>("formId")
            val key = id?.toString() ?: "new"
            if (!force && storedId == key && savedState.get<Boolean>("hasDraft") == true) {
                mutable.value = ActivityFormUiState(
                    id = id, loading = false,
                    name = savedState["name"] ?: "",
                    duration = savedState["duration"] ?: "",
                    category = savedState.get<String>("category")?.let { value ->
                        ActivityCategory.entries.firstOrNull { it.name == value }
                    },
                    days = savedState.get<ArrayList<String>>("days")?.toSet() ?: emptySet(),
                    startDate = savedState["startDate"] ?: "",
                    uncertainWrite = savedState["uncertainWrite"] ?: false,
                )
                return@launch
            }
            if (id == null) {
                val zone = settings.settings.first().serverZoneId
                mutable.value = ActivityFormUiState(loading = false, startDate = today(zone).toString())
                persist()
            } else {
                when (val result = repository.get(id)) {
                    is ApiResult.Success -> {
                        val activity = result.data
                        mutable.value = ActivityFormUiState(
                            id = id, loading = false,
                            name = activity.name.orEmpty(),
                            duration = activity.minimumDuration?.toString().orEmpty(),
                            category = activity.category,
                            days = activity.scheduledDays?.toSet() ?: emptySet(),
                            startDate = activity.startDate.orEmpty(),
                        )
                        persist()
                    }
                    is ApiResult.Failure -> mutable.update {
                        it.copy(loading = false, error = result.error.message)
                    }
                }
            }
        }
    }
    fun setName(value: String) = change { it.copy(name = value) }
    fun setDuration(value: String) = change { it.copy(duration = value) }
    fun setCategory(value: ActivityCategory) = change { it.copy(category = value) }
    fun setStartDate(value: String) = change { it.copy(startDate = value) }
    fun toggleDay(value: String) = change {
        it.copy(days = if (value in it.days) it.days - value else it.days + value)
    }
    private fun change(transform: (ActivityFormUiState) -> ActivityFormUiState) {
        if (mutable.value.saving || mutable.value.uncertainWrite) return
        mutable.update { transform(it).copy(fieldErrors = emptyMap(), error = null) }
        persist()
    }
    private fun persist() {
        val value = mutable.value
        savedState["formId"] = value.id?.toString() ?: "new"
        savedState["hasDraft"] = true
        savedState["name"] = value.name
        savedState["duration"] = value.duration
        savedState["category"] = value.category?.name
        savedState["days"] = ArrayList(value.days)
        savedState["startDate"] = value.startDate
        savedState["uncertainWrite"] = value.uncertainWrite
    }
    fun save() {
        val value = mutable.value
        if (value.loading || value.saving || value.uncertainWrite || !saveGate.tryLock()) return
        val errors = validateActivity(
            value.name, value.duration, value.category, value.days, value.startDate
        )
        if (errors.isNotEmpty()) {
            mutable.update { it.copy(fieldErrors = errors) }
            saveGate.unlock()
            return
        }
        val category = value.category ?: run { saveGate.unlock(); return }
        val duration = value.duration.toIntOrNull() ?: run { saveGate.unlock(); return }
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val request = ActivityRequest(
                    name = value.name.trim(), minimumDuration = duration,
                    category = category, scheduledDays = value.days, startDate = value.startDate,
                )
                val result = value.id?.let { repository.update(it, request) } ?: repository.create(request)
                when (result) {
                    is ApiResult.Success -> {
                        savedState["hasDraft"] = false
                        mutable.update { it.copy(saving = false, saved = true) }
                    }
                    is ApiResult.Failure -> {
                        mutable.update {
                            it.copy(saving = false, error = result.error.message,
                                uncertainWrite = result.error.uncertainWrite)
                        }
                        persist()
                    }
                }
            } finally { saveGate.unlock() }
        }
    }
}

data class ActivityDetailUiState(
    val activity: ActivityDto? = null,
    val sessions: List<SessionDto> = emptyList(),
    val loading: Boolean = true,
    val sessionsKnown: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val sessionsError: String? = null,
    val notice: Int? = null,
    val deleted: Boolean = false,
    val startedSessionId: Long? = null,
) {
    val openSessions get() = sessions.filter { it.endTime == null }
    val canStart get() = !loading && !busy && sessionsKnown &&
        activity?.active != false && openSessions.isEmpty() && activity != null
    val canDelete get() = !loading && !busy && sessionsKnown &&
        openSessions.isEmpty() && activity != null
}

@HiltViewModel
class ActivityDetailViewModel @Inject constructor(
    private val activities: ActivityRepository,
    private val sessions: SessionRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(ActivityDetailUiState())
    val state = mutable.asStateFlow()
    private var activityId: Long? = null
    private val loadGate = Mutex()
    private val actionGate = Mutex()
    fun load(id: Long) {
        if (id != activityId) {
            activityId = id
            refresh()
        }
    }
    fun refresh() {
        val id = activityId ?: return
        if (mutable.value.busy || !loadGate.tryLock()) return
        viewModelScope.launch {
            try { fetch(id) } finally { loadGate.unlock() }
        }
    }
    private suspend fun fetch(id: Long) = coroutineScope {
        mutable.update { it.copy(loading = true, error = null, sessionsError = null, sessionsKnown = false) }
        val activityResult = async { activities.get(id) }
        val sessionResult = async { sessions.forActivity(id) }
        when (val result = activityResult.await()) {
            is ApiResult.Success -> mutable.update { it.copy(activity = result.data) }
            is ApiResult.Failure -> mutable.update { it.copy(error = result.error.message) }
        }
        when (val result = sessionResult.await()) {
            is ApiResult.Success -> mutable.update { it.copy(sessions = result.data, sessionsKnown = true) }
            is ApiResult.Failure -> mutable.update { it.copy(sessionsError = result.error.message) }
        }
        mutable.update { it.copy(loading = false) }
    }
    fun start() {
        val id = activityId ?: return
        if (!mutable.value.canStart || !actionGate.tryLock()) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                // Re-read before a non-idempotent write. Another client may have started a session.
                when (val latest = sessions.forActivity(id)) {
                    is ApiResult.Failure -> mutable.update {
                        it.copy(error = latest.error.message, sessionsKnown = false)
                    }
                    is ApiResult.Success -> {
                        mutable.update { it.copy(sessions = latest.data, sessionsKnown = true) }
                        if (latest.data.none { it.endTime == null }) {
                            when (val result = sessions.start(id)) {
                                is ApiResult.Success -> {
                                    mutable.update {
                                        it.copy(startedSessionId = result.data.id, notice = R.string.wf_session_started)
                                    }
                                    fetch(id)
                                }
                                is ApiResult.Failure -> {
                                    mutable.update { it.copy(error = result.error.message) }
                                    fetch(id)
                                    mutable.update { it.copy(error = result.error.message) }
                                }
                            }
                        } else {
                            mutable.update { it.copy(notice = R.string.wf_session_already_open) }
                        }
                    }
                }
            } finally {
                mutable.update { it.copy(busy = false) }
                actionGate.unlock()
            }
        }
    }
    fun delete() {
        val id = activityId ?: return
        if (!mutable.value.canDelete || !actionGate.tryLock()) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                when (val latest = sessions.forActivity(id)) {
                    is ApiResult.Failure -> mutable.update {
                        it.copy(error = latest.error.message, sessionsKnown = false)
                    }
                    is ApiResult.Success -> {
                        mutable.update { it.copy(sessions = latest.data) }
                        if (latest.data.any { it.endTime == null }) {
                            mutable.update { it.copy(notice = R.string.wf_stop_before_delete) }
                        } else when (val result = activities.delete(id)) {
                            is ApiResult.Success -> mutable.update { it.copy(deleted = true) }
                            is ApiResult.Failure -> {
                                mutable.update { it.copy(error = result.error.message) }
                                if (result.error.uncertainWrite) {
                                    fetch(id)
                                    mutable.update { it.copy(error = result.error.message) }
                                }
                            }
                        }
                    }
                }
            } finally {
                mutable.update { it.copy(busy = false) }
                actionGate.unlock()
            }
        }
    }
    fun clearNotice() = mutable.update { it.copy(notice = null) }
}

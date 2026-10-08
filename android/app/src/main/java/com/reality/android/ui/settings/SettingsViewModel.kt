package com.reality.android.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality.android.R
import com.reality.android.core.network.ApiResult
import com.reality.android.core.network.MutationGate
import com.reality.android.data.repository.ActivityRepository
import com.reality.android.data.repository.AppSettings
import com.reality.android.data.repository.SettingsRepository
import com.reality.android.data.repository.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

data class SettingsUiState(
    val settings: AppSettings? = null,
    val url: String = "",
    val zone: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val testing: Boolean = false,
    val mutationBusy: Boolean = false,
    val error: String? = null,
    val message: Int? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: SettingsRepository,
    private val activities: ActivityRepository,
    private val mutations: MutationGate,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.settings.collect { value ->
                _state.update { current ->
                    current.copy(
                        settings = value,
                        url = if (current.loading) savedState["serverUrl"] ?: value.backendUrl else current.url,
                        zone = if (current.loading) savedState["serverZone"] ?: value.serverZoneId else current.zone,
                        loading = false
                    )
                }
            }
        }
        viewModelScope.launch {
            mutations.busy.collect { busy -> _state.update { it.copy(mutationBusy = busy) } }
        }
    }

    fun changeUrl(value: String) {
        savedState["serverUrl"] = value
        _state.update { it.copy(url = value, error = null, message = null) }
    }

    fun changeZone(value: String) {
        savedState["serverZone"] = value
        _state.update { it.copy(zone = value, error = null, message = null) }
    }

    fun saveConnection() {
        if (state.value.saving || state.value.testing || state.value.mutationBusy) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, message = null) }
            try {
                preferences.saveBackendUrl(state.value.url.trim())
                savedState.remove<String>("serverUrl")
                _state.update { it.copy(message = R.string.settings_saved) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalArgumentException) {
                _state.update { it.copy(error = error.message ?: "Enter a valid backend URL.") }
            } catch (_: Exception) {
                _state.update { it.copy(message = R.string.settings_read_failure) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun saveZone() {
        if (state.value.saving || state.value.testing || state.value.mutationBusy) return
        val zone = state.value.zone.trim()
        if (runCatching { ZoneId.of(zone) }.isFailure) {
            _state.update { it.copy(message = R.string.settings_zone_invalid) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, message = null) }
            try {
                preferences.saveServerZone(zone)
                savedState.remove<String>("serverZone")
                _state.update { it.copy(message = R.string.settings_zone_saved) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(message = R.string.settings_read_failure) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun testConnection() {
        val current = state.value
        if (current.testing || current.saving) return
        if (current.url.trim() != current.settings?.backendUrl) {
            _state.update { it.copy(message = R.string.settings_not_saved) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(testing = true, error = null, message = null) }
            when (val result = activities.list()) {
                is ApiResult.Success -> _state.update { it.copy(message = R.string.settings_connected) }
                is ApiResult.Failure -> _state.update { it.copy(error = result.error.message) }
            }
            _state.update { it.copy(testing = false) }
        }
    }

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch {
            try {
                preferences.saveTheme(theme)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(message = R.string.settings_read_failure) }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}

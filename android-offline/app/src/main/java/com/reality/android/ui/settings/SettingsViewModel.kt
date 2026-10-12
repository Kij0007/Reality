package com.reality.android.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality.android.R
import com.reality.android.core.network.MutationGate
import com.reality.android.data.repository.AppSettings
import com.reality.android.data.repository.SettingsRepository
import com.reality.android.data.repository.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(val settings: AppSettings? = null, val zone: String = "", val loading: Boolean = true,
    val saving: Boolean = false, val mutationBusy: Boolean = false, val error: String? = null, val message: Int? = null)
@HiltViewModel
class SettingsViewModel @Inject constructor(private val preferences: SettingsRepository,
    private val mutations: MutationGate, private val savedState: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()
    init {
        viewModelScope.launch { preferences.settings.collect { value -> _state.update {
            it.copy(settings = value, zone = if (it.loading) savedState["clockZone"] ?: value.serverZoneId else it.zone, loading = false)
        } } }
        viewModelScope.launch { mutations.busy.collect { busy -> _state.update { it.copy(mutationBusy = busy) } } }
    }
    fun changeZone(value: String) { savedState["clockZone"] = value; _state.update { it.copy(zone = value, error = null, message = null) } }
    fun saveZone() {
        if (state.value.saving || state.value.mutationBusy) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, message = null) }
            try { preferences.saveServerZone(state.value.zone); savedState.remove<String>("clockZone")
                _state.update { it.copy(zone = preferences.settings.first().serverZoneId, message = R.string.settings_zone_saved) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: IllegalArgumentException) { _state.update { it.copy(error = failure.message) }
            } catch (_: Exception) { _state.update { it.copy(message = R.string.settings_read_failure) }
            } finally { _state.update { it.copy(saving = false) } }
        }
    }
    fun setTheme(theme: ThemePreference) { viewModelScope.launch {
        try { preferences.saveTheme(theme) } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { _state.update { it.copy(message = R.string.settings_read_failure) } }
    } }
    fun dismissMessage() = _state.update { it.copy(message = null) }
}

package com.reality.android.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality.android.core.network.*
import com.reality.android.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val username: String = "", val displayName: String = "", val password: String = "",
    val registering: Boolean = false, val submitting: Boolean = false, val waking: Boolean = false,
    val mutationBusy: Boolean = false, val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository, private val sessions: SessionStore,
    private val settings: SettingsRepository, mutations: MutationGate,
    private val saved: SavedStateHandle
) : ViewModel() {
    private val mutable = MutableStateFlow(AuthUiState(
        username = saved["authUsername"] ?: "", displayName = saved["authName"] ?: "",
        error = if (saved.get<Boolean>("authRequestPending") == true)
            "A previous account request may have reached the server. Try signing in before creating the account again." else null,
    ))
    val state = mutable.asStateFlow()
    val session = sessions.state

    init {
        viewModelScope.launch { mutations.busy.collect { busy -> mutable.update { it.copy(mutationBusy = busy) } } }
        viewModelScope.launch {
            settings.settings.collect { value ->
                sessions.awaitLoaded()
                val session = sessions.state.value.session
                if (session != null && !AuthRules.usable(session, value.backendUrl)) sessions.clear(session.auth.token)
            }
        }
    }

    fun username(value: String) { saved["authUsername"] = value; mutable.update { it.copy(username = value, error = null) } }
    fun displayName(value: String) { saved["authName"] = value; mutable.update { it.copy(displayName = value, error = null) } }
    // Passwords deliberately never enter SavedStateHandle or disk.
    fun password(value: String) = mutable.update { it.copy(password = value, error = null) }
    fun toggleRegistration() { if (!state.value.submitting) mutable.update { it.copy(registering = !it.registering, password = "", error = null) } }

    fun submit() {
        val input = state.value
        if (input.submitting || input.mutationBusy) return
        val username = input.username.trim().lowercase(java.util.Locale.ROOT)
        val displayName = input.displayName.takeIf { input.registering }
        val invalid = AuthRules.validate(username, input.password, displayName)
        if (invalid != null) { mutable.update { it.copy(error = invalid) }; return }
        // Preserve uncertainty through process death without ever saving a password.
        saved["authRequestPending"] = true
        mutable.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = auth.signIn(username, input.password, displayName) { mutable.update { it.copy(waking = true) } }) {
                    is ApiResult.Success -> {
                        saved.remove<Boolean>("authRequestPending")
                        mutable.update { it.copy(password = "", error = null) }
                    }
                    is ApiResult.Failure -> {
                        if (!result.error.uncertainWrite) saved.remove<Boolean>("authRequestPending")
                        mutable.update { it.copy(error = result.error.message +
                            if (input.registering && result.error.uncertainWrite) " Try signing in before creating the account again." else "") }
                    }
                }
            } finally { mutable.update { it.copy(submitting = false, waking = false) } }
        }
    }

    fun logout() {
        if (state.value.mutationBusy || state.value.submitting) return
        mutable.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = auth.logout()) {
                    is ApiResult.Success -> mutable.update { it.copy(password = "") }
                    is ApiResult.Failure -> mutable.update { it.copy(error = result.error.message) }
                }
            } finally { mutable.update { it.copy(submitting = false) } }
        }
    }
}

package com.reality.android.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality.android.R
import com.reality.android.core.network.ApiResult
import com.reality.android.core.network.MutationGate
import com.reality.android.data.backup.BackupRepository
import com.reality.android.data.backup.RealityBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val busy: Boolean = false,
    val mutationBusy: Boolean = false,
    val pending: RealityBackup? = null,
    val error: String? = null,
    val message: Int? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
    mutations: MutationGate,
) : ViewModel() {
    private val mutable = MutableStateFlow(BackupUiState())
    val state = mutable.asStateFlow()
    init { viewModelScope.launch { mutations.busy.collect { busy -> mutable.update { it.copy(mutationBusy = busy) } } } }

    fun export(uri: Uri) = launchAction {
        when (val result = repository.export(uri)) {
            is ApiResult.Success -> mutable.update { it.copy(message = R.string.backup_export_success) }
            is ApiResult.Failure -> mutable.update { it.copy(error = result.error.message) }
        }
    }

    fun inspect(uri: Uri) = launchAction {
        when (val result = repository.inspect(uri)) {
            is ApiResult.Success -> mutable.update { it.copy(pending = result.data) }
            is ApiResult.Failure -> mutable.update { it.copy(error = result.error.message) }
        }
    }

    fun restore() {
        val pending = state.value.pending ?: return
        launchAction {
            when (val result = repository.restore(pending)) {
                is ApiResult.Success -> mutable.update { it.copy(pending = null, message = R.string.backup_restore_success) }
                is ApiResult.Failure -> mutable.update { it.copy(error = result.error.message) }
            }
        }
    }

    fun cancelRestore() { if (!state.value.busy) mutable.update { it.copy(pending = null) } }
    fun dismissMessage() = mutable.update { it.copy(message = null) }

    private fun launchAction(action: suspend () -> Unit) {
        if (state.value.busy || state.value.mutationBusy) return
        mutable.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            try { action() } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}

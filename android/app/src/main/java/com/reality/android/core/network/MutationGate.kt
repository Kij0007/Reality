package com.reality.android.core.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class MutationGate @Inject constructor() {
    private val mutex = Mutex()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    suspend fun <T> run(action: suspend () -> ApiResult<T>): ApiResult<T> {
        if (!mutex.tryLock()) return ApiResult.Failure(AppError("Another change is in progress. Please wait."))
        _busy.value = true
        return try { action() } finally { _busy.value = false; mutex.unlock() }
    }
}

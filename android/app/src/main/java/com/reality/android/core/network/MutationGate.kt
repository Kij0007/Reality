package com.reality.android.core.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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

    /** Configuration and server writes share one lock, so an address cannot
     * change after a write is admitted but before OkHttp dispatches it. */
    suspend fun <T> changeConfiguration(action: suspend () -> T): T {
        check(mutex.tryLock()) { "Another change is in progress. Please wait before changing the server settings." }
        _busy.value = true
        return try {
            // Finish the disk write and publish it before releasing the gate,
            // even when the settings screen is dismissed during the save.
            withContext(NonCancellable) { action() }
        } finally {
            _busy.value = false
            mutex.unlock()
        }
    }
}

package com.reality.android.core.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

// One gate across repositories prevents overlapping writes from different screens.
@Singleton
class MutationGate @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> run(action: suspend () -> ApiResult<T>): ApiResult<T> {
        if (!mutex.tryLock()) return ApiResult.Failure(AppError("Another change is in progress. Please wait."))
        return try { action() } finally { mutex.unlock() }
    }
}

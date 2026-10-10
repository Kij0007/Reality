package com.reality.android.data.repository

import com.reality.android.core.network.*
import com.reality.android.data.remote.api.AuthApi
import com.reality.android.data.remote.dto.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi, private val executor: ApiExecutor, private val mutations: MutationGate,
    private val sessions: SessionStore, private val settings: SettingsRepository
) {
    suspend fun health(): ApiResult<HealthDto> {
        val result = executor.execute { api.health() }
        return if (result is ApiResult.Success && result.data.status != "UP")
            ApiResult.Failure(AppError("This address did not return a healthy Reality server.", statusCode = 200))
        else result
    }
    suspend fun currentUser() = executor.execute { api.me() }

    suspend fun signIn(username: String, password: String, displayName: String?, waking: () -> Unit): ApiResult<AuthDto> = mutations.run {
        AuthRules.validate(username, password, displayName)?.let { return@run ApiResult.Failure(AppError(it)) }
        waking()
        // Free servers can take a minute to wake. Retry only this public read, never the account write.
        val ready = withTimeoutOrNull(120_000) {
            var result = health()
            while (result is ApiResult.Failure && (result.error.statusCode == null || result.error.statusCode >= 500)) {
                delay(3_000)
                result = health()
            }
            result
        } ?: ApiResult.Failure(AppError("The Reality server is not ready. Check your connection and try again."))
        if (ready is ApiResult.Failure) return@run ready
        val result = executor.execute(mutation = true) {
            if (displayName == null) api.login(LoginRequest(username, password))
            else api.register(RegisterRequest(username, displayName.trim(), password))
        }
        if (result is ApiResult.Success) {
            try { sessions.save(StoredSession(settings.currentSettings.value.backendUrl, result.data)) }
            catch (_: Exception) { return@run ApiResult.Failure(AppError("You signed in, but the session could not be saved. Please sign in again.")) }
        }
        result
    }

    suspend fun logout(): ApiResult<Unit> = mutations.run {
        val token = sessions.state.value.session?.auth?.token
        val result = executor.executeUnit { api.logout() }
        // Keep a valid session on network failure so Logout can be retried and server revocation is confirmed.
        if (result is ApiResult.Success || (result is ApiResult.Failure && result.error.statusCode == 401)) sessions.clear(token)
        result
    }
}

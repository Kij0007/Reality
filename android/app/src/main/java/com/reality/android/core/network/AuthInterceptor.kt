package com.reality.android.core.network

import com.reality.android.data.repository.SessionStore
import com.reality.android.data.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class AuthInterceptor @Inject constructor(private val sessions: SessionStore, private val settings: SettingsRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        runBlocking { sessions.awaitLoaded() }
        val request = chain.request()
        val public = listOf("/api/auth/login", "/api/auth/register", "/api/health").any { request.url.encodedPath.endsWith(it) }
        val session = sessions.state.value.session
        val base = session?.backendUrl?.toHttpUrlOrNull()
        // A queued read may have resolved its URL before the server setting changed.
        // Check the destination itself, including its context path, before sending credentials.
        val sameDestination = base != null && request.url.scheme == base.scheme &&
            request.url.host == base.host && request.url.port == base.port && request.url.encodedPath.startsWith(base.encodedPath)
        val token = if (!public && sameDestination && AuthRules.usable(session, settings.currentSettings.value.backendUrl)) session?.auth?.token else null
        val secured = request.newBuilder().removeHeader("Authorization").apply {
            if (token != null) header("Authorization", "Bearer $token")
        }.build()
        val response = chain.proceed(secured)
        if (response.code == 401 && token != null) runBlocking {
            sessions.clear(token, "Your sign-in expired. Please sign in again.")
        }
        return response
    }
}

package com.reality.android.data

import com.reality.android.core.network.*
import com.reality.android.data.remote.api.AuthApi
import com.reality.android.data.remote.dto.*
import com.reality.android.data.repository.*
import io.mockk.*
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AuthIntegrationTest {
    private val user = UserDto(7, "alice", "Alice", "2026-10-10T00:00:00Z")
    private fun stored(url: String, expiry: String = "2099-01-01T00:00:00Z") = StoredSession(url, AuthDto("opaque-test-token", expiry, user))

    @Test fun validationMatchesUnicodeAndUtf8ServerLimits() {
        assertNotNull(AuthRules.validate("alice", "😀".repeat(6), "Alice"))
        assertNull(AuthRules.validate("alice", "😀".repeat(12), "😀".repeat(80)))
        assertNotNull(AuthRules.validate("alice", "😀".repeat(19), "Alice"))
        assertNotNull(AuthRules.validate("Al ice", "a".repeat(12)))
        assertNotNull(AuthRules.validate("alice", "a".repeat(12), "  "))
        assertNull(AuthRules.validate("alice", "  untrimmed password  "))
    }

    @Test fun sessionCannotCrossServersAndExpiryIsExclusive() {
        val now = Instant.parse("2026-10-10T00:00:00Z")
        assertTrue(AuthRules.usable(stored("https://a.example/"), "https://a.example/", now))
        assertFalse(AuthRules.usable(stored("https://a.example/"), "https://b.example/", now))
        assertFalse(AuthRules.usable(stored("https://a.example/", now.toString()), "https://a.example/", now))
        assertFalse(AuthRules.usable(stored("https://a.example/", "invalid"), "https://a.example/", now))
    }

    @Test fun bearerAndPublicRoutesUseExactContractAnd401ClearsOnlyMatchingSession() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            val url = server.url("/").toString()
            val sessions = mockk<SessionStore>()
            val settings = mockk<SettingsRepository>()
            every { sessions.state } returns MutableStateFlow(SessionState(true, stored(url)))
            every { settings.currentSettings } returns MutableStateFlow(AppSettings(backendUrl = url))
            coEvery { sessions.awaitLoaded() } just Runs
            coEvery { settings.awaitLoaded() } just Runs
            coEvery { sessions.clear(any(), any()) } just Runs
            val client = OkHttpClient.Builder().addInterceptor(BackendUrlInterceptor(settings)).addInterceptor(AuthInterceptor(sessions, settings)).build()
            val api = Retrofit.Builder().baseUrl("https://reality.invalid/").client(client)
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(AuthApi::class.java)
            server.enqueue(MockResponse().setBody(Json.encodeToString(user)).setHeader("Content-Type", "application/json"))
            assertEquals(user, api.me().body())
            val me = server.takeRequest()
            assertEquals("GET", me.method)
            assertEquals("/api/auth/me", me.path)
            assertEquals("Bearer opaque-test-token", me.getHeader("Authorization"))
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            api.login(LoginRequest("alice", "a".repeat(12)))
            val login = server.takeRequest()
            assertEquals("/api/auth/login", login.path)
            assertNull(login.getHeader("Authorization"))
            assertEquals(LoginRequest("alice", "a".repeat(12)), Json.decodeFromString<LoginRequest>(login.body.readUtf8()))
            coVerify(exactly = 0) { sessions.clear(any(), any()) }
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            api.me()
            coVerify(exactly = 1) { sessions.clear("opaque-test-token", any()) }
            server.takeRequest()
            server.enqueue(MockResponse().setResponseCode(201).setBody(Json.encodeToString(stored(url).auth)))
            api.register(RegisterRequest("alice", "Alice", "a".repeat(12)))
            val registration = server.takeRequest()
            assertEquals("POST", registration.method)
            assertEquals("/api/auth/register", registration.path)
            assertNull(registration.getHeader("Authorization"))
            server.enqueue(MockResponse().setResponseCode(204))
            api.logout()
            assertEquals("/api/auth/logout", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("{\"status\":\"UP\"}"))
            api.health()
            val health = server.takeRequest()
            assertEquals("/api/health", health.path)
            assertNull(health.getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test fun foreignServerNeverReceivesSavedToken() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            val sessions = mockk<SessionStore>()
            val settings = mockk<SettingsRepository>()
            val sessionState = MutableStateFlow(SessionState(true, stored("https://old.example/")))
            val configuration = MutableStateFlow(AppSettings(backendUrl = server.url("/").toString()))
            every { sessions.state } returns sessionState
            every { settings.currentSettings } returns configuration
            coEvery { sessions.awaitLoaded() } just Runs
            server.enqueue(MockResponse().setResponseCode(401))
            val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(sessions, settings)).build()
            client.newCall(okhttp3.Request.Builder().url(server.url("/activities")).build()).execute().use { assertEquals(401, it.code) }
            assertNull(server.takeRequest().getHeader("Authorization"))
            // A read was queued for A before settings and credentials switched to B.
            sessionState.value = SessionState(true, stored("https://new.example/context/"))
            configuration.value = AppSettings(backendUrl = "https://new.example/context/")
            server.enqueue(MockResponse().setResponseCode(401))
            client.newCall(okhttp3.Request.Builder().url(server.url("/activities")).build()).execute().close()
            assertNull(server.takeRequest().getHeader("Authorization"))
            // Context paths are credential boundaries even when origin is identical.
            val scopedUrl = server.url("/expected/").toString()
            sessionState.value = SessionState(true, stored(scopedUrl))
            configuration.value = AppSettings(backendUrl = scopedUrl)
            server.enqueue(MockResponse().setResponseCode(401))
            client.newCall(okhttp3.Request.Builder().url(server.url("/outside/activities")).build()).execute().close()
            assertNull(server.takeRequest().getHeader("Authorization"))
            coVerify(exactly = 0) { sessions.clear(any(), any()) }
        } finally { server.shutdown() }
    }

    @Test fun failedAccountWriteIsNeverAutomaticallyRetried() = runTest {
        val api = mockk<AuthApi>()
        val sessions = mockk<SessionStore>()
        val settings = mockk<SettingsRepository>()
        coEvery { api.health() } returns retrofit2.Response.success(HealthDto("UP"))
        coEvery { api.register(any()) } throws java.io.IOException("Disconnected")
        val auth = AuthRepository(api, ApiExecutor(Json), MutationGate(), sessions, settings)
        val result = auth.signIn("alice", "a".repeat(12), "Alice") { }
        assertTrue(result is ApiResult.Failure && result.error.uncertainWrite)
        coVerify(exactly = 1) { api.register(any()) }
        coVerify(exactly = 0) { sessions.save(any()) }
    }

    @Test fun wrongHealthResponseDoesNotSendAccountCredentials() = runTest {
        val api = mockk<AuthApi>()
        coEvery { api.health() } returns retrofit2.Response.success(HealthDto("WRONG_SERVER"))
        val auth = AuthRepository(api, ApiExecutor(Json), MutationGate(), mockk(), mockk())
        assertTrue(auth.signIn("alice", "a".repeat(12), null) { } is ApiResult.Failure)
        coVerify(exactly = 0) { api.login(any()) }
    }
}

package com.reality.android.data

import com.reality.android.core.network.invalidateSavedSession
import com.reality.android.data.remote.dto.*
import com.reality.android.data.repository.SessionState
import com.reality.android.data.repository.SessionStore
import io.mockk.*
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SessionLifetimeTest {
    private val now = Instant.parse("2026-10-10T00:00:00Z")
    private val url = "https://reality.example/"

    private fun store(expiresAt: String, backend: String = url): SessionStore {
        val sessions = mockk<SessionStore>()
        val stored = StoredSession(backend, AuthDto("old-token", expiresAt,
            UserDto(1, "alice", "Alice", "2026-10-01T00:00:00Z")))
        every { sessions.state } returns MutableStateFlow(SessionState(true, stored))
        coEvery { sessions.awaitLoaded() } just Runs
        coEvery { sessions.clear(any(), any()) } just Runs
        return sessions
    }

    @Test fun restoredSessionIsClearedAtExpiryEvenWithoutAnAuthScreen() = runTest {
        val sessions = store(now.toString())
        invalidateSavedSession(sessions, url, now)
        coVerify(exactly = 1) { sessions.clear("old-token", "Your sign-in expired. Please sign in again.") }
    }

    @Test fun validRestoredSessionStaysSignedIn() = runTest {
        val sessions = store(now.plusSeconds(1).toString())
        invalidateSavedSession(sessions, url, now)
        coVerify(exactly = 0) { sessions.clear(any(), any()) }
    }

    @Test fun differentBackendClearsOnlyThePreviousTokenWithoutClaimingExpiry() = runTest {
        val sessions = store(now.plusSeconds(3600).toString())
        invalidateSavedSession(sessions, "https://another.example/", now)
        coVerify(exactly = 1) { sessions.clear("old-token", null) }
    }

    @Test fun malformedSavedExpiryCannotLeaveTheApplicationSignedIn() = runTest {
        val sessions = store("malformed")
        invalidateSavedSession(sessions, url, now)
        coVerify(exactly = 1) { sessions.clear("old-token", any()) }
    }
}

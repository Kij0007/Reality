package com.reality.android.core.network

import com.reality.android.data.repository.SessionStore
import java.time.Instant

/** Conditional clearing prevents an expired request from removing a newer sign-in. */
internal suspend fun invalidateSavedSession(
    sessions: SessionStore,
    backendUrl: String,
    now: Instant = Instant.now(),
) {
    sessions.awaitLoaded()
    val session = sessions.state.value.session ?: return
    if (!AuthRules.usable(session, backendUrl, now)) {
        val notice = if (session.backendUrl == backendUrl) "Your sign-in expired. Please sign in again." else null
        sessions.clear(session.auth.token, notice)
    }
}

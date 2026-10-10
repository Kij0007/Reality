package com.reality.android.core.network

import com.reality.android.data.remote.dto.StoredSession
import java.time.Instant

object AuthRules {
    fun validate(username: String, password: String, displayName: String? = null): String? = when {
        !username.matches(Regex("[a-z0-9_.-]{3,40}")) -> "Use 3–40 lowercase letters, numbers, dots, underscores or hyphens for your username."
        password.codePointCount(0, password.length) < 12 -> "Your password must contain at least 12 characters."
        password.toByteArray(Charsets.UTF_8).size > 72 -> "Your password must be no longer than 72 UTF-8 bytes."
        displayName != null && (displayName.isBlank() || displayName.trim().let { it.codePointCount(0, it.length) } > 80) -> "Enter a display name of up to 80 characters."
        else -> null
    }

    fun usable(session: StoredSession?, backendUrl: String, now: Instant = Instant.now()): Boolean =
        session != null && session.backendUrl == backendUrl &&
            runCatching { Instant.parse(session.auth.expiresAt).isAfter(now) }.getOrDefault(false)
}

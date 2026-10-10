package com.reality.android.ui.tracking

import com.reality.android.data.remote.dto.BreakDto
import com.reality.android.data.remote.dto.SessionDto

enum class SessionStatus { ALL, RUNNING, BREAK, STOPPED, UNKNOWN }

/** An unavailable break read must never be interpreted as an empty, running state. */
fun sessionStatus(session: SessionDto, breaks: List<BreakDto>?): SessionStatus = when {
    session.endTime != null -> SessionStatus.STOPPED
    breaks == null -> SessionStatus.UNKNOWN
    breaks.any { it.endTime == null } -> SessionStatus.BREAK
    else -> SessionStatus.RUNNING
}

fun permitsBreak(session: SessionDto, breaks: List<BreakDto>?): Boolean =
    sessionStatus(session, breaks) == SessionStatus.RUNNING

fun permitsResume(session: SessionDto, breaks: List<BreakDto>?): Boolean =
    sessionStatus(session, breaks) == SessionStatus.BREAK

fun permitsStop(session: SessionDto): Boolean = session.endTime == null

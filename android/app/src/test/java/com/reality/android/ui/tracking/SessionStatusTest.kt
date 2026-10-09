package com.reality.android.ui.tracking

import com.reality.android.data.remote.dto.BreakDto
import com.reality.android.data.remote.dto.SessionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStatusTest {
    private val open = SessionDto(
        id = 7, activityId = 2, startTime = "2026-10-08T10:00:00",
        endTime = null, duration = null,
    )
    private val pause = BreakDto(
        id = 4, sessionId = 7, startTime = "2026-10-08T10:05:00",
        endTime = null, duration = null, description = null,
    )

    @Test fun missingBreakResponseIsUnknownAndNeverEnablesPauseOrResume() {
        assertEquals(SessionStatus.UNKNOWN, sessionStatus(open, null))
        assertFalse(permitsBreak(open, null))
        assertFalse(permitsResume(open, null))
        assertTrue(permitsStop(open))
    }

    @Test fun emptyConfirmedBreakListMeansRunning() {
        assertEquals(SessionStatus.RUNNING, sessionStatus(open, emptyList()))
        assertTrue(permitsBreak(open, emptyList()))
        assertFalse(permitsResume(open, emptyList()))
    }

    @Test fun openBreakEnablesResumeAndPreventsSecondBreak() {
        assertEquals(SessionStatus.BREAK, sessionStatus(open, listOf(pause)))
        assertFalse(permitsBreak(open, listOf(pause)))
        assertTrue(permitsResume(open, listOf(pause)))
    }

    @Test fun closedBreakAllowsAnotherBreak() {
        val closed = pause.copy(endTime = "2026-10-08T10:07:00", duration = 120)
        assertEquals(SessionStatus.RUNNING, sessionStatus(open, listOf(closed)))
        assertTrue(permitsBreak(open, listOf(closed)))
        assertFalse(permitsResume(open, listOf(closed)))
    }

    @Test fun stoppedSessionOverridesStaleOpenBreakAndDisablesAllTransitions() {
        val stopped = open.copy(endTime = "2026-10-08T10:10:00", duration = 480)
        assertEquals(SessionStatus.STOPPED, sessionStatus(stopped, listOf(pause)))
        assertFalse(permitsBreak(stopped, listOf(pause)))
        assertFalse(permitsResume(stopped, listOf(pause)))
        assertFalse(permitsStop(stopped))
    }
}

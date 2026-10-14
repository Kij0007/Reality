package com.reality.android.core

import com.reality.android.core.util.breakDisplaySeconds
import com.reality.android.data.remote.dto.BreakDto
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RealityBreakTimeTest {
    private val open = BreakDto(1, 2, "2026-10-05T09:10:00")

    @Test fun importedOpenBreakStopsAtItsFinishedSessionEvenDaysLater() {
        val later = Instant.parse("2026-10-30T00:00:00Z")
        assertEquals(600L, breakDisplaySeconds(open, "2026-10-05T09:20:00", "Asia/Kolkata", later))
        assertEquals(600L, breakDisplaySeconds(open, "2026-10-05T09:20:00", "Asia/Kolkata", later.plusSeconds(3600)))
    }

    @Test fun ongoingBreakAdvancesOnlyWhileBothBreakAndSessionRemainOpen() {
        val now = Instant.parse("2026-10-05T03:45:00Z")
        assertEquals(300L, breakDisplaySeconds(open, null, "Asia/Kolkata", now))
        assertEquals(360L, breakDisplaySeconds(open, null, "Asia/Kolkata", now.plusSeconds(60)))
    }

    @Test fun confirmedDurationIsRetainedAndMissingClosedDurationIsDerived() {
        val ended = open.copy(endTime = "2026-10-05T09:20:00", duration = 599)
        assertEquals(599L, breakDisplaySeconds(ended, "2026-10-05T09:30:00", "Asia/Kolkata"))
        assertEquals(600L, breakDisplaySeconds(ended.copy(duration = null), null, "Asia/Kolkata"))
    }

    @Test fun displayedBreakMatchesStorageLocalTimestampSemanticsAcrossOffsetChange() {
        val pause = open.copy(startTime = "2026-03-08T01:50:00")
        val now = Instant.parse("2026-03-08T07:10:00Z") // 03:10 in New York.
        assertEquals(4800L, breakDisplaySeconds(pause, null, "America/New_York", now))
        assertEquals(4800L, breakDisplaySeconds(pause, "2026-03-08T03:10:00", "America/New_York", now))
    }

    @Test fun invalidTimestampsAreUnavailableAndBackwardClockCannotShowNegativeDuration() {
        assertNull(breakDisplaySeconds(open.copy(startTime = "invalid"), null, "Asia/Kolkata"))
        assertNull(breakDisplaySeconds(open, null, "Invalid/Zone"))
        assertEquals(0L, breakDisplaySeconds(open, null, "Asia/Kolkata", Instant.parse("2026-10-05T03:30:00Z")))
    }
}


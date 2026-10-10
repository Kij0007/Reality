package com.reality.android.core

import com.reality.android.core.util.*
import com.reality.android.data.remote.dto.*
import com.reality.android.di.NetworkModule
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class RealityRulesTest {
    private val json = NetworkModule.json()

    @Test fun requestEnumsAndDateStringsMatchSpringJackson() {
        val request = ActivityRequest("Exercise", 30, ActivityCategory.FITNESS, setOf("MONDAY", "FRIDAY"), "2026-10-01")
        val encoded = json.encodeToString(request)
        assertTrue(encoded.contains("\"category\":\"FITNESS\""))
        assertTrue(encoded.contains("\"minimumDuration\":30"))
        assertTrue(encoded.contains("\"startDate\":\"2026-10-01\""))
        assertTrue(encoded.contains("\"MONDAY\""))
        assertFalse(encoded.contains("createdAt"))
        assertEquals(7, ActivityCategory.entries.size)
    }

    @Test fun nullableLegacyActivitiesAndNanosecondSessionTimestampsDecodeSafely() {
        val legacy = json.decodeFromString<ActivityDto>("""{"id":2,"name":null,"category":null,"minimumDuration":null,"active":true,"startDate":null,"scheduledDays":[],"createdAt":null,"unknown":true}""")
        assertNull(legacy.startDate)
        assertNull(legacy.category)
        assertFalse(isScheduled(legacy, LocalDate.parse("2026-10-05")))
        val session = json.decodeFromString<SessionDto>("""{"id":11,"activityId":2,"startTime":"2026-10-05T09:00:00.123456789","endTime":null,"duration":null}""")
        assertNull(session.duration)
        assertNotEquals("Unavailable", formatDateTime(session.startTime))
        assertEquals("Unavailable", formatDateTime("broken"))
    }

    @Test fun activityValidationRejectsInvalidDatesEnumsAndDurations() {
        val invalid = validateActivity(" ", "0", null, emptySet(), "2026-02-30")
        assertEquals(setOf("name", "minimumDuration", "category", "scheduledDays", "startDate"), invalid.keys)
        assertTrue(validateActivity("Read", "20", ActivityCategory.LEARNING, setOf("MONDAY"), "2026-10-01").isEmpty())
        assertTrue(validateActivity("Read", "2147483648", ActivityCategory.STUDY, setOf("Tuesday"), "2026-10-01").keys.containsAll(listOf("minimumDuration", "scheduledDays")))
    }

    @Test fun schedulingHonorsStartDateRepeatDaysAndSoftDeletion() {
        val activity = ActivityDto(id = 2, active = true, startDate = "2026-10-05", scheduledDays = setOf("MONDAY"))
        assertFalse(isScheduled(activity, LocalDate.parse("2026-09-28")))
        assertTrue(isScheduled(activity, LocalDate.parse("2026-10-05")))
        assertFalse(isScheduled(activity, LocalDate.parse("2026-10-06")))
        assertFalse(isScheduled(activity.copy(active = false), LocalDate.parse("2026-10-05")))
    }

    @Test fun runningTimerUsesServerTimezoneSubtractsBreaksAndFreezesDuringBreak() {
        val session = SessionDto(11, 2, "2026-10-05T09:00:00")
        val now = Instant.parse("2026-10-05T03:50:00Z") // 09:20 in Kolkata
        val pause = BreakDto(12, 11, "2026-10-05T09:10:00")
        assertEquals(1200L, liveWorkingSeconds(session, emptyList(), "Asia/Kolkata", now))
        assertEquals(600L, liveWorkingSeconds(session, listOf(pause), "Asia/Kolkata", now))
        assertEquals(600L, liveWorkingSeconds(session, listOf(pause), "Asia/Kolkata", now.plusSeconds(60)))
        assertEquals(900L, liveWorkingSeconds(session, listOf(pause.copy(endTime = "2026-10-05T09:15:00", duration = 300)), "Asia/Kolkata", now))
    }

    @Test fun stoppedSessionUsesAuthoritativeDurationAndCorruptOverlapCannotMakeNegativeTime() {
        val stopped = SessionDto(11, 2, "2026-10-05T09:00:00", "2026-10-05T09:20:00", 899)
        assertEquals(899L, liveWorkingSeconds(stopped, emptyList(), "UTC"))
        val running = stopped.copy(endTime = null, duration = null)
        val pause = BreakDto(12, 11, "2026-10-05T08:00:00")
        val overlap = BreakDto(13, 11, "2026-10-05T08:30:00")
        assertEquals(0L, liveWorkingSeconds(running, listOf(pause, overlap), "Asia/Kolkata", Instant.parse("2026-10-05T03:50:00Z")))
        assertEquals("0:00", formatDuration(-12))
        assertEquals("1:01:01", formatDuration(3661))
    }
}

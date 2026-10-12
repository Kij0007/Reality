package com.reality.android.data.local

import com.reality.android.data.remote.dto.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class RealityCalculationsTest {
    private val activity = ActivityDto(1, "Read", 1, ActivityCategory.LEARNING, true,
        setOf("MONDAY", "WEDNESDAY", "FRIDAY"), "2026-10-01")
    private fun session(id: Long, start: String, end: String?, activityId: Long = 1) =
        SessionDto(id, activityId, start, end)
    private fun data(vararg sessions: SessionDto, breaks: List<BreakDto> = emptyList(), target: ActivityDto = activity) =
        LocalSnapshot(listOf(target), sessions.toList(), breaks)

    @Test fun completionUsesMinuteTargetAndSecondSessionDurations() {
        val before = data(session(1, "2026-10-05T09:00", "2026-10-05T09:00:59"))
        assertFalse(RealityCalculations.progress(before, 1, LocalDate.parse("2026-10-05")).completed)
        val after = data(session(1, "2026-10-05T09:00", "2026-10-05T09:01"))
        val progress = RealityCalculations.progress(after, 1, LocalDate.parse("2026-10-05"))
        assertEquals(1, progress.minimumDuration)
        assertEquals(60L, progress.totalDuration)
        assertTrue(progress.completed)
    }

    @Test fun runningSessionsNeverAppearInDailyOrReportTotals() {
        val snapshot = data(session(1, "2026-10-05T09:00", null))
        assertEquals(0L, RealityCalculations.day(snapshot, 1, LocalDate.parse("2026-10-05")).totalDuration)
        val report = RealityCalculations.month(snapshot, 1, YearMonth.parse("2026-10"), LocalDate.parse("2026-10-06"))
        assertEquals(0L, report.totalDuration)
        assertEquals(0, report.totalSessions)
    }

    @Test fun crossingMidnightSubtractsOnlyBreakTimeInsideEachDay() {
        val snapshot = data(session(1, "2026-10-05T23:30", "2026-10-06T01:30"), breaks = listOf(
            BreakDto(1, 1, "2026-10-05T23:45", "2026-10-06T00:15", 1800),
            BreakDto(2, 1, "2026-10-06T01:00", "2026-10-06T01:10", 600),
        ))
        assertEquals(900L, RealityCalculations.day(snapshot, 1, LocalDate.parse("2026-10-05")).totalDuration)
        assertEquals(3900L, RealityCalculations.day(snapshot, 1, LocalDate.parse("2026-10-06")).totalDuration)
    }

    @Test fun exactMidnightEndpointsDoNotLeakIntoAdjacentDays() {
        val snapshot = data(session(1, "2026-10-05T23:59", "2026-10-06T00:00"),
            session(2, "2026-10-06T00:00", "2026-10-06T00:01"))
        val yesterday = RealityCalculations.day(snapshot, 1, LocalDate.parse("2026-10-05"))
        val today = RealityCalculations.day(snapshot, 1, LocalDate.parse("2026-10-06"))
        assertEquals(listOf(1L), yesterday.sessions.map { it.id })
        assertEquals(listOf(2L), today.sessions.map { it.id })
        assertEquals(60L, yesterday.totalDuration)
        assertEquals(60L, today.totalDuration)
    }

    @Test fun legacyOpenBreakOnFinishedSessionEndsAtSessionEndForOverlap() {
        val snapshot = data(session(1, "2026-10-05T09:00", "2026-10-05T09:20"),
            breaks = listOf(BreakDto(1, 1, "2026-10-05T09:10")))
        RealityCalculations.validateReferences(snapshot)
        assertEquals(600L, RealityCalculations.day(snapshot, 1, LocalDate.parse("2026-10-05")).totalDuration)
    }

    @Test fun currentStreakExcludesTodayAndSkipsUnscheduledDays() {
        val snapshot = data(session(1, "2026-10-02T09:00", "2026-10-02T09:01"),
            session(2, "2026-10-05T09:00", "2026-10-05T09:01"),
            session(3, "2026-10-07T09:00", "2026-10-07T09:01"))
        val streak = RealityCalculations.streak(snapshot, 1, LocalDate.parse("2026-10-07"))
        assertEquals(2, streak.currentStreak)
        assertEquals("2026-10-06", streak.lastEvaluatedDate)
    }

    @Test fun firstMissedScheduledDayBreaksStreakAndStartDateLimitsHistory() {
        val snapshot = data(session(1, "2026-10-02T09:00", "2026-10-02T09:01"))
        assertEquals(0, RealityCalculations.streak(snapshot, 1, LocalDate.parse("2026-10-07")).currentStreak)
        val recent = snapshot.copy(activities = listOf(activity.copy(startDate = "2026-10-07")))
        assertEquals(0, RealityCalculations.streak(recent, 1, LocalDate.parse("2026-10-07")).currentStreak)
    }

    @Test fun monthlyTimeIncludesTodayAndUnscheduledDaysButMetricsExcludeToday() {
        val snapshot = data(session(1, "2026-10-02T09:00", "2026-10-02T09:01"),
            session(2, "2026-10-04T09:00", "2026-10-04T09:02"),
            session(3, "2026-10-05T09:00", "2026-10-05T09:01"),
            session(4, "2026-10-07T09:00", "2026-10-07T09:01"))
        val report = RealityCalculations.month(snapshot, 1, YearMonth.parse("2026-10"), LocalDate.parse("2026-10-07"))
        assertEquals(300L, report.totalDuration)
        assertEquals(4, report.totalSessions)
        assertEquals(2, report.scheduledDays)
        assertEquals(2, report.completedDays)
        assertEquals(0, report.missedDays)
        assertEquals(100.0, report.completionPercentage, 0.0)
        assertEquals(2, report.currentStreak)
        assertEquals(2, report.longestStreak)
    }

    @Test fun reportLongestIsWithinMonthAndCurrentStreakIsCurrentOverall() {
        val snapshot = data(session(1, "2026-10-02T09:00", "2026-10-02T09:01"),
            session(2, "2026-10-05T09:00", "2026-10-05T09:01"))
        val future = RealityCalculations.month(snapshot, 1, YearMonth.parse("2026-11"), LocalDate.parse("2026-10-06"))
        assertEquals(0, future.longestStreak)
        assertEquals(0L, future.totalDuration)
        assertEquals(2, future.currentStreak)
    }

    @Test fun reportRoundsCompletionPercentageToTwoPlaces() {
        val snapshot = data(session(1, "2026-10-02T09:00", "2026-10-02T09:01"))
        val report = RealityCalculations.month(snapshot, 1, YearMonth.parse("2026-10"), LocalDate.parse("2026-10-08"))
        assertEquals(3, report.scheduledDays)
        assertEquals(1, report.completedDays)
        assertEquals(2, report.missedDays)
        assertEquals(33.33, report.completionPercentage, 0.0)
    }

    @Test fun inactiveActivityRetainsSessionHistoryButProgressRequiresActive() {
        val snapshot = data(session(1, "2026-10-05T09:00", "2026-10-05T09:01"), target = activity.copy(active = false))
        assertEquals(60L, RealityCalculations.day(snapshot, 1, LocalDate.parse("2026-10-05")).totalDuration)
        assertEquals(404, assertThrows(LocalDataException::class.java) {
            RealityCalculations.progress(snapshot, 1, LocalDate.parse("2026-10-05"))
        }.status)
    }

    @Test fun referencesRequireUniqueIdsAndRealParentsButAllowConcurrentSessions() {
        val valid = data(session(1, "2026-10-05T09:00", null), session(2, "2026-10-05T10:00", null))
        RealityCalculations.validateReferences(valid)
        assertThrows(LocalDataException::class.java) { RealityCalculations.validateReferences(valid.copy(sessions = valid.sessions + valid.sessions.first())) }
        assertThrows(LocalDataException::class.java) { RealityCalculations.validateReferences(valid.copy(activities = emptyList())) }
        assertThrows(LocalDataException::class.java) { RealityCalculations.validateReferences(valid.copy(breaks = listOf(BreakDto(1, 88, "2026-10-05T09:00")))) }
    }

    @Test fun obviousInvalidFormsAndBackwardPhoneClockAreRejected() {
        val request = ActivityRequest("Read", 1, ActivityCategory.STUDY, setOf("MONDAY"), "2026-10-01")
        RealityCalculations.validate(request)
        assertThrows(LocalDataException::class.java) { RealityCalculations.validate(request.copy(minimumDuration = 0)) }
        assertThrows(LocalDataException::class.java) { RealityCalculations.validate(request.copy(scheduledDays = setOf("Monday"))) }
        assertThrows(LocalDataException::class.java) { RealityCalculations.validate(request.copy(startDate = "2026-02-30")) }
        assertThrows(LocalDataException::class.java) {
            RealityCalculations.requireClockNotBefore(LocalDateTime.parse("2026-10-05T09:00"), listOf("2026-10-05T09:01"))
        }
    }
}

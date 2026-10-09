package com.reality.android.ui.dashboard

import com.reality.android.data.remote.dto.ActivityDto
import com.reality.android.data.remote.dto.DailyProgressDto
import com.reality.android.data.remote.dto.StreakDto
import com.reality.android.ui.progress.RemoteValue
import com.reality.android.ui.progress.hasCompleteSchedule
import org.junit.Assert.*
import org.junit.Test

class DashboardSummaryTest {
    private fun activity(id: Long = 1L, start: String? = "2026-10-01", days: Set<String>? = setOf("MONDAY")) =
        ActivityDto(
            id = id, name = "Read", minimumDuration = 20, category = null,
            active = true, scheduledDays = days, startDate = start, createdAt = null, updatedAt = null
        )

    @Test
    fun streakFailureDoesNotDiscardSuccessfulRecordedWork() {
        val rows = listOf(
            DashboardActivity(
                activity(),
                RemoteValue(DailyProgressDto(1L, "Read", "2026-10-09", 20, 1200L, true)),
                RemoteValue<StreakDto>(error = "Server could not calculate the streak")
            ),
            DashboardActivity(
                activity(2L),
                RemoteValue(DailyProgressDto(2L, "Walk", "2026-10-09", 10, 300L, false))
            )
        )
        assertEquals(1500L, dashboardRecordedSeconds(rows))
    }

    @Test
    fun failedDailyReadDoesNotMakeMissingWorkLookLikeZero() {
        val row = DashboardActivity(activity(), RemoteValue(error = "Connection refused"))
        assertNull(dashboardRecordedSeconds(listOf(row)))
    }

    @Test
    fun staleDailyValueIsNotIncludedInCurrentSummary() {
        val row = DashboardActivity(
            activity(),
            RemoteValue(DailyProgressDto(1L, "Read", "2026-10-09", 20, 1200L, true), "Timeout")
        )
        assertNull(dashboardRecordedSeconds(listOf(row)))
    }

    @Test
    fun successfullyLoadedEmptyListHasZeroRecordedWork() {
        assertEquals(0L, dashboardRecordedSeconds(emptyList()))
    }

    @Test
    fun legacyNullDateAndEmptyRepeatDaysRequireScheduleRepair() {
        assertFalse(hasCompleteSchedule(activity(start = null)))
        assertFalse(hasCompleteSchedule(activity(days = emptySet())))
        assertFalse(hasCompleteSchedule(activity(start = "2026-02-30")))
        assertTrue(hasCompleteSchedule(activity()))
    }
}

package com.reality.android.ui.dashboard

import com.reality.android.core.network.*
import com.reality.android.core.util.today
import com.reality.android.data.remote.dto.*
import com.reality.android.data.repository.*
import io.mockk.*
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val activities = mockk<ActivityRepository>()
    private val sessions = mockk<SessionRepository>()
    private val progress = mockk<ProgressRepository>()
    private val settings = mockk<SettingsRepository>()
    private val activity = ActivityDto(
        id = 1L, name = "Read", minimumDuration = 20, active = true,
        scheduledDays = setOf("MONDAY"), startDate = "2026-10-01"
    )

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        every { settings.settings } returns flowOf(AppSettings(backendUrl = "http://10.0.2.2:8081/"))
        coEvery { activities.list() } returns ApiResult.Success(listOf(activity))
        coEvery { sessions.list() } returns ApiResult.Success(emptyList())
        coEvery { progress.day(1L, any()) } coAnswers {
            ApiResult.Success(DailyProgressDto(1L, "Read", secondArg(), 20, 1200L, true))
        }
        coEvery { progress.streak(1L) } returns ApiResult.Failure(AppError("Streak could not be calculated.", 500))
    }

    @After fun finish() {
        unmockkStatic(::today)
        Dispatchers.resetMain()
    }

    @Test fun failedStreakLeavesSuccessfulDailyProgressVisible() = runTest(dispatcher) {
        val vm = DashboardViewModel(activities, sessions, progress, settings)
        advanceUntilIdle()
        assertFalse(vm.state.value.loading)
        assertEquals(1200L, dashboardRecordedSeconds(vm.state.value.rows))
        assertEquals("Streak could not be calculated.", vm.state.value.rows.single().streak.error)
        assertTrue(vm.state.value.rows.single().progress.isCurrent)
    }

    @Test fun failedActivityRefreshInvalidatesCachedDailyTotals() = runTest(dispatcher) {
        val vm = DashboardViewModel(activities, sessions, progress, settings)
        advanceUntilIdle()
        coEvery { activities.list() } returns ApiResult.Failure(AppError("Server unavailable."))
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1200L, vm.state.value.rows.single().progress.data?.totalDuration)
        assertFalse(vm.state.value.rows.single().progress.isCurrent)
        assertNull(dashboardRecordedSeconds(vm.state.value.rows))
        assertEquals("Server unavailable.", vm.state.value.activityError)
    }

    @Test fun failedReadAfterMidnightCannotPresentYesterdayAsToday() = runTest(dispatcher) {
        mockkStatic(::today)
        every { today(any()) } returns LocalDate.parse("2026-10-08")
        val vm = DashboardViewModel(activities, sessions, progress, settings)
        advanceUntilIdle()
        assertEquals(LocalDate.parse("2026-10-08"), vm.state.value.loadedDate)
        every { today(any()) } returns LocalDate.parse("2026-10-09")
        coEvery { activities.list() } returns ApiResult.Failure(AppError("Server unavailable."))
        vm.refresh()
        advanceUntilIdle()
        assertEquals(LocalDate.parse("2026-10-09"), vm.state.value.date)
        assertEquals(LocalDate.parse("2026-10-08"), vm.state.value.loadedDate)
        assertTrue(vm.state.value.rows.isEmpty())
        assertNotEquals(vm.state.value.date, vm.state.value.loadedDate)
    }

    @Test fun incompleteLegacyScheduleStillLoadsDailyProgressWithoutCallingBrokenStreak() = runTest(dispatcher) {
        coEvery { activities.list() } returns ApiResult.Success(listOf(activity.copy(startDate = null)))
        val vm = DashboardViewModel(activities, sessions, progress, settings)
        advanceUntilIdle()
        assertEquals(1200L, dashboardRecordedSeconds(vm.state.value.rows))
        coVerify(exactly = 0) { progress.streak(any()) }
    }
}

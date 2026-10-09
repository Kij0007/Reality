package com.reality.android.ui.reports

import androidx.lifecycle.SavedStateHandle
import com.reality.android.core.network.*
import com.reality.android.data.remote.dto.*
import com.reality.android.data.repository.*
import io.mockk.*
import java.time.YearMonth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val activities = mockk<ActivityRepository>()
    private val reports = mockk<ReportRepository>()
    private val settings = mockk<SettingsRepository>()
    private val handle = SavedStateHandle(mapOf("reportMonth" to "2026-10"))
    private val activity = ActivityDto(
        id = 1L, name = "Read", minimumDuration = 20, active = true,
        scheduledDays = setOf("MONDAY"), startDate = "2026-10-01"
    )

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        every { settings.settings } returns flowOf(AppSettings(backendUrl = "http://10.0.2.2:8081/"))
        coEvery { activities.list() } returns ApiResult.Success(listOf(activity))
        coEvery { reports.month(any(), any()) } coAnswers {
            val month = YearMonth.parse(secondArg<String>())
            ApiResult.Success(MonthlyReportDto(firstArg(), "Read", month.year, month.monthValue, 600L, 1, 1, 0, 1, 0.0, 3, 0))
        }
    }
    @After fun finish() { Dispatchers.resetMain() }

    @Test fun changedMonthUsesIsoYearMonthAndPreservesServerCurrentStreak() = runTest(dispatcher) {
        val vm = ReportsViewModel(activities, reports, settings, handle)
        advanceUntilIdle()
        vm.selectMonth(YearMonth.parse("2026-09"))
        advanceUntilIdle()
        assertEquals("2026-09", handle.get<String>("reportMonth"))
        assertEquals(9, vm.state.value.report.data?.month)
        assertEquals(3, vm.state.value.report.data?.currentStreak)
        coVerify { reports.month(1L, "2026-09") }
    }

    @Test fun incompleteScheduleDoesNotCallKnownBrokenReportEndpoint() = runTest(dispatcher) {
        coEvery { activities.list() } returns ApiResult.Success(listOf(activity.copy(startDate = null)))
        val vm = ReportsViewModel(activities, reports, settings, handle)
        advanceUntilIdle()
        assertNotNull(vm.state.value.selected)
        assertNull(vm.state.value.report.data)
        assertFalse(vm.state.value.loading)
        coVerify(exactly = 0) { reports.month(any(), any()) }
    }
}

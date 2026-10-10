package com.reality.android.ui.progress

import androidx.lifecycle.SavedStateHandle
import com.reality.android.core.network.*
import com.reality.android.data.remote.dto.*
import com.reality.android.data.repository.*
import io.mockk.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val activities = mockk<ActivityRepository>()
    private val sessions = mockk<SessionRepository>()
    private val progress = mockk<ProgressRepository>()
    private val settings = mockk<SettingsRepository>()
    private val handle = SavedStateHandle(mapOf("progressDate" to "2026-10-08"))
    private val activity = ActivityDto(
        id = 1L, name = "Read", minimumDuration = 20, active = true,
        scheduledDays = setOf("MONDAY"), startDate = "2026-10-01"
    )

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        every { settings.settings } returns flowOf(AppSettings(backendUrl = "http://10.0.2.2:8081/"))
        coEvery { activities.list() } returns ApiResult.Success(listOf(activity, activity.copy(id = 2L)))
        coEvery { progress.day(any(), any()) } coAnswers {
            ApiResult.Success(DailyProgressDto(firstArg(), "Read", secondArg(), 20, 600L, false))
        }
        coEvery { sessions.forDay(any(), any()) } coAnswers {
            ApiResult.Success(SessionDayDto(firstArg(), secondArg(), 600L, emptyList()))
        }
        coEvery { progress.streak(any()) } returns ApiResult.Failure(AppError("Streak unavailable.", 500))
    }
    @After fun finish() { Dispatchers.resetMain() }

    @Test fun streakFailureDoesNotHideDailyProgressOrEndedSessions() = runTest(dispatcher) {
        val vm = ProgressViewModel(activities, sessions, progress, settings, handle)
        advanceUntilIdle()
        assertTrue(vm.state.value.progress.isCurrent)
        assertTrue(vm.state.value.sessions.isCurrent)
        assertEquals("Streak unavailable.", vm.state.value.streak.error)
        assertFalse(vm.state.value.loading)
    }

    @Test fun selectedDateAndActivityAreSavedAndSentUsingExactPathValues() = runTest(dispatcher) {
        val vm = ProgressViewModel(activities, sessions, progress, settings, handle)
        advanceUntilIdle()
        vm.selectActivity(2L)
        vm.selectDate(LocalDate.parse("2026-09-30"))
        advanceUntilIdle()
        assertEquals(2L, vm.state.value.selectedId)
        assertEquals("2026-09-30", vm.state.value.progress.data?.date)
        assertEquals(2L, handle.get<Long>("progressActivityId"))
        assertEquals("2026-09-30", handle.get<String>("progressDate"))
        coVerify { progress.day(2L, "2026-09-30") }
        coVerify { sessions.forDay(2L, "2026-09-30") }
    }

    @Test fun changingDateCancelsOlderRequestWithoutOverwritingNewerResult() = runTest(dispatcher) {
        val oldResponse = CompletableDeferred<ApiResult<DailyProgressDto>>()
        coEvery { progress.day(1L, "2026-10-08") } coAnswers { oldResponse.await() }
        val vm = ProgressViewModel(activities, sessions, progress, settings, handle)
        runCurrent()
        assertTrue(vm.state.value.loading)
        vm.selectDate(LocalDate.parse("2026-10-09"))
        advanceUntilIdle()
        assertEquals("2026-10-09", vm.state.value.progress.data?.date)
        oldResponse.complete(ApiResult.Success(DailyProgressDto(1L, "Read", "2026-10-08", 20, 9999L, true)))
        advanceUntilIdle()
        assertEquals("2026-10-09", vm.state.value.progress.data?.date)
        assertEquals(600L, vm.state.value.progress.data?.totalDuration)
        assertFalse(vm.state.value.loading)
    }

    @Test fun backendChangeDoesNotRetainPreviousServerDataWhenNewConnectionFails() = runTest(dispatcher) {
        val stored = MutableStateFlow(AppSettings(backendUrl = "http://10.0.2.2:8081/"))
        every { settings.settings } returns stored
        val vm = ProgressViewModel(activities, sessions, progress, settings, handle)
        advanceUntilIdle()
        assertNotNull(vm.state.value.progress.data)
        coEvery { activities.list() } returns ApiResult.Failure(AppError("New server is unreachable."))
        stored.value = stored.value.copy(backendUrl = "https://other.example/")
        advanceUntilIdle()
        assertNull(vm.state.value.progress.data)
        assertNull(vm.state.value.sessions.data)
        assertNull(vm.state.value.streak.data)
        assertEquals("New server is unreachable.", vm.state.value.error)
    }
}

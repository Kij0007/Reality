package com.reality.android.ui.tracking

import androidx.lifecycle.SavedStateHandle
import com.reality.android.R
import com.reality.android.core.network.ApiResult
import com.reality.android.core.network.AppError
import com.reality.android.data.remote.dto.ActivityCategory
import com.reality.android.data.remote.dto.ActivityDto
import com.reality.android.data.remote.dto.BreakDto
import com.reality.android.data.remote.dto.SessionDayDto
import com.reality.android.data.remote.dto.SessionDto
import com.reality.android.data.repository.ActivityRepository
import com.reality.android.data.repository.AppSettings
import com.reality.android.data.repository.SessionRepository
import com.reality.android.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val activities = mockk<ActivityRepository>()
    private val sessions = mockk<SessionRepository>()
    private val settings = mockk<SettingsRepository>()
    private val activity = ActivityDto(id = 2, name = "Reading", minimumDuration = 15,
        category = ActivityCategory.STUDY, active = true)
    private val session = SessionDto(id = 7, activityId = 2, startTime = "2026-10-08T10:00:00")
    private var records = emptyList<SessionDto>()

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        every { settings.settings } returns flowOf(AppSettings())
        coEvery { activities.list() } returns ApiResult.Success(listOf(activity))
        coEvery { sessions.list() } coAnswers { ApiResult.Success(records) }
        coEvery { sessions.forActivity(2) } coAnswers { ApiResult.Success(records) }
        coEvery { sessions.breaks(7) } returns ApiResult.Success(emptyList())
    }
    @After fun finish() { Dispatchers.resetMain() }

    private fun viewModel() = TrackingViewModel(activities, sessions, settings, SavedStateHandle())

    @Test fun unknownBreakReadDisablesPauseAndResumeButConfirmedOpenSessionCanStop() = runTest(dispatcher) {
        records = listOf(session)
        coEvery { sessions.breaks(7) } returns ApiResult.Failure(AppError("Break data unavailable."))
        coEvery { sessions.stop(7) } coAnswers {
            val stopped = session.copy(endTime = "2026-10-08T10:10:00", duration = 600)
            records = listOf(stopped)
            ApiResult.Success(stopped)
        }
        val vm = viewModel()
        vm.load(null)
        runCurrent()
        assertEquals(SessionStatus.UNKNOWN, sessionStatus(session, vm.state.value.breaks[7]))
        assertEquals("Break data unavailable.", vm.state.value.breakErrors[7])
        vm.startBreak(session, null)
        vm.resume(session)
        runCurrent()
        coVerify(exactly = 0) { sessions.startBreak(any(), any()) }
        coVerify(exactly = 0) { sessions.resume(any()) }
        vm.stop(session)
        runCurrent()
        coVerify(exactly = 1) { sessions.stop(7) }
        assertEquals(R.string.wf_session_stopped, vm.state.value.notice)
    }

    @Test fun startRechecksServerAndFindsSessionStartedByAnotherClient() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load(2)
        runCurrent()
        assertTrue(vm.state.value.canStart)
        records = listOf(session)
        vm.start()
        runCurrent()
        coVerify(exactly = 0) { sessions.start(any()) }
        assertEquals(R.string.wf_session_already_open, vm.state.value.notice)
        assertFalse(vm.state.value.canStart)
    }

    @Test fun rapidStartTapsCreateOneSessionAndRefreshConfirmedServerRecord() = runTest(dispatcher) {
        val response = CompletableDeferred<ApiResult<SessionDto>>()
        coEvery { sessions.start(2) } coAnswers { response.await() }
        val vm = viewModel()
        vm.load(2)
        runCurrent()
        vm.start()
        vm.start()
        runCurrent()
        assertTrue(vm.state.value.busy)
        records = listOf(session)
        response.complete(ApiResult.Success(session))
        runCurrent()
        vm.start()
        runCurrent()
        coVerify(exactly = 1) { sessions.start(2) }
        assertEquals(listOf(session), vm.state.value.open)
        assertFalse(vm.state.value.busy)
    }

    @Test fun failedActivityRefreshPreservesVisibleDataButDisablesStart() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load(2)
        runCurrent()
        assertTrue(vm.state.value.canStart)
        coEvery { activities.list() } returns ApiResult.Failure(AppError("Activity refresh failed."))
        vm.refresh()
        runCurrent()
        assertEquals(listOf(activity), vm.state.value.activities)
        assertFalse(vm.state.value.activitiesKnown)
        assertFalse(vm.state.value.canStart)
        vm.start()
        runCurrent()
        coVerify(exactly = 0) { sessions.start(any()) }
    }

    @Test fun dayFilterUsesBackendClippedTotalAndExactDatePath() = runTest(dispatcher) {
        val stopped = session.copy(endTime = "2026-10-09T00:10:00", duration = 3600)
        coEvery { sessions.forDay(2, "2026-10-08") } returns ApiResult.Success(
            SessionDayDto(activityId = 2, date = "2026-10-08", totalDuration = 3000, sessions = listOf(stopped)),
        )
        val vm = viewModel()
        vm.load(2)
        runCurrent()
        vm.selectDate("2026-10-08")
        runCurrent()
        coVerify(exactly = 1) { sessions.forDay(2, "2026-10-08") }
        assertEquals(3000L, vm.state.value.dayTotal)
        assertEquals(listOf(stopped), vm.state.value.sessions)
    }

    @Test fun confirmedBreakSuccessIsKeptEvenWhenRefreshFailsAndCannotBeRepeated() = runTest(dispatcher) {
        records = listOf(session)
        val vm = viewModel()
        vm.load(null)
        runCurrent()
        coEvery { sessions.startBreak(7, "Lunch") } returns ApiResult.Success(BreakDto(
            id = 1, sessionId = 7, startTime = "2026-10-08T10:05:00", description = "Lunch",
        ))
        coEvery { sessions.list() } returns ApiResult.Failure(AppError("Refresh unavailable."))
        vm.startBreak(session, "Lunch")
        runCurrent()
        assertEquals(R.string.wf_break_started, vm.state.value.notice)
        assertEquals("Refresh unavailable.", vm.state.value.error)
        assertFalse(vm.state.value.sessionsKnown)
        assertFalse(vm.state.value.busy)
        vm.startBreak(session, "Lunch")
        runCurrent()
        coVerify(exactly = 1) { sessions.startBreak(7, "Lunch") }
    }
}

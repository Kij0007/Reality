package com.reality.android.ui.tracking

import com.reality.android.core.network.ApiResult
import com.reality.android.core.network.AppError
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
class SessionDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessions = mockk<SessionRepository>()
    private val activities = mockk<ActivityRepository>()
    private val settings = mockk<SettingsRepository>()
    private var record = SessionDto(id = 7, activityId = 2, startTime = "2026-10-08T10:00:00")

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        every { settings.settings } returns flowOf(AppSettings())
        coEvery { activities.list() } returns ApiResult.Success(emptyList())
        coEvery { sessions.get(7) } coAnswers { ApiResult.Success(record) }
        coEvery { sessions.breaks(7) } returns ApiResult.Success(emptyList())
    }
    @After fun finish() { Dispatchers.resetMain() }

    private fun viewModel() = SessionDetailViewModel(sessions, activities, settings)

    @Test fun repeatedStopTapsAreGatedAndFinalStateComesFromServer() = runTest(dispatcher) {
        val response = CompletableDeferred<ApiResult<SessionDto>>()
        coEvery { sessions.stop(7) } coAnswers { response.await() }
        val vm = viewModel()
        vm.load(7)
        runCurrent()
        vm.stop()
        vm.stop()
        runCurrent()
        assertTrue(vm.state.value.busy)
        record = record.copy(endTime = "2026-10-08T10:10:00", duration = 600)
        response.complete(ApiResult.Success(record))
        runCurrent()
        vm.stop()
        runCurrent()
        coVerify(exactly = 1) { sessions.stop(7) }
        assertEquals(record, vm.state.value.session)
        assertFalse(vm.state.value.busy)
    }

    @Test fun failedSessionReadPreventsActionsOnCachedRecord() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load(7)
        runCurrent()
        coEvery { sessions.get(7) } returns ApiResult.Failure(AppError("Session not found.", 404))
        vm.refresh()
        runCurrent()
        assertFalse(vm.state.value.sessionKnown)
        vm.stop()
        vm.delete()
        runCurrent()
        coVerify(exactly = 0) { sessions.stop(any()) }
        coVerify(exactly = 0) { sessions.delete(any()) }
    }

    @Test fun uncertainDeleteIsReconciledByConfirmedNotFound() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load(7)
        runCurrent()
        coEvery { sessions.delete(7) } returns ApiResult.Failure(AppError("Unknown outcome.", uncertainWrite = true))
        coEvery { sessions.get(7) } returns ApiResult.Failure(AppError("Session not found.", 404))
        vm.delete()
        runCurrent()
        assertTrue(vm.state.value.deleted)
        assertFalse(vm.state.value.busy)
        coVerify(exactly = 1) { sessions.delete(7) }
    }
}

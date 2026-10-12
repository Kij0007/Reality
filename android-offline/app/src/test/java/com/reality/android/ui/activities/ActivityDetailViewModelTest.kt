package com.reality.android.ui.activities

import com.reality.android.core.network.ApiResult
import com.reality.android.core.network.AppError
import com.reality.android.data.remote.dto.ActivityDto
import com.reality.android.data.remote.dto.SessionDto
import com.reality.android.data.repository.ActivityRepository
import com.reality.android.data.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val activities = mockk<ActivityRepository>()
    private val sessions = mockk<SessionRepository>()

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        coEvery { activities.get(2) } returns ApiResult.Success(ActivityDto(id = 2, name = "Reading", active = true))
        coEvery { sessions.forActivity(2) } returns ApiResult.Success(emptyList())
    }
    @After fun finish() { Dispatchers.resetMain() }

    @Test fun failedActivityRefreshKeepsCachedDisplayButDisablesStartAndDelete() = runTest(dispatcher) {
        val vm = ActivityDetailViewModel(activities, sessions)
        vm.load(2)
        runCurrent()
        assertTrue(vm.state.value.canStart)
        coEvery { activities.get(2) } returns ApiResult.Failure(AppError("Activity not found.", 404))
        vm.refresh()
        runCurrent()
        assertNotNull(vm.state.value.activity)
        assertTrue(vm.state.value.sessionsKnown)
        assertFalse(vm.state.value.activityKnown)
        assertFalse(vm.state.value.canStart)
        assertFalse(vm.state.value.canDelete)
        vm.start()
        vm.delete()
        runCurrent()
        coVerify(exactly = 0) { sessions.start(any()) }
        coVerify(exactly = 0) { activities.delete(any()) }
    }

    @Test fun deletionRechecksOpenSessionsBeforeSoftDeleting() = runTest(dispatcher) {
        val vm = ActivityDetailViewModel(activities, sessions)
        vm.load(2)
        runCurrent()
        assertTrue(vm.state.value.canDelete)
        coEvery { sessions.forActivity(2) } returns ApiResult.Success(listOf(SessionDto(
            id = 7, activityId = 2, startTime = "2026-10-08T10:00:00",
        )))
        vm.delete()
        runCurrent()
        coVerify(exactly = 0) { activities.delete(any()) }
        assertFalse(vm.state.value.canDelete)
        assertFalse(vm.state.value.busy)
    }
}

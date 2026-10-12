package com.reality.android.ui.activities

import androidx.lifecycle.SavedStateHandle
import com.reality.android.core.network.ApiResult
import com.reality.android.core.network.AppError
import com.reality.android.data.remote.dto.ActivityCategory
import com.reality.android.data.remote.dto.ActivityDto
import com.reality.android.data.remote.dto.ActivityRequest
import com.reality.android.data.repository.ActivityRepository
import com.reality.android.data.repository.AppSettings
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
class ActivityFormViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<ActivityRepository>()
    private val settings = mockk<SettingsRepository>()
    private val activity = ActivityDto(
        id = 12, name = "Reading", minimumDuration = 15, category = ActivityCategory.STUDY,
        active = true, scheduledDays = setOf("MONDAY"), startDate = "2026-10-01",
    )

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        every { settings.settings } returns flowOf(AppSettings())
    }
    @After fun finish() { Dispatchers.resetMain() }

    private fun completeForm(vm: ActivityFormViewModel) {
        vm.setName("  Reading  ")
        vm.setDuration(" 15 ")
        vm.setCategory(ActivityCategory.STUDY)
        vm.toggleDay("MONDAY")
        vm.setStartDate("2026-10-01")
    }

    @Test fun invalidFormDoesNotSubmitAndReportsRequiredFields() = runTest(dispatcher) {
        val vm = ActivityFormViewModel(repository, settings, SavedStateHandle())
        vm.load(null)
        runCurrent()
        vm.save()
        assertTrue(vm.state.value.fieldErrors.keys.containsAll(listOf(
            "name", "minimumDuration", "category", "scheduledDays",
        )))
        assertFalse(vm.state.value.saving)
        coVerify(exactly = 0) { repository.create(any()) }
        coVerify(exactly = 0) { repository.update(any(), any()) }
    }

    @Test fun repeatedSaveTapsDuringAndAfterConfirmationSendOneCreate() = runTest(dispatcher) {
        val response = CompletableDeferred<ApiResult<ActivityDto>>()
        coEvery { repository.create(any()) } coAnswers { response.await() }
        val vm = ActivityFormViewModel(repository, settings, SavedStateHandle())
        vm.load(null)
        runCurrent()
        completeForm(vm)
        vm.save()
        vm.save()
        runCurrent()
        assertTrue(vm.state.value.saving)
        response.complete(ApiResult.Success(activity))
        runCurrent()
        assertTrue(vm.state.value.saved)
        vm.save()
        runCurrent()
        coVerify(exactly = 1) { repository.create(ActivityRequest(
            name = "Reading", minimumDuration = 15, category = ActivityCategory.STUDY,
            scheduledDays = setOf("MONDAY"), startDate = "2026-10-01",
        )) }
    }

    @Test fun persistedEditDraftIsRestoredWithoutOverwritingFromServer() = runTest(dispatcher) {
        val draft = SavedStateHandle(mapOf(
            "formId" to "12", "hasDraft" to true, "name" to "New name",
            "duration" to "20", "category" to "LEARNING",
            "days" to arrayListOf("TUESDAY", "THURSDAY"), "startDate" to "2026-09-10",
        ))
        val vm = ActivityFormViewModel(repository, settings, draft)
        vm.load(12)
        runCurrent()
        assertEquals("New name", vm.state.value.name)
        assertEquals(setOf("TUESDAY", "THURSDAY"), vm.state.value.days)
        assertEquals(ActivityCategory.LEARNING, vm.state.value.category)
        assertTrue(vm.state.value.loaded)
        coVerify(exactly = 0) { repository.get(any()) }
    }

    @Test fun interruptedRequestRestoresAsUncertainAndCannotBeResubmitted() = runTest(dispatcher) {
        val draft = SavedStateHandle(mapOf(
            "formId" to "new", "hasDraft" to true, "name" to "Reading", "duration" to "15",
            "category" to "STUDY", "days" to arrayListOf("MONDAY"),
            "startDate" to "2026-10-01", "writePending" to true,
        ))
        val vm = ActivityFormViewModel(repository, settings, draft)
        vm.load(null)
        runCurrent()
        assertTrue(vm.state.value.uncertainWrite)
        vm.save()
        runCurrent()
        coVerify(exactly = 0) { repository.create(any()) }
    }

    @Test fun pendingFlagIsWrittenBeforeRequestAndClearedAfterConfirmedFailure() = runTest(dispatcher) {
        val response = CompletableDeferred<ApiResult<ActivityDto>>()
        coEvery { repository.create(any()) } coAnswers { response.await() }
        val handle = SavedStateHandle()
        val vm = ActivityFormViewModel(repository, settings, handle)
        vm.load(null)
        runCurrent()
        completeForm(vm)
        vm.save()
        assertEquals(true, handle.get<Boolean>("writePending"))
        runCurrent()
        response.complete(ApiResult.Failure(AppError("Minimum duration is invalid.", 400)))
        runCurrent()
        assertEquals(false, handle.get<Boolean>("writePending"))
        assertFalse(vm.state.value.uncertainWrite)
        assertFalse(vm.state.value.saving)
        assertEquals("Minimum duration is invalid.", vm.state.value.error)
        assertEquals("  Reading  ", vm.state.value.name)
    }

    @Test fun timeoutPreservesDraftAndBlocksBlindRetry() = runTest(dispatcher) {
        coEvery { repository.create(any()) } returns ApiResult.Failure(AppError(
            "Request outcome is unknown. Check the server before trying again.", uncertainWrite = true,
        ))
        val vm = ActivityFormViewModel(repository, settings, SavedStateHandle())
        vm.load(null)
        runCurrent()
        completeForm(vm)
        vm.save()
        runCurrent()
        assertTrue(vm.state.value.uncertainWrite)
        assertEquals("  Reading  ", vm.state.value.name)
        vm.save()
        runCurrent()
        coVerify(exactly = 1) { repository.create(any()) }
    }

    @Test fun failedInitialEditReadCannotSubmitEmptyReplacement() = runTest(dispatcher) {
        coEvery { repository.get(12) } returns ApiResult.Failure(AppError("Activity not found.", 404))
        val vm = ActivityFormViewModel(repository, settings, SavedStateHandle())
        vm.load(12)
        runCurrent()
        assertFalse(vm.state.value.loaded)
        completeForm(vm)
        vm.save()
        runCurrent()
        coVerify(exactly = 0) { repository.update(any(), any()) }
        assertEquals("Activity not found.", vm.state.value.error)
    }
}

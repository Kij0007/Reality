package com.reality.android.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.reality.android.R
import com.reality.android.core.network.*
import com.reality.android.data.repository.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val preferences = mockk<SettingsRepository>()
    private val activities = mockk<ActivityRepository>()
    private val stored = MutableStateFlow(AppSettings(backendUrl = "http://10.0.2.2:8081/"))
    private val gate = MutationGate()

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        every { preferences.settings } returns stored
    }
    @After fun finish() { Dispatchers.resetMain() }

    @Test fun restoredConnectionDraftSurvivesViewModelRecreation() = runTest(dispatcher) {
        val vm = SettingsViewModel(preferences, activities, gate,
            SavedStateHandle(mapOf("serverUrl" to "https://new.example/")))
        runCurrent()
        assertEquals("https://new.example/", vm.state.value.url)
        assertEquals(stored.value, vm.state.value.settings)
    }

    @Test fun unsavedServerIsNeverContacted() = runTest(dispatcher) {
        val vm = SettingsViewModel(preferences, activities, gate, SavedStateHandle())
        runCurrent()
        vm.changeUrl("https://new.example/")
        vm.testConnection()
        runCurrent()
        coVerify(exactly = 0) { activities.list() }
        assertEquals(R.string.settings_not_saved, vm.state.value.message)
    }

    @Test fun connectionFailureBecomesReadableStateAndClearsLoading() = runTest(dispatcher) {
        coEvery { activities.list() } returns ApiResult.Failure(AppError("Unable to connect."))
        val vm = SettingsViewModel(preferences, activities, gate, SavedStateHandle())
        runCurrent()
        vm.testConnection()
        runCurrent()
        assertEquals("Unable to connect.", vm.state.value.error)
        assertFalse(vm.state.value.testing)
    }

    @Test fun serverCannotChangeDuringAnInFlightWrite() = runTest(dispatcher) {
        val vm = SettingsViewModel(preferences, activities, gate, SavedStateHandle())
        runCurrent()
        val release = CompletableDeferred<Unit>()
        val write = launch { gate.run { release.await(); ApiResult.Success(Unit) } }
        runCurrent()
        vm.changeUrl("https://new.example/")
        vm.saveConnection()
        runCurrent()
        coVerify(exactly = 0) { preferences.saveBackendUrl(any()) }
        assertTrue(vm.state.value.mutationBusy)
        release.complete(Unit)
        write.join()
    }

    @Test fun invalidServerClockDoesNotReachPersistence() = runTest(dispatcher) {
        val vm = SettingsViewModel(preferences, activities, gate, SavedStateHandle())
        runCurrent()
        vm.changeZone("invalid/timezone")
        vm.saveZone()
        coVerify(exactly = 0) { preferences.saveServerZone(any()) }
        assertEquals(R.string.settings_zone_invalid, vm.state.value.message)
    }
}

package com.reality.android.ui.auth

import androidx.lifecycle.SavedStateHandle
import com.reality.android.core.network.*
import com.reality.android.data.remote.dto.AuthDto
import com.reality.android.data.remote.dto.UserDto
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
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = mockk<AuthRepository>()
    private val sessions = mockk<SessionStore>()
    private val settings = mockk<SettingsRepository>()
    private val gate = MutationGate()
    private val account = AuthDto("test-token", "2099-01-01T00:00:00Z",
        UserDto(1, "alice", "Alice", "2026-10-10T00:00:00Z"))

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        every { sessions.state } returns MutableStateFlow(SessionState(loaded = true))
        coEvery { sessions.awaitLoaded() } just Runs
        every { settings.settings } returns MutableStateFlow(AppSettings(backendUrl = "https://example.test/"))
    }

    @After fun finish() { Dispatchers.resetMain() }

    private fun model(saved: SavedStateHandle = SavedStateHandle()) = AuthViewModel(auth, sessions, settings, gate, saved)

    @Test fun interruptedAccountRequestRestoresGuidanceAndUsernameWithoutPassword() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("authUsername" to "alice", "authName" to "Alice", "authRequestPending" to true))
        val vm = model(saved)
        runCurrent()
        assertEquals("alice", vm.state.value.username)
        assertEquals("Alice", vm.state.value.displayName)
        assertEquals("", vm.state.value.password)
        assertFalse(vm.state.value.registering)
        assertFalse(vm.state.value.submitting)
        assertTrue(vm.state.value.error.orEmpty().contains("Try signing in before creating"))
        coVerify(exactly = 0) { auth.signIn(any(), any(), any(), any()) }
    }

    @Test fun validationRejectsInvalidInputBeforeCreatingPendingRequest() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = model(saved)
        runCurrent()
        vm.username("a")
        vm.password("a".repeat(12))
        vm.submit()
        runCurrent()
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.submitting)
        assertNull(saved.get<Boolean>("authRequestPending"))
        coVerify(exactly = 0) { auth.signIn(any(), any(), any(), any()) }
    }

    @Test fun rapidSubmissionsUseOneRequestAndNeverPersistThePassword() = runTest(dispatcher) {
        val response = CompletableDeferred<ApiResult<AuthDto>>()
        coEvery { auth.signIn(any(), any(), any(), any()) } coAnswers { response.await() }
        val saved = SavedStateHandle()
        val vm = model(saved)
        runCurrent()
        val password = "  untrimmed password  "
        vm.username(" Alice ")
        vm.password(password)
        vm.submit()
        vm.submit()
        runCurrent()
        assertTrue(vm.state.value.submitting)
        assertEquals(true, saved.get<Boolean>("authRequestPending"))
        assertFalse(saved.keys().any { it.contains("password", ignoreCase = true) })
        coVerify(exactly = 1) { auth.signIn("alice", password, null, any()) }
        response.complete(ApiResult.Success(account))
        runCurrent()
        assertFalse(vm.state.value.submitting)
        assertEquals("", vm.state.value.password)
        assertNull(saved.get<Boolean>("authRequestPending"))
    }

    @Test fun confirmedRejectionClearsPendingRequest() = runTest(dispatcher) {
        coEvery { auth.signIn(any(), any(), any(), any()) } returns
            ApiResult.Failure(AppError("Invalid username or password.", statusCode = 401))
        val saved = SavedStateHandle()
        val vm = model(saved)
        runCurrent()
        vm.username("alice")
        vm.password("a".repeat(12))
        vm.submit()
        runCurrent()
        assertNull(saved.get<Boolean>("authRequestPending"))
        assertEquals("Invalid username or password.", vm.state.value.error)
        assertFalse(vm.state.value.submitting)
    }

    @Test fun uncertainRegistrationRetainsRecoveryGuidanceForRecreation() = runTest(dispatcher) {
        coEvery { auth.signIn(any(), any(), any(), any()) } returns
            ApiResult.Failure(AppError("Unable to connect.", uncertainWrite = true))
        val saved = SavedStateHandle()
        val vm = model(saved)
        runCurrent()
        vm.username("alice")
        vm.password("a".repeat(12))
        vm.toggleRegistration()
        vm.displayName("Alice")
        vm.password("a".repeat(12))
        vm.submit()
        runCurrent()
        assertEquals(true, saved.get<Boolean>("authRequestPending"))
        assertTrue(vm.state.value.error.orEmpty().contains("Try signing in before creating"))
        val restored = model(saved)
        runCurrent()
        assertEquals("", restored.state.value.password)
        assertFalse(restored.state.value.registering)
        assertTrue(restored.state.value.error.orEmpty().contains("Try signing in before creating"))
    }

    @Test fun cancelledRequestPreservesPendingMarker() = runTest(dispatcher) {
        coEvery { auth.signIn(any(), any(), any(), any()) } throws CancellationException("Process interrupted")
        val saved = SavedStateHandle()
        val vm = model(saved)
        runCurrent()
        vm.username("alice")
        vm.password("a".repeat(12))
        vm.submit()
        runCurrent()
        assertEquals(true, saved.get<Boolean>("authRequestPending"))
        assertFalse(vm.state.value.submitting)
    }
}

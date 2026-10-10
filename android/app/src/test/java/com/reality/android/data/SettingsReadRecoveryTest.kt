package com.reality.android.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.reality.android.core.network.MutationGate
import com.reality.android.data.repository.retrySettingsReads
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsReadRecoveryTest {
    private val backend = stringPreferencesKey("backend_url")

    @Test fun failedStartupReadRecoversAndPublishesBeforeConfigurationUnlock() = runTest {
        val saved = preferencesOf(backend to "https://reality.example/")
        var attempts = 0
        val disk = flow {
            attempts++
            if (attempts == 1) throw IOException("Temporarily unavailable disk")
            emit(saved)
            awaitCancellation()
        }
        val current = MutableStateFlow(emptyPreferences())
        disk.retrySettingsReads().onEach { current.value = it }.launchIn(backgroundScope)
        runCurrent()
        assertEquals(1, attempts)
        assertNull(current.value[backend])
        val gate = MutationGate()
        val save = async {
            gate.changeConfiguration { current.first { it[backend] == saved[backend] } }
        }
        runCurrent()
        assertTrue(gate.busy.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(saved, save.await())
        assertFalse(gate.busy.value)
        assertEquals(2, attempts)
    }

    @Test fun laterReadFailuresRetainExistingBackendInsteadOfEmittingDefaults() = runTest {
        val original = preferencesOf(backend to "https://old.example/")
        val updated = preferencesOf(backend to "https://new.example/")
        var attempts = 0
        val snapshots = mutableListOf<String?>()
        val disk = flow {
            attempts++
            if (attempts == 1) {
                emit(original)
                throw IOException("Read failure after initial settings")
            }
            emit(updated)
            awaitCancellation()
        }
        disk.retrySettingsReads().onEach { snapshots += it[backend] }.launchIn(backgroundScope)
        runCurrent()
        assertEquals(listOf("https://old.example/"), snapshots)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("https://old.example/", "https://new.example/"), snapshots)
    }
}

package com.reality.android.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reality.android.data.remote.dto.*
import com.reality.android.data.repository.SessionStore
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionPersistenceTest {
    @Test fun keystoreEncryptedSessionRestoresAndConditionalClearPreservesNewSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SessionStore(context, Json)
        val token = "encrypted-instrumentation-only-test-token-43x"
        val session = StoredSession("https://session-test.invalid/", AuthDto(token, "2099-01-01T00:00:00Z",
            UserDto(1, "test_user", "Test account", "2026-10-10T00:00:00Z")))
        try {
            store.awaitLoaded()
            store.save(session)
            assertEquals(session, store.state.value.session)
            val disk = File(context.filesDir, "datastore/reality_session.preferences_pb").readBytes().toString(Charsets.ISO_8859_1)
            assertFalse("Bearer token must never be persisted in plaintext", disk.contains(token))
            assertFalse("Session metadata is encrypted with the token", disk.contains("test_user"))
            val restored = SessionStore(context, Json)
            restored.awaitLoaded()
            assertEquals(session, restored.state.value.session)
            store.clear("a-token-from-an-old-request")
            assertEquals(session, store.state.value.session)
            store.clear(token)
            assertNull(store.state.value.session)
            val cleared = SessionStore(context, Json)
            cleared.awaitLoaded()
            assertNull(cleared.state.value.session)
        } finally { store.clear() }
    }
}

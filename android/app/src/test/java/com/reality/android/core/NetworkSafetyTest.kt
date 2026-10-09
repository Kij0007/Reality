package com.reality.android.core

import com.reality.android.core.network.*
import com.reality.android.di.NetworkModule
import java.net.SocketTimeoutException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkSafetyTest {
    @Test fun releaseRejectsHttpCredentialsQueriesAndFragments() {
        listOf("http://example.com", "https://user:password@example.com/", "https://example.com/?x=1", "https://example.com/#screen").forEach { url ->
            assertTrue(runCatching { normalizeBackendUrl(url, false) }.isFailure)
        }
        assertEquals("https://example.com/context/", normalizeBackendUrl(" https://example.com/context ", false))
        assertEquals("http://10.0.2.2:8081/", normalizeBackendUrl("http://10.0.2.2:8081", true))
    }

    @Test fun addressRewriteRetainsContextAndEncodedRequestPath() {
        val original = "https://reality.invalid/api/sessions/activity/7/date/2026-10-05?x=a%2Fb".toHttpUrl()
        assertEquals("https://example.com/reality/api/sessions/activity/7/date/2026-10-05?x=a%2Fb",
            resolveBackendUrl("https://example.com/reality/", original).toString())
    }

    @Test fun cancellationIsRethrownAndAmbiguousWritesAreMarked() = runTest {
        val executor = ApiExecutor(NetworkModule.json())
        val read = executor.execute<String> { throw SocketTimeoutException() } as ApiResult.Failure
        assertFalse(read.error.uncertainWrite)
        val write = executor.execute<String>(mutation = true) { throw SocketTimeoutException() } as ApiResult.Failure
        assertTrue(write.error.uncertainWrite)
        assertTrue(write.error.message.contains("Refresh before trying again"))
        try {
            executor.execute<String> { throw CancellationException("navigation") }
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { /* expected */ }
    }

    @Test fun simultaneousWritesAndAddressChangesAreRejected() = runTest {
        val gate = MutationGate()
        val finish = CompletableDeferred<Unit>()
        val first = async { gate.run { finish.await(); ApiResult.Success("saved") } }
        runCurrent()
        assertTrue(gate.busy.value)
        var duplicateCalled = false
        assertTrue(gate.run { duplicateCalled = true; ApiResult.Success(Unit) } is ApiResult.Failure)
        assertFalse(duplicateCalled)
        assertTrue(runCatching { gate.changeConfiguration { "new server" } }.isFailure)
        finish.complete(Unit)
        assertEquals(ApiResult.Success("saved"), first.await())
        assertFalse(gate.busy.value)
    }

    @Test fun configurationPublicationHoldsTheGateAndCancellationReleasesWrites() = runTest {
        val gate = MutationGate()
        val published = CompletableDeferred<Unit>()
        val save = async { gate.changeConfiguration { published.await() } }
        runCurrent()
        assertTrue(gate.busy.value)
        assertTrue(gate.run { ApiResult.Success(Unit) } is ApiResult.Failure)
        published.complete(Unit)
        save.await()
        val waitingWrite = launch { gate.run<Nothing> { awaitCancellation() } }
        runCurrent()
        assertTrue(gate.busy.value)
        waitingWrite.cancelAndJoin()
        assertFalse(gate.busy.value)
        assertEquals(ApiResult.Success(Unit), gate.run { ApiResult.Success(Unit) })
    }
}

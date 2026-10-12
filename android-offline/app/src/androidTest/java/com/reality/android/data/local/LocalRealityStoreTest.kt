package com.reality.android.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reality.android.data.remote.dto.ActivityCategory
import com.reality.android.data.remote.dto.ActivityRequest
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalRealityStoreTest {
    private lateinit var database: RealityDatabase
    private lateinit var store: LocalRealityStore
    private val time = LocalDateTime.parse("2026-10-05T09:00")
    private val request = ActivityRequest("Read", 1, ActivityCategory.STUDY, setOf("MONDAY"), "2026-10-01")

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RealityDatabase::class.java).build()
        store = LocalRealityStore(database)
    }
    @After fun close() { database.close() }

    @Test fun trackingTransitionsCommitBreaksAndActiveDuration() = runBlocking {
        val activity = store.createActivity(request, time)
        val session = store.startSession(activity.id, time)
        store.startBreak(session.id, "Tea", time.plusMinutes(1))
        assertThrowsSuspend { store.startBreak(session.id, null, time.plusMinutes(2)) }
        store.resume(session.id, time.plusMinutes(3))
        store.startBreak(session.id, null, time.plusMinutes(4))
        val stopped = store.stopSession(session.id, time.plusMinutes(6))
        assertEquals(120L, stopped.duration)
        assertTrue(store.breaks(session.id).all { it.endTime != null })
        assertEquals(listOf(120L, 120L), store.breaks(session.id).map { it.duration })
        assertThrowsSuspend { store.stopSession(session.id, time.plusMinutes(7)) }
        assertThrowsSuspend { store.resume(session.id, time.plusMinutes(7)) }
    }

    @Test fun softDeletionKeepsHistoryAndSessionDeleteCascadesBreaks() = runBlocking {
        val activity = store.createActivity(request, time)
        val session = store.startSession(activity.id, time)
        store.startBreak(session.id, null, time.plusMinutes(1))
        store.deleteActivity(activity.id, time.plusMinutes(2))
        assertTrue(store.activities().isEmpty())
        assertEquals(false, store.snapshot().activities.single().active)
        assertEquals(session.id, store.sessionsForActivity(activity.id).single().id)
        assertThrowsSuspend { store.startSession(activity.id, time.plusMinutes(3)) }
        store.resume(session.id, time.plusMinutes(3))
        store.stopSession(session.id, time.plusMinutes(4))
        store.deleteSession(session.id)
        assertTrue(store.snapshot().sessions.isEmpty())
        assertTrue(store.snapshot().breaks.isEmpty())
    }

    @Test fun invalidRestorePreservesDataTimezoneAndRevision() = runBlocking {
        val activity = store.createActivity(request, time)
        val session = store.startSession(activity.id, time)
        val snapshot = store.snapshot()
        assertThrowsSuspend { store.replaceAll(snapshot.copy(activities = emptyList()), "UTC") }
        assertEquals(snapshot, store.snapshot())
        assertEquals("Asia/Kolkata", store.timezone())
        assertEquals(0L, store.revision.first())
        assertEquals(session.id, store.sessions().single().id)
        assertThrowsSuspend { store.replaceAll(snapshot, "Invalid/Zone") }
        assertEquals(snapshot, store.snapshot())
        assertEquals("Asia/Kolkata", store.timezone())
        assertEquals(0L, store.revision.first())
    }

    @Test fun restoredIdsTimezoneAndRevisionCommitTogetherAndNewIdsDoNotCollide() = runBlocking {
        val activity = store.createActivity(request, time)
        val session = store.startSession(activity.id, time)
        store.startBreak(session.id, "Pause", time.plusMinutes(1))
        val snapshot = store.snapshot()
        store.replaceAll(snapshot, "UTC")
        assertEquals(snapshot, store.snapshotWithZone().first)
        assertEquals("UTC", store.snapshotWithZone().second)
        assertEquals(1L, store.revision.first())
        val next = store.createActivity(request.copy(name = "Walk"), time)
        assertTrue(next.id > activity.id)
        assertEquals(2, store.snapshot().activities.size)
    }

    @Test fun databaseSurvivesCloseAndReopenWithoutServer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "offline-persistence-test.db"
        context.deleteDatabase(name)
        try {
            val first = Room.databaseBuilder(context, RealityDatabase::class.java, name).build()
            val firstStore = LocalRealityStore(first)
            val activity = firstStore.createActivity(request, time)
            val session = firstStore.startSession(activity.id, time)
            firstStore.startBreak(session.id, "Persisted", time.plusMinutes(1))
            first.close()
            val reopened = Room.databaseBuilder(context, RealityDatabase::class.java, name).build()
            try {
                val restored = LocalRealityStore(reopened).snapshot()
                assertEquals(activity.id, restored.activities.single().id)
                assertEquals(session.id, restored.sessions.single().id)
                assertEquals("Persisted", restored.breaks.single().description)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    private suspend fun assertThrowsSuspend(action: suspend () -> Any?) {
        var thrown = false
        try { action() } catch (_: IllegalArgumentException) { thrown = true }
        assertTrue("Expected invalid state/data to be rejected", thrown)
    }
}


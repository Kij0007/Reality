package com.reality.android.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reality.android.core.network.ApiResult
import com.reality.android.core.network.MutationGate
import com.reality.android.data.local.LocalRealityStore
import com.reality.android.data.local.RealityDatabase
import com.reality.android.data.remote.dto.ActivityCategory
import com.reality.android.data.remote.dto.ActivityRequest
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    @Test fun documentRoundTripPreservesArchivedRowsBreaksIdsAndClock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, RealityDatabase::class.java).build()
        val file = File.createTempFile("reality-backup-", ".json", context.cacheDir)
        try {
            val store = LocalRealityStore(database)
            val backup = BackupRepository(context, store, MutationGate())
            val time = LocalDateTime.parse("2026-10-05T09:00:00")
            val activity = store.createActivity(ActivityRequest("Read", 1, ActivityCategory.STUDY,
                setOf("MONDAY"), "2026-10-01"), time)
            val session = store.startSession(activity.id, time)
            store.startBreak(session.id, "Tea", time.plusMinutes(1))
            store.stopSession(session.id, time.plusMinutes(3))
            store.deleteActivity(activity.id, time.plusMinutes(4))
            database.realityDao().setTimezone("UTC")
            val expected = store.snapshot()
            assertTrue(backup.export(Uri.fromFile(file)) is ApiResult.Success)
            val decoded = backup.inspect(Uri.fromFile(file))
            assertTrue(decoded is ApiResult.Success)
            val document = (decoded as ApiResult.Success).data
            assertEquals("UTC", document.timeZone)
            assertEquals(expected, document.snapshot())
            store.deleteSession(session.id)
            database.realityDao().setTimezone("Asia/Kolkata")
            assertTrue(backup.restore(document) is ApiResult.Success)
            assertEquals(expected, store.snapshot())
            assertEquals("UTC", store.timezone())
            assertTrue(store.activities().isEmpty())
            file.writeText("{\"schemaVersion\":999}")
            assertTrue(backup.inspect(Uri.fromFile(file)) is ApiResult.Failure)
            assertEquals(expected, store.snapshot())
        } finally {
            database.close()
            file.delete()
        }
    }
}

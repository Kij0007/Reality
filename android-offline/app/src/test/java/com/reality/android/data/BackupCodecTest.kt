package com.reality.android.data

import com.reality.android.data.backup.BackupCodec
import com.reality.android.data.backup.RealityBackup
import com.reality.android.data.local.RealityCalculations
import com.reality.android.data.remote.dto.*
import java.io.ByteArrayInputStream
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val archived = ActivityDto(2, "Read", 20, ActivityCategory.LEARNING, false,
        setOf("MONDAY", "WEDNESDAY"), "2026-09-01", "2026-09-01T10:00:00", "2026-10-01T10:00:00")
    private val active = archived.copy(id = 7, name = "Study", active = true)
    private val finished = SessionDto(4, 2, "2026-10-01T10:00:00", "2026-10-01T11:00:00", 3000)
    private val running = SessionDto(8, 7, "2026-10-13T09:00:00")
    private fun backup() = RealityBackup(
        exportedAt = "2026-10-13T10:00:00Z", timeZone = "Asia/Kolkata", source = "spring-boot-postgresql",
        activities = listOf(archived, active), sessions = listOf(finished, running),
        breaks = listOf(BreakDto(5, 4, "2026-10-01T10:10:00", "2026-10-01T10:20:00", 600, "Tea"),
            BreakDto(9, 8, "2026-10-13T09:30:00")),
        streaks = listOf(StreakDto(7, "Study", 9, "2026-10-12")),
    )

    @Test fun roundTripPreservesAllHistoryAndServerIds() {
        val expected = backup()
        val actual = BackupCodec.read(ByteArrayInputStream(BackupCodec.encode(expected)))
        assertEquals(expected, actual)
        assertFalse(actual.activities.first().active == true)
        assertEquals(9, actual.streaks.single().currentStreak)
        assertNull(actual.sessions.last().endTime)
        assertNull(actual.breaks.last().duration)
    }

    @Test fun emptyDatabaseIsAValidPortableBackup() {
        val empty = backup().copy(activities = emptyList(), sessions = emptyList(), breaks = emptyList(), streaks = emptyList())
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty).decodeToString()))
    }

    @Test fun originalMultipleRunningSessionsArePreserved() {
        val value = backup().copy(sessions = backup().sessions + running.copy(id = 11))
        assertEquals(3, BackupCodec.decode(BackupCodec.encode(value).decodeToString()).sessions.size)
    }

    @Test fun historicalOpenBreakOnFinishedSessionIsPreserved() {
        val value = backup().copy(breaks = listOf(backup().breaks.first().copy(endTime = null, duration = null)))
        assertNull(BackupCodec.decode(BackupCodec.encode(value).decodeToString()).breaks.single().endTime)
    }

    @Test fun editedFinishedSessionKeepsOutOfSessionBreakHistoryExactlyWithoutDeductingIt() {
        val value = backup().copy(breaks = listOf(
            BreakDto(5, 4, "2026-10-01T09:40:00", "2026-10-01T09:50:00", 600, "Before edited session"),
            BreakDto(6, 4, "2026-10-01T11:30:00", "2026-10-01T11:40:00", 600, "After edited session"),
        ))
        val actual = BackupCodec.read(ByteArrayInputStream(BackupCodec.encode(value)))
        assertEquals(value, actual)
        assertEquals(3600L, RealityCalculations.day(actual.snapshot(), 2, LocalDate.parse("2026-10-01")).totalDuration)
    }

    @Test fun editedFinishedSessionDeductsOnlyTheOverlappingPartOfHistoricalBreaks() {
        val value = backup().copy(breaks = listOf(
            BreakDto(5, 4, "2026-10-01T09:55:00", "2026-10-01T10:05:00", 600),
            BreakDto(6, 4, "2026-10-01T10:55:00", "2026-10-01T11:05:00", 600),
        ))
        val actual = BackupCodec.read(ByteArrayInputStream(BackupCodec.encode(value)))
        assertEquals(value, actual)
        assertEquals(3000L, RealityCalculations.day(actual.snapshot(), 2, LocalDate.parse("2026-10-01")).totalDuration)
    }

    @Test fun unfinishedSessionStillRejectsBreaksBeforeItsStart() {
        rejected { BackupCodec.validate(backup().copy(breaks = listOf(
            BreakDto(9, 8, "2026-10-13T08:50:00", "2026-10-13T09:00:00", 600),
        ))) }
        rejected { BackupCodec.validate(backup().copy(breaks = listOf(
            BreakDto(9, 8, "2026-10-13T08:50:00"),
        ))) }
    }

    @Test fun finishedHistoryStillRejectsReversedBreakTimesAndNegativeDurations() {
        rejected { BackupCodec.validate(backup().copy(breaks = listOf(
            BreakDto(5, 4, "2026-10-01T11:40:00", "2026-10-01T11:30:00", 600),
        ))) }
        rejected { BackupCodec.validate(backup().copy(breaks = listOf(
            BreakDto(5, 4, "2026-10-01T11:30:00", "2026-10-01T11:40:00", -1),
        ))) }
    }

    @Test fun originalArchivedActivitiesWithoutScheduleMetadataArePreserved() {
        val value = backup().copy(activities = listOf(archived.copy(startDate = null, scheduledDays = null, category = null), active))
        val actual = BackupCodec.decode(BackupCodec.encode(value).decodeToString())
        assertNull(actual.activities.first().startDate)
        assertNull(actual.activities.first().scheduledDays)
        assertNull(actual.activities.first().category)
    }

    @Test fun unsupportedVersionAndForeignFormatAreRejected() {
        rejected { BackupCodec.validate(backup().copy(schemaVersion = 2)) }
        rejected { BackupCodec.validate(backup().copy(app = "Other")) }
        rejected { BackupCodec.decode("{\"activities\":[]}") }
    }

    @Test fun brokenParentsAndDuplicateIdsAreRejectedBeforeRestore() {
        rejected { BackupCodec.validate(backup().copy(activities = listOf(active))) }
        rejected { BackupCodec.validate(backup().copy(sessions = listOf(running))) }
        rejected { BackupCodec.validate(backup().copy(activities = backup().activities + active)) }
        rejected { BackupCodec.validate(backup().copy(breaks = backup().breaks + backup().breaks.first())) }
    }

    @Test fun invalidDatesDaysEnumsAndDurationsAreRejected() {
        rejected { BackupCodec.validate(backup().copy(timeZone = "Invalid/Zone")) }
        rejected { BackupCodec.validate(backup().copy(exportedAt = "not an instant")) }
        rejected { BackupCodec.validate(backup().copy(activities = listOf(archived.copy(scheduledDays = setOf("Today")), active))) }
        rejected { BackupCodec.validate(backup().copy(sessions = listOf(finished.copy(duration = -1), running))) }
        rejected { BackupCodec.decode(BackupCodec.encode(backup()).decodeToString().replace("LEARNING", "FAKE")) }
    }

    @Test fun reversedTimesAndOverlappingBreaksAreRejected() {
        rejected { BackupCodec.validate(backup().copy(sessions = listOf(finished.copy(endTime = "2026-09-30T10:00:00"), running))) }
        rejected { BackupCodec.validate(backup().copy(breaks = listOf(backup().breaks.first(),
            BreakDto(22, 4, "2026-10-01T10:15:00", "2026-10-01T10:25:00", 600)))) }
    }

    @Test fun malformedUtf8UnknownFieldsAndOversizedFilesAreRejected() {
        rejected { BackupCodec.read(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))) }
        rejected { BackupCodec.decode(BackupCodec.encode(backup()).decodeToString().replace("\"app\":", "\"password\":\"unsafe\",\"app\":")) }
        rejected { BackupCodec.read(ByteArrayInputStream(ByteArray(BackupCodec.MAX_BYTES + 1))) }
    }

    @Test fun staleOrDuplicatedStreakSnapshotIsRejected() {
        rejected { BackupCodec.validate(backup().copy(streaks = listOf(StreakDto(7, "Study", 9, "2026-10-11")))) }
        rejected { BackupCodec.validate(backup().copy(streaks = backup().streaks + backup().streaks)) }
    }

    private fun rejected(action: () -> Unit) {
        try { action(); fail("Invalid history must be rejected before replacing phone data") }
        catch (expected: Exception) { /* validation and strict UTF-8 errors are both expected */ }
    }
}

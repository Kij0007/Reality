package com.reality.android.data.backup

import com.reality.android.data.local.LocalSnapshot
import com.reality.android.data.remote.dto.ActivityDto
import com.reality.android.data.remote.dto.BreakDto
import com.reality.android.data.remote.dto.SessionDto
import com.reality.android.data.remote.dto.StreakDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Portable history, deliberately excluding passwords, account tokens and database credentials. */
@Serializable
data class RealityBackup(
    val app: String = "Reality",
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val timeZone: String,
    val source: String = "android-offline",
    val activities: List<ActivityDto>,
    val sessions: List<SessionDto>,
    val breaks: List<BreakDto>,
    // Historical comparison only. Actual streaks are recalculated from imported session history.
    val streaks: List<StreakDto> = emptyList(),
) {
    fun snapshot() = LocalSnapshot(activities, sessions, breaks)
}

object BackupCodec {
    const val MAX_BYTES = 20 * 1024 * 1024
    const val MAX_RECORDS = 200_000
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    fun encode(backup: RealityBackup): ByteArray {
        validate(backup)
        val bytes = json.encodeToString(backup).toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "This backup exceeds the 20 MB safety limit." }
        return bytes
    }

    fun read(input: InputStream): RealityBackup {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= MAX_BYTES) { "Choose a Reality JSON backup smaller than 20 MB." }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        require(bytes.size <= MAX_BYTES) { "Choose a Reality JSON backup smaller than 20 MB." }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return decode(decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString())
    }

    fun decode(text: String): RealityBackup {
        require(text.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) { "The backup exceeds 20 MB." }
        val backup = try { json.decodeFromString<RealityBackup>(text) }
        catch (_: Exception) { throw IllegalArgumentException("This file is not a valid Reality version 1 JSON backup.") }
        validate(backup)
        return backup
    }

    fun validate(backup: RealityBackup) {
        require(backup.app == "Reality" && backup.schemaVersion == 1) { "This Reality backup version is not supported." }
        require(runCatching { Instant.parse(backup.exportedAt) }.isSuccess) { "The backup export date is invalid." }
        require(runCatching { ZoneId.of(backup.timeZone) }.isSuccess) { "The backup timezone is invalid." }
        require(backup.source.length <= 100) { "The backup source is too long." }
        require(backup.activities.size.toLong() + backup.sessions.size + backup.breaks.size + backup.streaks.size <= MAX_RECORDS) {
            "This backup contains too many records. The limit is 200,000."
        }
        fun unique(ids: List<Long>, kind: String) {
            require(ids.all { it > 0 && it < Long.MAX_VALUE } && ids.toSet().size == ids.size) {
                "The backup has invalid or duplicate $kind IDs."
            }
        }
        fun stamp(value: String?, kind: String): LocalDateTime? = value?.let {
            require(it.length <= 40) { "The backup contains an invalid $kind timestamp." }
            try { LocalDateTime.parse(it) } catch (_: Exception) {
                throw IllegalArgumentException("The backup contains an invalid $kind timestamp.")
            }
        }
        unique(backup.activities.map { it.id }, "activity")
        unique(backup.sessions.map { it.id }, "session")
        unique(backup.breaks.map { it.id }, "break")
        val activities = backup.activities.associateBy { it.id }
        backup.activities.forEach { activity ->
            require(activity.name == null || activity.name.length <= 10_000) { "An activity name is too long." }
            // Older archived activities can predate scheduling fields. Preserve those nulls;
            // active commitments must have everything needed to calculate progress safely.
            if (activity.active == true) {
                require(!activity.name.isNullOrBlank()) { "An active activity has an invalid name." }
                require(activity.minimumDuration != null && activity.minimumDuration > 0) { "An active activity has an invalid minimum duration." }
                require(activity.category != null && activity.startDate != null && activity.scheduledDays != null) {
                    "An active activity is missing its category, start date or scheduled days."
                }
            }
            require(activity.startDate == null || runCatching { LocalDate.parse(activity.startDate) }.isSuccess) {
                "An activity contains an invalid commitment start date."
            }
            require(activity.scheduledDays == null || activity.scheduledDays.all { day -> DayOfWeek.entries.any { it.name == day } }) {
                "An activity contains invalid scheduled days."
            }
            stamp(activity.createdAt, "activity creation")
            stamp(activity.updatedAt, "activity update")
        }
        val sessions = backup.sessions.associateBy { it.id }
        backup.sessions.forEach { session ->
            require(session.activityId in activities) { "A session refers to an activity missing from this backup." }
            val start = requireNotNull(stamp(session.startTime, "session start"))
            val end = stamp(session.endTime, "session end")
            require(end == null || !end.isBefore(start)) { "A session ends before it starts." }
            require(session.duration == null || session.duration >= 0) { "A session has a negative duration." }
            require(end != null || session.duration == null) { "An unfinished session cannot have a final duration." }
        }
        backup.breaks.groupBy { it.sessionId }.forEach { (sessionId, values) ->
            val session = requireNotNull(sessions[sessionId]) { "A break refers to a session missing from this backup." }
            val sessionStart = LocalDateTime.parse(session.startTime)
            val sessionEnd = stamp(session.endTime, "session end")
            var previousEnd: LocalDateTime? = null
            var previousOpen = false
            values.sortedBy { it.startTime }.forEach { item ->
                val start = requireNotNull(stamp(item.startTime, "break start"))
                val end = stamp(item.endTime, "break end")
                require(!start.isBefore(sessionStart) && (sessionEnd == null || !start.isAfter(sessionEnd))) { "A break is outside its session." }
                require(end == null || (!end.isBefore(start) && (sessionEnd == null || !end.isAfter(sessionEnd)))) { "A break has an invalid end time." }
                require(!previousOpen && (previousEnd == null || !start.isBefore(previousEnd))) { "A session contains overlapping breaks." }
                require(item.duration == null || item.duration >= 0) { "A break has a negative duration." }
                require(end != null || item.duration == null) { "An unfinished break cannot have a final duration." }
                require(item.description == null || item.description.length <= 100_000) { "A break description is too long." }
                previousEnd = end
                previousOpen = end == null
            }
        }
        require(backup.streaks.map { it.activityId }.distinct().size == backup.streaks.size) { "The backup contains duplicate streak snapshots." }
        val lastDate = Instant.parse(backup.exportedAt).atZone(ZoneId.of(backup.timeZone)).toLocalDate().minusDays(1)
        backup.streaks.forEach {
            require(it.activityId in activities && it.currentStreak >= 0 && runCatching { LocalDate.parse(it.lastEvaluatedDate) }.isSuccess) {
                "The backup contains an invalid streak snapshot."
            }
            require(LocalDate.parse(it.lastEvaluatedDate) == lastDate) { "The streak snapshot date does not match the export timezone." }
        }
    }
}

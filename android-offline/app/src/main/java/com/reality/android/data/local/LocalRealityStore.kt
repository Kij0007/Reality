package com.reality.android.data.local

import androidx.room.withTransaction
import com.reality.android.data.remote.dto.ActivityDto
import com.reality.android.data.remote.dto.ActivityRequest
import com.reality.android.data.remote.dto.BreakDto
import com.reality.android.data.remote.dto.SessionDto
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Every state transition and import commits all related rows together. */
@Singleton
class LocalRealityStore @Inject constructor(private val database: RealityDatabase) {
    private val dao = database.realityDao()
    val revision: Flow<Long> = dao.observeGeneration().map { it ?: 0L }

    suspend fun timezone(): String = dao.metadata()?.timezone ?: "Asia/Kolkata"
    suspend fun snapshot(): LocalSnapshot = database.withTransaction { readSnapshot() }
    suspend fun snapshotWithZone(): Pair<LocalSnapshot, String> = database.withTransaction { readSnapshot() to timezone() }

    suspend fun replaceAll(snapshot: LocalSnapshot, zoneId: String? = null) = database.withTransaction {
        RealityCalculations.validateReferences(snapshot)
        zoneId?.let { ZoneId.of(it) }
        val oldMetadata = dao.metadata() ?: LocalMetadataEntity()
        dao.clearBreaks()
        dao.clearSessions()
        dao.clearActivities()
        dao.insertActivities(snapshot.activities.map { it.entity() })
        dao.insertSessions(snapshot.sessions.map { it.entity() })
        dao.insertBreaks(snapshot.breaks.map { it.entity() })
        dao.putMetadata(oldMetadata.copy(timezone = zoneId ?: oldMetadata.timezone,
            importGeneration = oldMetadata.importGeneration + 1))
    }

    private suspend fun readSnapshot() = LocalSnapshot(
        dao.activities().map { it.dto() }, dao.sessions().map { it.dto() }, dao.breaks().map { it.dto() },
    )

    suspend fun activities(): List<ActivityDto> = dao.activities().filter { it.active == true }.map { it.dto() }
    suspend fun activity(id: Long): ActivityDto = activeActivity(id).dto()

    suspend fun createActivity(request: ActivityRequest, now: LocalDateTime): ActivityDto = database.withTransaction {
        RealityCalculations.validate(request)
        val timestamp = now.toString()
        val entity = LocalActivityEntity(name = request.name, minimumDuration = request.minimumDuration,
            category = request.category, active = true, scheduledDays = request.scheduledDays,
            startDate = request.startDate, createdAt = timestamp, updatedAt = timestamp)
        entity.copy(id = dao.insertActivity(entity)).dto()
    }

    suspend fun updateActivity(id: Long, request: ActivityRequest, now: LocalDateTime): ActivityDto = database.withTransaction {
        val old = activeActivity(id)
        RealityCalculations.validate(request)
        val updated = old.copy(name = request.name, minimumDuration = request.minimumDuration,
            category = request.category, scheduledDays = request.scheduledDays, startDate = request.startDate,
            updatedAt = now.toString())
        dao.updateActivity(updated)
        updated.dto()
    }

    suspend fun deleteActivity(id: Long, now: LocalDateTime) = database.withTransaction {
        dao.updateActivity(activeActivity(id).copy(active = false, updatedAt = now.toString()))
    }

    suspend fun sessions(): List<SessionDto> = dao.sessions().map { it.dto() }
    suspend fun session(id: Long): SessionDto = requiredSession(id).dto()
    suspend fun sessionsForActivity(activityId: Long): List<SessionDto> = database.withTransaction {
        requiredActivity(activityId)
        dao.sessions().filter { it.activityId == activityId }.map { it.dto() }
    }
    suspend fun sessionsForDay(activityId: Long, date: LocalDate) = database.withTransaction {
        RealityCalculations.day(readSnapshot(), activityId, date)
    }

    suspend fun startSession(activityId: Long, now: LocalDateTime): SessionDto = database.withTransaction {
        activeActivity(activityId)
        val entity = LocalSessionEntity(activityId = activityId, startTime = now.toString())
        entity.copy(id = dao.insertSession(entity)).dto()
    }

    suspend fun stopSession(id: Long, now: LocalDateTime): SessionDto = database.withTransaction {
        val session = requiredRunningSession(id)
        val breaks = dao.sessionBreaks(id)
        RealityCalculations.requireClockNotBefore(now, sessionTimes(session, breaks))
        val end = now.toString()
        breaks.filter { it.endTime == null }.forEach { pause ->
            dao.updateBreak(pause.copy(endTime = end,
                duration = Duration.between(RealityCalculations.parseTime(pause.startTime), now).seconds))
        }
        val elapsed = Duration.between(RealityCalculations.parseTime(session.startTime), now).seconds
        val paused = dao.sessionBreaks(id).sumOf { it.duration ?: 0L }
        val stopped = session.copy(endTime = end, duration = (elapsed - paused).coerceAtLeast(0))
        dao.updateSession(stopped)
        stopped.dto()
    }

    suspend fun deleteSession(id: Long) = database.withTransaction {
        requiredSession(id)
        dao.deleteSession(id) // Room foreign keys also remove this session's breaks.
    }

    suspend fun breaks(id: Long): List<BreakDto> = database.withTransaction {
        requiredSession(id)
        dao.sessionBreaks(id).map { it.dto() }
    }

    suspend fun startBreak(id: Long, description: String?, now: LocalDateTime): BreakDto = database.withTransaction {
        val session = requiredRunningSession(id, "Cannot take a break on a stopped session.")
        val breaks = dao.sessionBreaks(id)
        if (breaks.any { it.endTime == null }) throw LocalDataException("Session is already on break.")
        RealityCalculations.requireClockNotBefore(now, sessionTimes(session, breaks))
        val entity = LocalBreakEntity(sessionId = id, startTime = now.toString(), description = description)
        entity.copy(id = dao.insertBreak(entity)).dto()
    }

    suspend fun resume(id: Long, now: LocalDateTime): BreakDto = database.withTransaction {
        val session = requiredRunningSession(id)
        val breaks = dao.sessionBreaks(id)
        val pause = breaks.firstOrNull { it.endTime == null }
            ?: throw LocalDataException("Session is not currently on break.")
        RealityCalculations.requireClockNotBefore(now, sessionTimes(session, breaks))
        val resumed = pause.copy(endTime = now.toString(),
            duration = Duration.between(RealityCalculations.parseTime(pause.startTime), now).seconds)
        dao.updateBreak(resumed)
        resumed.dto()
    }

    suspend fun progress(activityId: Long, date: LocalDate) = database.withTransaction {
        RealityCalculations.progress(readSnapshot(), activityId, date)
    }
    suspend fun streak(activityId: Long, today: LocalDate) = database.withTransaction {
        RealityCalculations.streak(readSnapshot(), activityId, today)
    }
    suspend fun currentStreak(activityId: Long) = database.withTransaction {
        RealityCalculations.streak(readSnapshot(), activityId, LocalDate.now(ZoneId.of(timezone())))
    }
    suspend fun report(activityId: Long, month: YearMonth, today: LocalDate) = database.withTransaction {
        RealityCalculations.month(readSnapshot(), activityId, month, today)
    }
    suspend fun currentReport(activityId: Long, month: YearMonth) = database.withTransaction {
        RealityCalculations.month(readSnapshot(), activityId, month, LocalDate.now(ZoneId.of(timezone())))
    }

    private suspend fun requiredActivity(id: Long): LocalActivityEntity = dao.activity(id)
        ?: throw LocalDataException("Activity not found.", 404)
    private suspend fun activeActivity(id: Long): LocalActivityEntity = requiredActivity(id).takeIf { it.active == true }
        ?: throw LocalDataException("Activity not found.", 404)
    private suspend fun requiredSession(id: Long): LocalSessionEntity = dao.session(id)
        ?: throw LocalDataException("Session not found.", 404)
    private suspend fun requiredRunningSession(id: Long, message: String = "Session has already been stopped."): LocalSessionEntity =
        requiredSession(id).also { if (it.endTime != null) throw LocalDataException(message) }
    private fun sessionTimes(session: LocalSessionEntity, breaks: List<LocalBreakEntity>): List<String> =
        listOf(session.startTime) + breaks.flatMap { listOfNotNull(it.startTime, it.endTime) }
}

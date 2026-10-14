package com.reality.android.data.local

import com.reality.android.data.remote.dto.ActivityDto
import com.reality.android.data.remote.dto.ActivityRequest
import com.reality.android.data.remote.dto.BreakDto
import com.reality.android.data.remote.dto.DailyProgressDto
import com.reality.android.data.remote.dto.MonthlyReportDto
import com.reality.android.data.remote.dto.SessionDayDto
import com.reality.android.data.remote.dto.SessionDto
import com.reality.android.data.remote.dto.StreakDto
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

data class LocalSnapshot(
    val activities: List<ActivityDto> = emptyList(),
    val sessions: List<SessionDto> = emptyList(),
    val breaks: List<BreakDto> = emptyList(),
)

class LocalDataException(message: String, val status: Int = 400) : IllegalArgumentException(message)

/** The Spring services use local timestamps, minute targets and completed sessions.
 * These calculations retain that contract so imported history has the same meaning. */
object RealityCalculations {
    fun activity(data: LocalSnapshot, id: Long, activeOnly: Boolean = true): ActivityDto =
        data.activities.firstOrNull { it.id == id && (!activeOnly || it.active == true) }
            ?: throw LocalDataException("Activity not found.", 404)

    fun validate(request: ActivityRequest) {
        if (request.name.isBlank()) throw LocalDataException("Enter an activity name.")
        if (request.minimumDuration <= 0) throw LocalDataException("Minimum duration must be greater than 0.")
        if (request.scheduledDays.isEmpty()) throw LocalDataException("At least one scheduled day is required.")
        request.scheduledDays.forEach { day ->
            if (runCatching { DayOfWeek.valueOf(day) }.isFailure) throw LocalDataException("Choose valid scheduled days.")
        }
        parseDate(request.startDate)
    }

    fun parseDate(value: String): LocalDate = runCatching { LocalDate.parse(value) }.getOrElse {
        throw LocalDataException("Enter a valid date in yyyy-MM-dd format.")
    }

    fun parseMonth(value: String): YearMonth = runCatching { YearMonth.parse(value) }.getOrElse {
        throw LocalDataException("Enter a valid month in yyyy-MM format.")
    }

    fun parseTime(value: String): LocalDateTime = runCatching { LocalDateTime.parse(value) }.getOrElse {
        throw LocalDataException("A saved timestamp is invalid. Restore a valid backup or repair its source data.")
    }

    fun requireClockNotBefore(now: LocalDateTime, times: List<String>) {
        if (times.any { now.isBefore(parseTime(it)) }) {
            throw LocalDataException("The phone clock is earlier than this session. Correct the date/time before continuing.")
        }
    }

    fun overlapSeconds(session: SessionDto, breaks: List<BreakDto>, start: LocalDateTime, end: LocalDateTime): Long {
        val stopped = session.endTime ?: return 0
        val overlapStart = maxOf(parseTime(session.startTime), start)
        val overlapEnd = minOf(parseTime(stopped), end)
        if (!overlapEnd.isAfter(overlapStart)) return 0
        val elapsed = Duration.between(overlapStart, overlapEnd).seconds
        val paused = breaks.filter { it.sessionId == session.id }.sumOf { pause ->
            val breakStart = maxOf(parseTime(pause.startTime), overlapStart)
            val breakEnd = minOf(parseTime(pause.endTime ?: stopped), overlapEnd)
            if (breakEnd.isAfter(breakStart)) Duration.between(breakStart, breakEnd).seconds else 0L
        }
        return (elapsed - paused).coerceAtLeast(0)
    }

    fun day(data: LocalSnapshot, activityId: Long, date: LocalDate): SessionDayDto {
        activity(data, activityId, activeOnly = false)
        val start = date.atStartOfDay()
        val end = date.plusDays(1).atStartOfDay()
        val sessions = data.sessions.filter {
            it.activityId == activityId && it.endTime != null &&
                parseTime(it.startTime).isBefore(end) && parseTime(it.endTime).isAfter(start)
        }
        return SessionDayDto(activityId, date.toString(), sessions.sumOf { overlapSeconds(it, data.breaks, start, end) }, sessions)
    }

    fun progress(data: LocalSnapshot, activityId: Long, date: LocalDate): DailyProgressDto {
        val activity = activity(data, activityId)
        val required = activity.minimumDuration?.takeIf { it > 0 }
            ?: throw LocalDataException("This activity needs a valid minimum duration. Edit it before viewing progress.")
        val total = day(data, activityId, date).totalDuration
        return DailyProgressDto(activityId, activity.name, date.toString(), required, total, total >= required * 60L)
    }

    fun streak(data: LocalSnapshot, activityId: Long, today: LocalDate): StreakDto {
        val activity = activity(data, activityId)
        val start = activity.startDate?.let(::parseDate)
            ?: throw LocalDataException("This activity needs a start date. Edit it before viewing its streak.")
        val days = activity.scheduledDays.orEmpty()
        val yesterday = today.minusDays(1)
        var date = yesterday
        var completed = 0
        if (days.isEmpty()) return StreakDto(activityId, activity.name, 0, yesterday.toString())
        while (!date.isBefore(start)) {
            if (date.dayOfWeek.name in days) {
                if (!progress(data, activityId, date).completed) break
                completed++
            }
            date = date.minusDays(1)
        }
        return StreakDto(activityId, activity.name, completed, yesterday.toString())
    }

    fun month(data: LocalSnapshot, activityId: Long, month: YearMonth, today: LocalDate): MonthlyReportDto {
        val activity = activity(data, activityId)
        val commitmentStart = activity.startDate?.let(::parseDate)
            ?: throw LocalDataException("This activity needs a start date. Edit it before viewing reports.")
        val start = maxOf(month.atDay(1), commitmentStart)
        val durationEnd = minOf(month.atEndOfMonth(), today)
        val evaluationEnd = minOf(month.atEndOfMonth(), today.minusDays(1))
        var total = 0L
        var scheduled = 0
        var completed = 0
        var longest = 0
        var running = 0
        var date = start
        while (!date.isAfter(durationEnd)) {
            val progress = progress(data, activityId, date)
            total += progress.totalDuration
            if (!date.isAfter(evaluationEnd) && date.dayOfWeek.name in activity.scheduledDays.orEmpty()) {
                scheduled++
                if (progress.completed) {
                    completed++
                    running++
                    longest = maxOf(longest, running)
                } else running = 0
            }
            date = date.plusDays(1)
        }
        val sessions = if (start.isAfter(durationEnd)) 0 else {
            val rangeStart = start.atStartOfDay()
            val rangeEnd = durationEnd.plusDays(1).atStartOfDay()
            data.sessions.count { it.activityId == activityId && it.endTime != null &&
                parseTime(it.startTime).isBefore(rangeEnd) && parseTime(it.endTime).isAfter(rangeStart) }
        }
        val percentage = if (scheduled == 0) 0.0 else Math.round(completed * 10000.0 / scheduled) / 100.0
        return MonthlyReportDto(activityId, activity.name, month.year, month.monthValue, total, sessions,
            scheduled, completed, scheduled - completed, percentage, streak(data, activityId, today).currentStreak, longest)
    }

    fun validateReferences(data: LocalSnapshot) {
        fun requireIds(ids: List<Long>, name: String) {
            if (ids.any { it <= 0 } || ids.distinct().size != ids.size) {
                throw LocalDataException("The backup has duplicate or invalid $name IDs.")
            }
        }
        requireIds(data.activities.map { it.id }, "activity")
        requireIds(data.sessions.map { it.id }, "session")
        requireIds(data.breaks.map { it.id }, "break")
        val activityIds = data.activities.map { it.id }.toSet()
        val sessionIds = data.sessions.map { it.id }.toSet()
        if (data.sessions.any { it.activityId !in activityIds } || data.breaks.any { it.sessionId !in sessionIds }) {
            throw LocalDataException("The backup contains a session or break without its parent record.")
        }
        if (data.breaks.filter { it.endTime == null }.groupBy { it.sessionId }.any { it.value.size > 1 }) {
            throw LocalDataException("The backup contains more than one open break for a session.")
        }
    }
}

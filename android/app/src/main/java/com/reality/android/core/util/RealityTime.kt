package com.reality.android.core.util

import com.reality.android.data.remote.dto.ActivityDto
import com.reality.android.data.remote.dto.BreakDto
import com.reality.android.data.remote.dto.SessionDto
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val remaining = safe % 60
    return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remaining)
    else String.format(Locale.getDefault(), "%d:%02d", minutes, remaining)
}

fun formatDate(value: String?): String = value?.let {
    runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrNull()
} ?: "Unavailable"

// Spring returns LocalDateTime without an offset. Display server wall time, never
// silently reinterpret it as the Android device's local timezone.
fun formatDateTime(value: String?): String = value?.let {
    runCatching {
        LocalDateTime.parse(it).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
    }.getOrNull()
} ?: "Unavailable"

fun today(zoneId: String): LocalDate = LocalDate.now(ZoneId.of(zoneId))

fun isScheduled(activity: ActivityDto, date: LocalDate): Boolean {
    if (activity.active == false) return false
    val start = runCatching { LocalDate.parse(activity.startDate) }.getOrNull() ?: return false
    return !date.isBefore(start) && activity.scheduledDays?.contains(date.dayOfWeek.name) == true
}

fun liveWorkingSeconds(
    session: SessionDto,
    breaks: List<BreakDto>,
    zoneId: String,
    now: Instant = Instant.now(),
): Long {
    if (session.endTime != null && session.duration != null) return session.duration.coerceAtLeast(0)
    val start = runCatching { LocalDateTime.parse(session.startTime) }.getOrNull() ?: return 0
    val end = if (session.endTime != null) {
        runCatching { LocalDateTime.parse(session.endTime) }.getOrNull() ?: return 0
    } else {
        runCatching { LocalDateTime.ofInstant(now, ZoneId.of(zoneId)) }.getOrNull() ?: return 0
    }
    if (!end.isAfter(start)) return 0
    // Clip to the session and merge overlaps defensively; a timer is only an
    // estimate until the stop endpoint returns its authoritative duration.
    val intervals = breaks.filter { it.sessionId == session.id }.mapNotNull { entry ->
        val breakStart = runCatching { LocalDateTime.parse(entry.startTime) }.getOrNull() ?: return@mapNotNull null
        val breakEnd = entry.endTime?.let {
            runCatching { LocalDateTime.parse(it) }.getOrNull()
        } ?: end
        val clippedStart = maxOf(start, breakStart)
        val clippedEnd = minOf(end, breakEnd)
        if (clippedEnd.isAfter(clippedStart)) clippedStart to clippedEnd else null
    }.sortedBy { it.first }
    var breakSeconds = 0L
    var intervalStart: LocalDateTime? = null
    var intervalEnd: LocalDateTime? = null
    intervals.forEach { (from, to) ->
        val currentEnd = intervalEnd
        if (currentEnd == null) {
            intervalStart = from
            intervalEnd = to
        } else if (!from.isAfter(currentEnd)) {
            intervalEnd = maxOf(currentEnd, to)
        } else {
            intervalStart?.let { breakSeconds += Duration.between(it, currentEnd).seconds }
            intervalStart = from
            intervalEnd = to
        }
    }
    val finalStart = intervalStart
    val finalEnd = intervalEnd
    if (finalStart != null && finalEnd != null) breakSeconds += Duration.between(finalStart, finalEnd).seconds
    return (Duration.between(start, end).seconds - breakSeconds).coerceAtLeast(0)
}

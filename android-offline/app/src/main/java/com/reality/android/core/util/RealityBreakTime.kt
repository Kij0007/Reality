package com.reality.android.core.util

import com.reality.android.data.remote.dto.BreakDto
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Legacy stopped sessions may contain a break whose own end time was never saved. */
fun breakDisplaySeconds(
    entry: BreakDto,
    sessionEnd: String?,
    zoneId: String,
    now: Instant = Instant.now(),
): Long? {
    val recordedEnd = entry.endTime ?: sessionEnd
    if (recordedEnd != null && entry.duration != null) return entry.duration.coerceAtLeast(0)
    return runCatching {
        val start = LocalDateTime.parse(entry.startTime)
        val end = recordedEnd?.let(LocalDateTime::parse)
            ?: LocalDateTime.ofInstant(now, ZoneId.of(zoneId))
        Duration.between(start, end).seconds.coerceAtLeast(0)
    }.getOrNull()
}


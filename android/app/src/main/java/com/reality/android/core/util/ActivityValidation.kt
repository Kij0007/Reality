package com.reality.android.core.util

import com.reality.android.data.remote.dto.ActivityCategory
import java.time.DayOfWeek
import java.time.LocalDate

fun validateActivity(
    name: String,
    duration: String,
    category: ActivityCategory?,
    days: Set<String>,
    startDate: String,
): Map<String, String> = buildMap {
    if (name.isBlank()) put("name", "Enter an activity name.")
    else if (name.length > 255) put("name", "Use 255 characters or fewer.")
    if (duration.trim().toIntOrNull()?.let { it > 0 } != true) {
        put("minimumDuration", "Enter a whole number of minutes greater than zero.")
    }
    if (category == null) put("category", "Choose a category.")
    if (days.isEmpty() || days.any { day -> DayOfWeek.entries.none { it.name == day } }) {
        put("scheduledDays", "Choose at least one valid repeat day.")
    }
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(startDate) ||
        runCatching { LocalDate.parse(startDate) }.isFailure) {
        put("startDate", "Choose a valid commitment start date.")
    }
}

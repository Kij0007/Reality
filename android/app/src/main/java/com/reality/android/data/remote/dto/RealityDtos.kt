package com.reality.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
enum class ActivityCategory { STUDY, FITNESS, WORK, LEARNING, HEALTH, PERSONAL, OTHER }

@Serializable
data class ActivityDto(
    val id: Long,
    val name: String? = null,
    val minimumDuration: Int? = null,
    val category: ActivityCategory? = null,
    val active: Boolean? = null,
    val scheduledDays: Set<String>? = null,
    val startDate: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ActivityRequest(
    val name: String,
    val minimumDuration: Int,
    val category: ActivityCategory,
    val scheduledDays: Set<String>,
    val startDate: String,
)

@Serializable
data class SessionDto(
    val id: Long,
    val activityId: Long,
    val startTime: String,
    val endTime: String? = null,
    val duration: Long? = null,
)

@Serializable
data class BreakDto(
    val id: Long,
    val sessionId: Long,
    val startTime: String,
    val endTime: String? = null,
    val duration: Long? = null,
    val description: String? = null,
)

@Serializable
data class SessionRequest(val activityId: Long)

@Serializable
data class BreakRequest(val description: String? = null)

@Serializable
data class DailyProgressDto(
    val activityId: Long,
    val activityName: String? = null,
    val date: String,
    val minimumDuration: Int,
    val totalDuration: Long,
    val completed: Boolean,
)

@Serializable
data class SessionDayDto(
    val activityId: Long,
    val date: String,
    val totalDuration: Long,
    val sessions: List<SessionDto>,
)

@Serializable
data class StreakDto(
    val activityId: Long,
    val activityName: String? = null,
    val currentStreak: Int,
    val lastEvaluatedDate: String,
)

@Serializable
data class MonthlyReportDto(
    val activityId: Long,
    val activityName: String? = null,
    val year: Int,
    val month: Int,
    val totalDuration: Long,
    val totalSessions: Int,
    val scheduledDays: Int,
    val completedDays: Int,
    val missedDays: Int,
    val completionPercentage: Double,
    val currentStreak: Int,
    val longestStreak: Int,
)

// Nullable because Spring's fallback error responses can omit fields.
@Serializable
data class ErrorDto(
    val timestamp: String? = null,
    val status: Int? = null,
    val error: String? = null,
    val message: String? = null,
    val path: String? = null,
)

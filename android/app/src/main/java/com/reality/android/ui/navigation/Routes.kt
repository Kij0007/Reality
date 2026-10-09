package com.reality.android.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object Home
@Serializable data object Activities
@Serializable data class Tracking(val activityId: Long? = null)
@Serializable data class Reports(val activityId: Long? = null)
@Serializable data object More
@Serializable data object Schedule
@Serializable data class Progress(val activityId: Long? = null)
@Serializable data object Settings
@Serializable data class ActivityDetail(val activityId: Long)
@Serializable data object CreateActivity
@Serializable data class EditActivity(val activityId: Long)
@Serializable data class SessionDetail(val sessionId: Long)

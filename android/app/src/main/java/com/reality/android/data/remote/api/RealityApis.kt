package com.reality.android.data.remote.api

import com.reality.android.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ActivityApi {
    @GET("activities")
    suspend fun list(): Response<List<ActivityDto>>

    @GET("activities/{id}")
    suspend fun get(@Path("id") id: Long): Response<ActivityDto>

    @POST("activities")
    suspend fun create(@Body request: ActivityRequest): Response<ActivityDto>

    @PUT("activities/{id}")
    suspend fun update(@Path("id") id: Long, @Body request: ActivityRequest): Response<ActivityDto>

    // Unit deliberately discards the backend's plain String success body.
    @DELETE("activities/{id}")
    suspend fun delete(@Path("id") id: Long): Response<Unit>
}

interface SessionApi {
    @GET("api/sessions")
    suspend fun list(): Response<List<SessionDto>>

    @GET("api/sessions/{id}")
    suspend fun get(@Path("id") id: Long): Response<SessionDto>

    @GET("api/sessions/activity/{activityId}")
    suspend fun forActivity(@Path("activityId") activityId: Long): Response<List<SessionDto>>

    @GET("api/sessions/activity/{activityId}/date/{date}")
    suspend fun forDay(@Path("activityId") activityId: Long, @Path("date") date: String): Response<SessionDayDto>

    @POST("api/sessions/start")
    suspend fun start(@Body request: SessionRequest): Response<SessionDto>

    @PUT("api/sessions/{id}/stop")
    suspend fun stop(@Path("id") id: Long): Response<SessionDto>

    @DELETE("api/sessions/{id}")
    suspend fun delete(@Path("id") id: Long): Response<Unit>
}

interface SessionBreakApi {
    @GET("api/sessions/{sessionId}/breaks")
    suspend fun list(@Path("sessionId") sessionId: Long): Response<List<BreakDto>>

    @POST("api/sessions/{sessionId}/break")
    suspend fun start(@Path("sessionId") sessionId: Long, @Body request: BreakRequest): Response<BreakDto>

    @PUT("api/sessions/{sessionId}/resume")
    suspend fun resume(@Path("sessionId") sessionId: Long): Response<BreakDto>
}

interface DailyProgressApi {
    @GET("api/daily-progress/activity/{activityId}/date/{date}")
    suspend fun day(@Path("activityId") activityId: Long, @Path("date") date: String): Response<DailyProgressDto>
}

interface StreakApi {
    @GET("api/streaks/activity/{activityId}")
    suspend fun get(@Path("activityId") activityId: Long): Response<StreakDto>
}

interface ReportApi {
    @GET("api/reports/activity/{activityId}/month/{month}")
    suspend fun month(@Path("activityId") activityId: Long, @Path("month") month: String): Response<MonthlyReportDto>
}

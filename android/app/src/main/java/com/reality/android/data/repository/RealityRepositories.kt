package com.reality.android.data.repository

import com.reality.android.core.network.ApiExecutor
import com.reality.android.core.network.ApiResult
import com.reality.android.core.network.MutationGate
import com.reality.android.data.remote.api.*
import com.reality.android.data.remote.dto.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepository @Inject constructor(
    private val api: ActivityApi,
    private val executor: ApiExecutor,
    private val mutations: MutationGate,
) {
    suspend fun list(): ApiResult<List<ActivityDto>> = executor.execute { api.list() }
    suspend fun get(id: Long): ApiResult<ActivityDto> = executor.execute { api.get(id) }
    suspend fun create(request: ActivityRequest): ApiResult<ActivityDto> =
        mutations.run { executor.execute(mutation = true) { api.create(request) } }
    suspend fun update(id: Long, request: ActivityRequest): ApiResult<ActivityDto> =
        mutations.run { executor.execute(mutation = true) { api.update(id, request) } }
    suspend fun delete(id: Long): ApiResult<Unit> =
        mutations.run { executor.executeUnit { api.delete(id) } }
}

@Singleton
class SessionRepository @Inject constructor(
    private val api: SessionApi,
    private val breaksApi: SessionBreakApi,
    private val executor: ApiExecutor,
    private val mutations: MutationGate,
) {
    suspend fun list(): ApiResult<List<SessionDto>> = executor.execute { api.list() }
    suspend fun get(id: Long): ApiResult<SessionDto> = executor.execute { api.get(id) }
    suspend fun forActivity(activityId: Long): ApiResult<List<SessionDto>> =
        executor.execute { api.forActivity(activityId) }
    suspend fun forDay(activityId: Long, date: String): ApiResult<SessionDayDto> =
        executor.execute { api.forDay(activityId, date) }
    suspend fun start(activityId: Long): ApiResult<SessionDto> =
        mutations.run { executor.execute(mutation = true) { api.start(SessionRequest(activityId)) } }
    suspend fun stop(id: Long): ApiResult<SessionDto> =
        mutations.run { executor.execute(mutation = true) { api.stop(id) } }
    suspend fun delete(id: Long): ApiResult<Unit> =
        mutations.run { executor.executeUnit { api.delete(id) } }
    suspend fun breaks(id: Long): ApiResult<List<BreakDto>> = executor.execute { breaksApi.list(id) }
    suspend fun startBreak(id: Long, description: String?): ApiResult<BreakDto> =
        mutations.run {
            executor.execute(mutation = true) { breaksApi.start(id, BreakRequest(description)) }
        }
    suspend fun resume(id: Long): ApiResult<BreakDto> =
        mutations.run { executor.execute(mutation = true) { breaksApi.resume(id) } }
}

@Singleton
class ProgressRepository @Inject constructor(
    private val daily: DailyProgressApi,
    private val streaks: StreakApi,
    private val executor: ApiExecutor,
) {
    suspend fun day(activityId: Long, date: String): ApiResult<DailyProgressDto> =
        executor.execute { daily.day(activityId, date) }
    suspend fun streak(activityId: Long): ApiResult<StreakDto> =
        executor.execute { streaks.get(activityId) }
}

@Singleton
class ReportRepository @Inject constructor(
    private val api: ReportApi,
    private val executor: ApiExecutor,
) {
    suspend fun month(activityId: Long, month: String): ApiResult<MonthlyReportDto> =
        executor.execute { api.month(activityId, month) }
}

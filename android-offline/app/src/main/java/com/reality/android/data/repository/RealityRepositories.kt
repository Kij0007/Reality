package com.reality.android.data.repository

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteFullException
import com.reality.android.core.network.ApiResult
import com.reality.android.core.network.AppError
import com.reality.android.core.network.MutationGate
import com.reality.android.data.local.LocalClock
import com.reality.android.data.local.LocalDataException
import com.reality.android.data.local.LocalRealityStore
import com.reality.android.data.local.RealityCalculations
import com.reality.android.data.remote.dto.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

private suspend fun <T> localResult(action: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(action())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: LocalDataException) {
    ApiResult.Failure(AppError(error.message ?: "This action could not be completed.", error.status))
} catch (_: SQLiteFullException) {
    ApiResult.Failure(AppError("Your phone storage is full. Free some space and try again."))
} catch (_: SQLiteDatabaseCorruptException) {
    ApiResult.Failure(AppError("Reality could not read its local database. Restore a saved backup."))
} catch (_: IOException) {
    ApiResult.Failure(AppError("Reality could not access phone storage. Try again."))
} catch (_: Exception) {
    ApiResult.Failure(AppError("Reality could not save or load this data. Try again or restore a valid backup."))
}

@Singleton
class ActivityRepository @Inject constructor(
    private val store: LocalRealityStore,
    private val clock: LocalClock,
    private val mutations: MutationGate,
) {
    suspend fun list(): ApiResult<List<ActivityDto>> = localResult { store.activities() }
    suspend fun get(id: Long): ApiResult<ActivityDto> = localResult { store.activity(id) }
    suspend fun create(request: ActivityRequest): ApiResult<ActivityDto> =
        mutations.run { localResult { store.createActivity(request, clock.now()) } }
    suspend fun update(id: Long, request: ActivityRequest): ApiResult<ActivityDto> =
        mutations.run { localResult { store.updateActivity(id, request, clock.now()) } }
    suspend fun delete(id: Long): ApiResult<Unit> =
        mutations.run { localResult { store.deleteActivity(id, clock.now()) } }
}

@Singleton
class SessionRepository @Inject constructor(
    private val store: LocalRealityStore,
    private val clock: LocalClock,
    private val mutations: MutationGate,
) {
    suspend fun list(): ApiResult<List<SessionDto>> = localResult { store.sessions() }
    suspend fun get(id: Long): ApiResult<SessionDto> = localResult { store.session(id) }
    suspend fun forActivity(activityId: Long): ApiResult<List<SessionDto>> =
        localResult { store.sessionsForActivity(activityId) }
    suspend fun forDay(activityId: Long, date: String): ApiResult<SessionDayDto> =
        localResult { store.sessionsForDay(activityId, RealityCalculations.parseDate(date)) }
    suspend fun start(activityId: Long): ApiResult<SessionDto> =
        mutations.run { localResult { store.startSession(activityId, clock.now()) } }
    suspend fun stop(id: Long): ApiResult<SessionDto> =
        mutations.run { localResult { store.stopSession(id, clock.now()) } }
    suspend fun delete(id: Long): ApiResult<Unit> =
        mutations.run { localResult { store.deleteSession(id) } }
    suspend fun breaks(id: Long): ApiResult<List<BreakDto>> = localResult { store.breaks(id) }
    suspend fun startBreak(id: Long, description: String?): ApiResult<BreakDto> =
        mutations.run {
            localResult { store.startBreak(id, description, clock.now()) }
        }
    suspend fun resume(id: Long): ApiResult<BreakDto> =
        mutations.run { localResult { store.resume(id, clock.now()) } }
}

@Singleton
class ProgressRepository @Inject constructor(private val store: LocalRealityStore) {
    suspend fun day(activityId: Long, date: String): ApiResult<DailyProgressDto> =
        localResult { store.progress(activityId, RealityCalculations.parseDate(date)) }
    suspend fun streak(activityId: Long): ApiResult<StreakDto> =
        localResult { store.currentStreak(activityId) }
}

@Singleton
class ReportRepository @Inject constructor(private val store: LocalRealityStore) {
    suspend fun month(activityId: Long, month: String): ApiResult<MonthlyReportDto> =
        localResult { store.currentReport(activityId, RealityCalculations.parseMonth(month)) }
}

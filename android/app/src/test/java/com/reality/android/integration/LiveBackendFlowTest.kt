package com.reality.android.integration

import com.reality.android.data.remote.api.*
import com.reality.android.data.remote.dto.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Opt-in integration test: creates only uniquely named test records, then cleans them up. */
class LiveBackendFlowTest {
    @Test fun allControllerWorkflowsAgainstReachableSpringBoot() = runBlocking {
        val endpoint = System.getenv("REALITY_TEST_BASE_URL")
        assumeTrue("Set REALITY_TEST_BASE_URL to opt in to real backend writes.", !endpoint.isNullOrBlank())
        val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).build()
        val retrofit = Retrofit.Builder().baseUrl(requireNotNull(endpoint))
            .client(client).addConverterFactory(Json { ignoreUnknownKeys = true; explicitNulls = false }
                .asConverterFactory("application/json".toMediaType())).build()
        val activities = retrofit.create(ActivityApi::class.java)
        val sessions = retrofit.create(SessionApi::class.java)
        val breaks = retrofit.create(SessionBreakApi::class.java)
        val daily = retrofit.create(DailyProgressApi::class.java)
        val streak = retrofit.create(StreakApi::class.java)
        val reports = retrofit.create(ReportApi::class.java)
        val name = "Android verification ${UUID.randomUUID()}"
        val request = ActivityRequest(name, 1, ActivityCategory.OTHER,
            DayOfWeek.values().map { it.name }.toSet(), LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1).toString())
        var activityId: Long? = null
        var sessionId: Long? = null
        try {
            val created = body(activities.create(request), 201)
            activityId = created.id
            assertTrue(created.id > 0)
            assertEquals(request.name, created.name)
            assertEquals(request.scheduledDays, created.scheduledDays)
            assertTrue(body(activities.list(), 200).any { it.id == created.id })
            assertEquals(created.id, body(activities.get(created.id), 200).id)
            val edited = body(activities.update(created.id, request.copy(name = "$name edited")), 200)
            assertEquals("$name edited", edited.name)

            val started = body(sessions.start(SessionRequest(created.id)), 201)
            sessionId = started.id
            assertNull(started.endTime)
            assertEquals(created.id, body(sessions.get(started.id), 200).activityId)
            assertTrue(body(sessions.list(), 200).any { it.id == started.id })
            assertTrue(body(sessions.forActivity(created.id), 200).any { it.id == started.id })
            delay(61_100)
            val pause = body(breaks.start(started.id, BreakRequest("Android integration verification")), 201)
            assertNull(pause.endTime)
            delay(1100)
            assertNotNull(body(breaks.resume(started.id), 200).endTime)
            assertEquals(pause.id, body(breaks.list(started.id), 200).single().id)
            // Ending during a second break must close the open break on the server.
            body(breaks.start(started.id, BreakRequest()), 201)
            delay(1100)
            val ended = body(sessions.stop(started.id), 200)
            assertNotNull(ended.endTime)
            assertTrue(requireNotNull(ended.duration) >= 0)
            assertTrue(body(breaks.list(started.id), 200).all { it.endTime != null })

            val day = LocalDateTime.parse(started.startTime).toLocalDate().toString()
            val history = body(sessions.forDay(created.id, day), 200)
            assertTrue(history.sessions.any { it.id == started.id })
            val progress = body(daily.day(created.id, day), 200)
            assertEquals(created.id, progress.activityId)
            assertTrue("One minute of confirmed work should meet the one-minute target.", progress.completed)
            assertEquals(created.id, body(streak.get(created.id), 200).activityId)
            val month = YearMonth.from(LocalDate.parse(day))
            val report = body(reports.month(created.id, month.toString()), 200)
            assertEquals(month.year, report.year)
            assertEquals(month.monthValue, report.month)
            assertTrue(report.totalSessions >= 1)
            assertTrue(report.completionPercentage in 0.0..100.0)

            assertEquals(204, sessions.delete(started.id).code())
            sessionId = null
            assertEquals(404, sessions.get(started.id).code())
            assertEquals(200, activities.delete(created.id).code())
            activityId = null
            assertEquals(404, activities.get(created.id).code())
        } finally {
            sessionId?.let { id -> runCatching { sessions.delete(id) } }
            activityId?.let { id -> runCatching { activities.delete(id) } }
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun <T : Any> body(response: Response<T>, expected: Int): T {
        assertEquals("Unexpected backend response: ${response.errorBody()?.string()}", expected, response.code())
        return requireNotNull(response.body()) { "Backend returned an empty successful response." }
    }
}

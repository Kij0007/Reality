package com.reality.android.data

import com.reality.android.core.network.ApiExecutor
import com.reality.android.core.network.ApiResult
import com.reality.android.core.network.MutationGate
import com.reality.android.data.remote.api.*
import com.reality.android.data.remote.dto.*
import com.reality.android.data.repository.*
import com.reality.android.di.NetworkModule
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ApiContractTest {
    private val server = MockWebServer()
    private val json = NetworkModule.json()
    private lateinit var activities: ActivityRepository
    private lateinit var sessions: SessionRepository
    private lateinit var progress: ProgressRepository
    private lateinit var reports: ReportRepository

    private val activity = """{"id":7,"name":"Read","minimumDuration":20,"category":"LEARNING","active":true,"scheduledDays":["MONDAY"],"startDate":"2026-10-01","createdAt":"2026-10-01T09:00:00","updatedAt":null}"""
    private val session = """{"id":11,"activityId":7,"startTime":"2026-10-05T09:00:00.123456","endTime":null,"duration":null}"""
    private val pause = """{"id":12,"sessionId":11,"startTime":"2026-10-05T09:10:00","endTime":null,"duration":null,"description":"Water"}"""
    private val request = ActivityRequest("Read", 20, ActivityCategory.LEARNING, setOf("MONDAY"), "2026-10-01")

    @Before fun setUp() {
        server.start()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/reality/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
        val executor = ApiExecutor(json)
        val gate = MutationGate()
        activities = ActivityRepository(retrofit.create(ActivityApi::class.java), executor, gate)
        sessions = SessionRepository(retrofit.create(SessionApi::class.java), retrofit.create(SessionBreakApi::class.java), executor, gate)
        progress = ProgressRepository(retrofit.create(DailyProgressApi::class.java), retrofit.create(StreakApi::class.java), executor)
        reports = ReportRepository(retrofit.create(ReportApi::class.java), executor)
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun allEighteenControllerOperationsUseExactRoutesAndPayloads() = runBlocking {
        checkRequest("GET", "activities", "[$activity]") { activities.list() }
        checkRequest("GET", "activities/7", activity) { activities.get(7) }
        val activityPayload = json.parseToJsonElement("""{"name":"Read","minimumDuration":20,"category":"LEARNING","scheduledDays":["MONDAY"],"startDate":"2026-10-01"}""")
        checkRequest("POST", "activities", activity, 201, activityPayload) { activities.create(request) }
        checkRequest("PUT", "activities/7", activity, expectedBody = activityPayload) { activities.update(7, request) }
        // Spring's String response can be mislabeled application/json: it must not be decoded.
        checkRequest("DELETE", "activities/7", "Activity deleted successfully.") { activities.delete(7) }
        checkRequest("GET", "api/sessions", "[$session]") { sessions.list() }
        checkRequest("GET", "api/sessions/11", session) { sessions.get(11) }
        checkRequest("GET", "api/sessions/activity/7", "[$session]") { sessions.forActivity(7) }
        checkRequest("GET", "api/sessions/activity/7/date/2026-10-05", """{"activityId":7,"date":"2026-10-05","totalDuration":0,"sessions":[$session]}""") { sessions.forDay(7, "2026-10-05") }
        checkRequest("POST", "api/sessions/start", session, 201, JsonObject(mapOf("activityId" to JsonPrimitive(7)))) { sessions.start(7) }
        checkRequest("PUT", "api/sessions/11/stop", session) { sessions.stop(11) }
        checkRequest("DELETE", "api/sessions/11", "", 204) { sessions.delete(11) }
        checkRequest("GET", "api/sessions/11/breaks", "[$pause]") { sessions.breaks(11) }
        checkRequest("POST", "api/sessions/11/break", pause, 201, JsonObject(mapOf("description" to JsonPrimitive("Water")))) { sessions.startBreak(11, "Water") }
        checkRequest("PUT", "api/sessions/11/resume", pause) { sessions.resume(11) }
        checkRequest("GET", "api/daily-progress/activity/7/date/2026-10-05", """{"activityId":7,"activityName":"Read","date":"2026-10-05","minimumDuration":20,"totalDuration":1200,"completed":true}""") { progress.day(7, "2026-10-05") }
        checkRequest("GET", "api/streaks/activity/7", """{"activityId":7,"activityName":"Read","currentStreak":2,"lastEvaluatedDate":"2026-10-04"}""") { progress.streak(7) }
        checkRequest("GET", "api/reports/activity/7/month/2026-10", """{"activityId":7,"activityName":"Read","year":2026,"month":10,"totalDuration":1200,"totalSessions":1,"scheduledDays":1,"completedDays":1,"missedDays":0,"completionPercentage":100.0,"currentStreak":2,"longestStreak":1}""") { reports.month(7, "2026-10") }
        assertEquals(18, server.requestCount)
    }

    @Test fun structuredValidationMessagesReachTheUser() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
            .setBody("""{"timestamp":"2026-10-05T09:00:00","status":400,"error":"Invalid Activity","message":"Start date is required","path":"/activities"}"""))
        val result = activities.create(request) as ApiResult.Failure
        assertEquals(400, result.error.statusCode)
        assertEquals("Start date is required", result.error.message)
        assertFalse(result.error.uncertainWrite)
        assertEquals(1, server.requestCount)
    }

    @Test fun malformedSuccessJsonIsAnErrorAndIsNeverRetried() = runBlocking {
        server.enqueue(MockResponse().setBody("{broken"))
        val result = activities.create(request) as ApiResult.Failure
        assertTrue(result.error.uncertainWrite)
        assertTrue(result.error.message.contains("Refresh before trying again"))
        assertEquals(1, server.requestCount)
    }

    @Test fun httpErrorsAreReadableAndServerStackTracesAreHidden() = runBlocking {
        listOf(401, 403, 404, 409, 422, 500).forEach { code ->
            server.enqueue(MockResponse().setResponseCode(code).setBody("""{"status":$code,"message":"java.lang.NullPointerException Cannot invoke something","trace":"secret stack"}"""))
            val result = activities.get(7) as ApiResult.Failure
            assertEquals(code, result.error.statusCode)
            assertFalse(result.error.message.contains("NullPointerException"))
            assertFalse(result.error.message.contains("secret"))
            assertFalse(result.error.uncertainWrite)
        }
    }

    private suspend fun checkRequest(
        method: String,
        path: String,
        responseBody: String,
        status: Int = 200,
        expectedBody: JsonElement? = null,
        call: suspend () -> ApiResult<*>,
    ) {
        val response = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json")
        if (status != 204) response.setBody(responseBody)
        server.enqueue(response)
        val result = call()
        assertTrue("$method $path returned $result", result is ApiResult.Success)
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("Missing $method $path", recorded)
        assertEquals(method, recorded?.method)
        assertEquals("/reality/$path", recorded?.path)
        if (expectedBody != null) {
            assertTrue(recorded?.getHeader("Content-Type").orEmpty().startsWith("application/json"))
            assertEquals(expectedBody, json.parseToJsonElement(requireNotNull(recorded).body.readUtf8()))
        }
    }
}

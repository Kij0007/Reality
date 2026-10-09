package com.reality.android.core.network

import com.reality.android.data.remote.dto.ErrorDto
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

@Singleton
class ApiExecutor @Inject constructor(private val json: Json) {
    suspend fun <T : Any> execute(
        mutation: Boolean = false,
        call: suspend () -> Response<T>,
    ): ApiResult<T> = protect(mutation) {
        val response = call()
        if (!response.isSuccessful) ApiResult.Failure(httpError(response, mutation))
        else response.body()?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure(error("The server returned an empty response.", mutation))
    }

    suspend fun executeUnit(
        call: suspend () -> Response<Unit>,
    ): ApiResult<Unit> = protect(true) {
        val response = call()
        if (response.isSuccessful) ApiResult.Success(Unit)
        else ApiResult.Failure(httpError(response, true))
    }

    private suspend fun <T> protect(
        mutation: Boolean,
        block: suspend () -> ApiResult<T>,
    ): ApiResult<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SocketTimeoutException) {
        ApiResult.Failure(error("The Reality server took too long to respond.", mutation))
    } catch (_: SerializationException) {
        ApiResult.Failure(error("The server returned an unexpected response. Check that this app matches your backend version.", mutation))
    } catch (_: IOException) {
        ApiResult.Failure(error("Unable to connect to the Reality server. Check your connection and backend address.", mutation))
    } catch (_: Exception) {
        ApiResult.Failure(error("The request could not be completed. Check your backend settings and try again.", mutation))
    }

    private fun httpError(response: Response<*>, mutation: Boolean): AppError {
        val status = response.code()
        val raw = runCatching { response.errorBody()?.use { it.string() } }.getOrNull().orEmpty()
        val structured = runCatching { json.decodeFromString<ErrorDto>(raw) }.getOrNull()
        // Never display a server stack trace or HTML error document.
        val readable = structured?.message?.trim()?.takeIf {
            it.length in 1..500 && !it.contains("\n") && !it.contains("<") &&
                !it.contains("Exception") && !it.contains("java.") &&
                !it.contains("Cannot invoke") && !it.contains("org.springframework")
        }
        val message = when {
            status >= 500 -> "The Reality server could not complete this request. Try refreshing; if it persists, check the backend log."
            readable != null -> readable
            status == 400 || status == 422 -> "The server rejected the input. Check the entered values."
            status == 401 -> "The server requires authentication. This backend version does not expose an app login API."
            status == 403 -> "The server denied access to this operation."
            status == 404 -> "The requested record or endpoint was not found. Refresh your data and check the backend address."
            status == 409 -> "The operation conflicts with the current server state. Refresh before trying again."
            else -> "The Reality server returned HTTP $status."
        }
        return error(message, mutation && status >= 500, status)
    }

    private fun error(message: String, uncertain: Boolean, status: Int? = null) = AppError(
        message = if (uncertain) "$message The server may have received the change. Refresh before trying again." else message,
        statusCode = status,
        uncertainWrite = uncertain,
    )
}

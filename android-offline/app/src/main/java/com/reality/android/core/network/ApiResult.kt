package com.reality.android.core.network

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: AppError) : ApiResult<Nothing>
}

data class AppError(
    val message: String,
    val statusCode: Int? = null,
    val uncertainWrite: Boolean = false,
)

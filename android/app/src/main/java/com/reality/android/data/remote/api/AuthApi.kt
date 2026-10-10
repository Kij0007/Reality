package com.reality.android.data.remote.api

import com.reality.android.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/register") suspend fun register(@Body request: RegisterRequest): Response<AuthDto>
    @POST("api/auth/login") suspend fun login(@Body request: LoginRequest): Response<AuthDto>
    @GET("api/auth/me") suspend fun me(): Response<UserDto>
    @POST("api/auth/logout") suspend fun logout(): Response<Unit>
    @GET("api/health") suspend fun health(): Response<HealthDto>
}

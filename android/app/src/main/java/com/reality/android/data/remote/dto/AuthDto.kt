package com.reality.android.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class RegisterRequest(val username: String, val displayName: String, val password: String)
@Serializable data class UserDto(val id: Long, val username: String, val displayName: String, val createdAt: String)
@Serializable data class AuthDto(val token: String, val expiresAt: String, val user: UserDto)
@Serializable data class HealthDto(val status: String)
@Serializable data class StoredSession(val backendUrl: String, val auth: AuthDto)

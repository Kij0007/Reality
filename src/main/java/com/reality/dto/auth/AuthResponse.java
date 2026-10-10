package com.reality.dto.auth;

import java.time.Instant;

public record AuthResponse(String token, Instant expiresAt, UserResponse user) { }

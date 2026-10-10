package com.reality.dto.auth;

import java.time.Instant;

public record UserResponse(Long id, String username, String displayName, Instant createdAt) { }

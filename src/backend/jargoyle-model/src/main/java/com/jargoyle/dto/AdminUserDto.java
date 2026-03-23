package com.jargoyle.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin-facing user representation with timestamps.
 * Separate from UserDto (used for /api/auth/me) because admin views
 * need createdAt and lastLoginAt, which aren't exposed publicly.
 */
public record AdminUserDto(
    UUID id,
    String email,
    String displayName,
    String oauthProvider,
    String role,
    boolean enabled,
    Instant createdAt,
    Instant lastLoginAt
) {}

package com.jargoyle.dto;

import java.util.UUID;

/**
 * Public-facing user representation for the /api/auth/me endpoint.
 * Omits internal fields like oauthSubject, createdAt, and lastLoginAt.
 */
public record UserDto(UUID id, String email, String displayName, String oauthProvider) {
}

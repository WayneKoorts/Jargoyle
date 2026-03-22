package com.jargoyle.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for updating a user via the admin API.
 *
 * Only `role` is required. `displayName`, `email`, and `enabled` are optional —
 * when null they're left unchanged. Jackson deserialises missing JSON
 * properties as null, so the client only sends what it wants to change.
 */
public record AdminUserUpdateRequest(
    @NotNull(message = "Role must not be null") String role,
    String displayName,
    String email,
    Boolean enabled
) {}

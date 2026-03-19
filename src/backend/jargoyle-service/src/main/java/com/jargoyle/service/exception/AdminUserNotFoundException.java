package com.jargoyle.service.exception;

import java.util.UUID;

/**
 * Thrown when an admin operation targets a user ID that doesn't exist.
 * Mapped to 404 in GlobalExceptionHandler.
 *
 * Separate from UserNotFoundException (in service.security) which maps to
 * 401 for OAuth lookup failures — different HTTP semantics, different purpose.
 */
public class AdminUserNotFoundException extends RuntimeException {

    private static final String ERROR_FORMAT = "User with ID \"%s\" not found";

    public AdminUserNotFoundException(UUID userId) {
        super(String.format(ERROR_FORMAT, userId));
    }
}

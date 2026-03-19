package com.jargoyle.service.exception;

/**
 * Thrown when an admin operation violates a business rule, such as
 * self-deletion or demoting the last remaining admin.
 * Mapped to 409 Conflict in GlobalExceptionHandler.
 */
public class AdminOperationException extends RuntimeException {

    public AdminOperationException(String message) {
        super(message);
    }
}

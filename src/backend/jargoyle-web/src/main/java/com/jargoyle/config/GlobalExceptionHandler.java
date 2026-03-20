package com.jargoyle.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.jargoyle.service.exception.AdminOperationException;
import com.jargoyle.service.exception.AdminUserNotFoundException;
import com.jargoyle.service.exception.ConversationNotFoundException;
import com.jargoyle.service.exception.DocumentNotFoundException;
import com.jargoyle.service.exception.DocumentNotReadyException;
import com.jargoyle.service.security.UserNotFoundException;
import com.jargoyle.service.storage.StorageLoadException;
import com.jargoyle.service.storage.StorageSaveException;

import java.util.concurrent.CompletionException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(401).body(ex.getMessage());
    }

    @ExceptionHandler(AdminUserNotFoundException.class)
    public ResponseEntity<String> handleAdminUserNotFound(AdminUserNotFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(AdminOperationException.class)
    public ResponseEntity<String> handleAdminOperation(AdminOperationException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<String> handleDocumentNotFound(DocumentNotFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<String> handleConversationNotFound(ConversationNotFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(DocumentNotReadyException.class)
    public ResponseEntity<String> handleDocumentNotReady(DocumentNotReadyException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }

    @ExceptionHandler(StorageSaveException.class)
    public ResponseEntity<String> handleStorageSaveException(StorageSaveException ex) {
        log.error("Error saving to storage", ex);
        return ResponseEntity.internalServerError().body(ex.getMessage());
    }

    @ExceptionHandler(StorageLoadException.class)
    public ResponseEntity<String> handleStorageLoadException(StorageLoadException ex) {
        log.error("Error loading from storage", ex);
        return ResponseEntity.internalServerError().body("Failed to retrieve document from storage.");
    }

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<String> handleCompletionException(CompletionException ex) {
        // Unwrap the CompletionException and re-dispatch to the appropriate handler
        // if the cause is a known exception type.
        var cause = ex.getCause();
        if (cause instanceof StorageSaveException sse) {
            return handleStorageSaveException(sse);
        }
        if (cause instanceof StorageLoadException sle) {
            return handleStorageLoadException(sle);
        }
        log.error("Unhandled async error", ex);
        return ResponseEntity.internalServerError().body("An unexpected error occurred.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException ex) {
        var messages = ex.getBindingResult().getAllErrors().stream()
            .map(ObjectError::getDefaultMessage)
            .toList();

        return ResponseEntity.badRequest().body(String.join("; ", messages));
    }

}

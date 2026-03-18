package com.jargoyle.service.exception;

import java.util.UUID;

public class ConversationNotFoundException extends RuntimeException {

    private static final String errorFormat = "Conversation with ID \"%s\" not found";

    public ConversationNotFoundException(String message) {
        super(message);
    }

    public ConversationNotFoundException(UUID conversationId) {
        super(String.format(errorFormat, conversationId));
    }

    public ConversationNotFoundException(UUID conversationId, Throwable cause) {
        super(String.format(errorFormat, conversationId), cause);
    }
}

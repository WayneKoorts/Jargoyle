package com.jargoyle.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
    UUID id,
    UUID documentId,
    String title,
    int messageCount,
    Instant createdAt,
    Instant lastMessageAt
) {}

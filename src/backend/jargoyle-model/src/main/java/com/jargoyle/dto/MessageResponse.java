package com.jargoyle.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
    UUID id,
    String role,
    String content,
    List<SourceChunkReference> sourceChunks,
    Instant createdAt
) {}

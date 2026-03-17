package com.jargoyle.dto;

import java.util.UUID;

public record SourceChunkReference(
    UUID chunkId,
    int chunkIndex,
    String preview
) {}

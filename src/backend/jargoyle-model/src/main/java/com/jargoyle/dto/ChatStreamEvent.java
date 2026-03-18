package com.jargoyle.dto;

import java.util.List;

/**
 * SSE event payload, sent as 'text/event-stream'.
 */
public record ChatStreamEvent(
    String type,
    String content,
    String messageId,
    List<SourceChunkReference> sourceChunks
) {
    public static ChatStreamEvent token(String content) {
        return new ChatStreamEvent("TOKEN", content, null, null);
    }

    public static ChatStreamEvent complete(String messageId, List<SourceChunkReference> sourceChunks) {
        return new ChatStreamEvent("COMPLETE", null, messageId, sourceChunks);
    }

    public static ChatStreamEvent error(String message) {
        return new ChatStreamEvent("ERROR", message, null, null);
    }
}

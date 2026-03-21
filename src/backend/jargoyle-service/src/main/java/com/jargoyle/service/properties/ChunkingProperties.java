package com.jargoyle.service.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration values that control how extracted text is split into chunks.
 *
 * @param targetTokens approximate target size for each chunk
 * @param overlapTokens approximate overlap carried from one chunk to the next
 * @param minTokens minimum size before a small section is merged with a neighbour
 */
@ConfigurationProperties(prefix = "jargoyle.rag.chunk")
public record ChunkingProperties(
    int targetTokens,
    int overlapTokens,
    int minTokens
) {
    /**
     * Applies safe defaults and guards against invalid chunk-size combinations.
     */
    public ChunkingProperties {
        if (targetTokens <= 0) targetTokens = 500;
        if (overlapTokens < 0) overlapTokens = 50;
        if (minTokens <= 0) minTokens = 100;

        if (overlapTokens >= targetTokens) {
            overlapTokens = Math.max(1, targetTokens / 10);
        }

        if (minTokens >= targetTokens) {
            minTokens = Math.max(1, targetTokens / 2);
        }
    }
}
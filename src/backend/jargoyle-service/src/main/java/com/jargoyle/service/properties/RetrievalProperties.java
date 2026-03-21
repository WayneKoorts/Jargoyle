package com.jargoyle.service.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration values that control how document chunks are retrieved for
 * RAG-augmented chat.
 *
 * @param topK the number of most-similar chunks to retrieve per query
 */
@ConfigurationProperties(prefix = "jargoyle.rag.retrieval")
public record RetrievalProperties(int topK) {

    /**
     * Applies safe defaults for invalid or missing values.
     */
    public RetrievalProperties {
        if (topK <= 0) topK = 5;
    }
}

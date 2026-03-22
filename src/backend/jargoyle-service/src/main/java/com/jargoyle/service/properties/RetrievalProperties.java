package com.jargoyle.service.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration values that control how document chunks are retrieved for
 * RAG-augmented chat.
 *
 * <p>Retrieval uses a <em>token-budgeted</em> strategy: chunks are fetched
 * from the database ordered by cosine similarity, then as many as fit within
 * {@code maxContextTokens} are included in the prompt. For small documents
 * this means the LLM receives the full document content; for large documents
 * the most relevant chunks are prioritised.
 *
 * @param maxContextTokens maximum total tokens of chunk content to include
 *                         in the prompt (default 100,000)
 * @param maxChunks        safety cap on the number of database rows fetched
 *                         per query (default 200)
 */
@ConfigurationProperties(prefix = "jargoyle.rag.retrieval")
public record RetrievalProperties(int maxContextTokens, int maxChunks) {

    /**
     * Applies safe defaults for invalid or missing values.
     */
    public RetrievalProperties {
        if (maxContextTokens <= 0) maxContextTokens = 100_000;
        if (maxChunks <= 0) maxChunks = 200;
    }
}

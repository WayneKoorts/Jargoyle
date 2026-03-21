package com.jargoyle.service.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration values that control the chat prompt's token budget and
 * conversation history window.
 *
 * @param maxHistoryMessages maximum number of recent messages to load from the database
 * @param maxHistoryTokens   approximate token budget for the conversation history section
 *                           of the prompt
 * @param maxResponseTokens  token headroom reserved for the LLM's response
 */
@ConfigurationProperties(prefix = "jargoyle.rag.chat")
public record ChatProperties(
    int maxHistoryMessages,
    int maxHistoryTokens,
    int maxResponseTokens
) {

    /**
     * Applies safe defaults for invalid or missing values.
     */
    public ChatProperties {
        if (maxHistoryMessages <= 0) maxHistoryMessages = 10;
        if (maxHistoryTokens <= 0) maxHistoryTokens = 2000;
        if (maxResponseTokens <= 0) maxResponseTokens = 4000;
    }
}

package com.jargoyle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Generates short, descriptive conversation titles using the LLM.
 *
 * <p>Called after the first message exchange in a conversation to replace the
 * default "New conversation" placeholder with a meaningful title derived from
 * the user's opening question. The title helps users distinguish between
 * conversations in the sidebar.
 *
 * <p>This follows the same single-purpose service pattern as
 * {@link SummaryGenerationService} — each service wraps one focused LLM call
 * with its own system prompt and {@link ChatClient} instance.
 *
 * @see SummaryGenerationService
 */
@Service
public class TitleGenerationService {

    private static final Logger log = LoggerFactory.getLogger(TitleGenerationService.class);

    private final ChatClient chatClient;

    /**
     * Creates a new {@code TitleGenerationService}.
     *
     * <p>The {@link ChatClient} is built with a default system prompt that
     * instructs the model to produce a concise conversation title. A separate
     * {@code ChatClient} instance is used (rather than sharing with
     * {@link ChatService}) because the system prompt is fixed and specific
     * to title generation.
     *
     * @param chatClientBuilder Spring AI's auto-configured builder
     */
    public TitleGenerationService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        You are a conversation title generator. Given a user's question \
                        about a document, produce a short, descriptive title (at most 8 words) \
                        that captures the topic. Return only the title text — no quotes, no \
                        trailing punctuation, no preamble.""")
                .build();
    }

    /**
     * Generates a concise title from the user's opening question.
     *
     * <p>The title is trimmed and any surrounding quotation marks are stripped
     * (some models wrap their response in quotes despite instructions not to).
     *
     * @param userQuestion the first question the user asked in the conversation
     * @return a short title string, or {@code null} if the model returned
     *         an empty or blank response
     */
    public String generateTitle(String userQuestion) {
        log.debug("Generating conversation title for question: \"{}\"",
                userQuestion.length() > 50
                        ? userQuestion.substring(0, 50) + "..."
                        : userQuestion);

        var response = chatClient.prompt()
                .user(userQuestion)
                .call()
                .content();

        if (response == null || response.isBlank()) {
            log.warn("Title generation returned empty response");
            return null;
        }

        var title = sanitise(stripSurroundingQuotes(response.trim()));

        if (title.isBlank()) {
            log.warn("Title generation returned only non-printable characters");
            return null;
        }

        log.debug("Generated conversation title: \"{}\"", title);
        return title;
    }

    /**
     * Strips characters outside the standard printable range. LLMs
     * occasionally produce characters from extended Unicode blocks (e.g.
     * Latin Extended Additional) that render as garbled glyphs in the UI.
     *
     * <p>Keeps ASCII printable characters ({@code U+0020–U+007E}) and common
     * accented Latin characters ({@code U+00C0–U+00FF}), which covers
     * English and most Western European text. Consecutive whitespace left
     * by removed characters is collapsed to a single space.
     */
    private String sanitise(String text) {
        return text
                .replaceAll("[^\\x20-\\x7E\\u00C0-\\u00FF]", " ")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    /**
     * Strips surrounding double or single quotes that some models add despite
     * instructions not to. For example, {@code "My Title"} becomes
     * {@code My Title}.
     */
    private String stripSurroundingQuotes(String text) {
        if (text.length() >= 2) {
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return text.substring(1, text.length() - 1);
            }
        }
        return text;
    }
}

package com.jargoyle.service.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;

/**
 * A {@link StructuredOutputConverter} that tolerates duplicate JSON keys in
 * LLM-generated responses.
 *
 * <p>LLMs sometimes emit the same key twice (e.g. at the start and end of an
 * object). Jackson cannot deserialise such JSON into Java records because records
 * have no setter for the second occurrence to overwrite — the attempt results in
 * "No fallback setter/field defined for creator property".</p>
 *
 * <p>This converter works around the issue by first parsing the raw JSON into a
 * tree ({@link JsonNode}), which collapses duplicate keys (last value wins, per
 * RFC 8259 §4), then re-serialising it to clean JSON before delegating to the
 * standard {@link BeanOutputConverter}.</p>
 *
 * @param <T> the target type to deserialise into
 */
public class DuplicateKeyTolerantOutputConverter<T> implements StructuredOutputConverter<T> {

    private final BeanOutputConverter<T> delegate;
    private final ObjectMapper objectMapper;

    /**
     * Creates a converter for the given target type.
     *
     * @param type the class to deserialise JSON into
     */
    public DuplicateKeyTolerantOutputConverter(Class<T> type) {
        this.delegate = new BeanOutputConverter<>(type);
        this.objectMapper = new ObjectMapper();
    }

    /** {@inheritDoc} */
    @Override
    public String getFormat() {
        return delegate.getFormat();
    }

    /**
     * Converts the given JSON text to the target type, tolerating duplicate keys.
     *
     * @param source the raw JSON text (may contain duplicate keys or markdown fences)
     * @return the deserialised object
     */
    @Override
    public T convert(String source) {
        try {
            // Strip markdown code fences that LLMs sometimes wrap around JSON.
            String stripped = stripMarkdownCodeBlock(source);
            // Parse into a tree — duplicate keys are collapsed (last value wins).
            JsonNode tree = objectMapper.readTree(stripped);
            // Re-serialise to clean, deduplicated JSON.
            String deduplicatedJson = objectMapper.writeValueAsString(tree);
            return delegate.convert(deduplicatedJson);
        } catch (JsonProcessingException e) {
            // If tree parsing fails for any reason, fall back to the delegate
            // which has its own error handling and markdown stripping.
            return delegate.convert(source);
        }
    }

    /**
     * Strips markdown code fences ({@code ```json ... ```}) that LLMs sometimes
     * wrap around JSON responses.
     */
    private String stripMarkdownCodeBlock(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}

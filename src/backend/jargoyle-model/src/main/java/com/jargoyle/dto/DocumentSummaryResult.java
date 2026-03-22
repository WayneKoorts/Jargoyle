package com.jargoyle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Internal DTO for passing the LLM's structured summary response between services.
 * Not exposed via the API — {@code DocumentProcessingService} unpacks the fields
 * onto the {@link com.jargoyle.entity.Document Document} and
 * {@link com.jargoyle.entity.DocumentSummary DocumentSummary} entities.
 *
 * <p>Explicit {@code @JsonProperty} annotations are required to work around a
 * Jackson 2.20 regression where record creator properties can fail deserialisation
 * with "No fallback setter/field defined" when processed by Spring AI's
 * {@code BeanOutputConverter}.</p>
 */
public record DocumentSummaryResult(
        @JsonProperty("plainSummary") String plainSummary,
        @JsonProperty("keyFacts") KeyFacts keyFacts,
        @JsonProperty("flaggedTerms") List<FlaggedTerm> flaggedTerms,
        @JsonProperty("title") String title,
        @JsonProperty("documentType") String documentType
) {}

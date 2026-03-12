package com.jargoyle.dto;

import java.util.List;

/**
 * Internal DTO for passing the LLM's structured summary response between services.
 * Not exposed via the API — {@code DocumentProcessingService} unpacks the fields
 * onto the {@link com.jargoyle.entity.Document Document} and
 * {@link com.jargoyle.entity.DocumentSummary DocumentSummary} entities.
 */
public record DocumentSummaryResult(
        String plainSummary,
        KeyFacts keyFacts,
        List<FlaggedTerm> flaggedTerms,
        String title,
        String documentType
) {}

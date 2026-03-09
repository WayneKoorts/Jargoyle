package com.jargoyle.dto;

/**
 * Internal DTO for passing the LLM's structured summary response between services.
 * Not exposed via the API — {@code DocumentProcessingService} unpacks the fields
 * onto the {@link com.jargoyle.entity.Document Document} and
 * {@link com.jargoyle.entity.DocumentSummary DocumentSummary} entities.
 */
public record DocumentSummaryResult(
    String plainSummary,
    String keyFacts,       // JSON
    String flaggedTerms,   // JSON
    String title,          // LLM-generated title
    String documentType    // LLM-classified type
) {}

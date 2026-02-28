package com.jargoyle.dto;

public record DocumentSummaryResponse(
    String plainSummary,
    String keyFacts,
    String flaggedTerms
) {}

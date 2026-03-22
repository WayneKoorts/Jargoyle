package com.jargoyle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Structured key facts extracted from a document, grouped by category. */
public record KeyFacts(
        @JsonProperty("amounts") List<KeyFact> amounts,
        @JsonProperty("dates") List<KeyFact> dates,
        @JsonProperty("parties") List<KeyFact> parties
) {}

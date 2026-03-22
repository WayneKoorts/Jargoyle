package com.jargoyle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A single key fact extracted from a document. */
public record KeyFact(
        @JsonProperty("label") String label,
        @JsonProperty("value") String value,
        @JsonProperty("context") String context
) {}


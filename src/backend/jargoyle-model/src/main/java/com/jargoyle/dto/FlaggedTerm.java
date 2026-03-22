package com.jargoyle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A jargon term flagged by the LLM, paired with a plain-English definition. */
public record FlaggedTerm(
        @JsonProperty("term") String term,
        @JsonProperty("definition") String definition
) {}

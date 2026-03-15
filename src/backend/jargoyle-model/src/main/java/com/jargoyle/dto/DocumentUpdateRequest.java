package com.jargoyle.dto;

import java.util.Optional;

import com.jargoyle.validation.AtLeastOneRequired;

import jakarta.validation.constraints.Size;

@AtLeastOneRequired({ "title", "documentType" })
public record DocumentUpdateRequest(
    // Assumes id is provided in the URL.
    Optional<@Size(max = 255) String> title,
    Optional<String> documentType
) {}

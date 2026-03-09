package com.jargoyle.dto;

import jakarta.validation.constraints.Size;

public record DocumentUpdateRequest(
    // Assumes id is provided in the URL.
    @Size(max = 255)
    String title,
    String documentType
) {}

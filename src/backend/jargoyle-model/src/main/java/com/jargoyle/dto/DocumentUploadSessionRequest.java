package com.jargoyle.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentUploadSessionRequest(
    @NotBlank(message = "inputType is required")
    String inputType,
    String originalFilename,
    String text
) {}

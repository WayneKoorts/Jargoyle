package com.jargoyle.dto;

public record ProcessingStatusEvent(
    String status,
    String step,
    String errorMessage
) {}

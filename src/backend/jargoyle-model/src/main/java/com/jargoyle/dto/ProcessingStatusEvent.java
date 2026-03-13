package com.jargoyle.dto;

public record ProcessingStatusEvent(
    String status,
    String step,
    String errorMessage
) {
    public ProcessingStatusEvent(String status, String step) {
        this(status, step, null);
    }

    public static ProcessingStatusEvent processing(String step) {
        return new ProcessingStatusEvent("PROCESSING", step, null);
    }

    public static ProcessingStatusEvent ready() {
        return new ProcessingStatusEvent("READY", "Complete", null);
    }

    public static ProcessingStatusEvent failed(String errorMessage) {
        return new ProcessingStatusEvent("FAILED", "Failed", errorMessage);
    }
}

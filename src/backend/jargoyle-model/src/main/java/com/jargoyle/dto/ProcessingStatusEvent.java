package com.jargoyle.dto;

import com.jargoyle.entity.DocumentStatus;

public record ProcessingStatusEvent(
    String status,
    String step,
    String errorMessage
) {
    public ProcessingStatusEvent(String status, String step) {
        this(status, step, null);
    }

    public static ProcessingStatusEvent pendingUpload() {
        return new ProcessingStatusEvent("PENDING_UPLOAD", "Waiting for upload...", null);
    }

    public static ProcessingStatusEvent uploading() {
        return new ProcessingStatusEvent("UPLOADING", "Uploading document...", null);
    }

    public static ProcessingStatusEvent queued() {
        return new ProcessingStatusEvent("QUEUED", "Queued for processing...", null);
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

    public static ProcessingStatusEvent fromDocumentStatus(DocumentStatus status, String errorMessage) {
        return switch (status) {
            case PENDING_UPLOAD -> pendingUpload();
            case UPLOADING -> uploading();
            case QUEUED -> queued();
            case PROCESSING -> processing("Processing document...");
            case READY -> ready();
            case FAILED -> failed(errorMessage != null ? errorMessage : "Document processing failed.");
        };
    }
}

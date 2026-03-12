package com.jargoyle.service;

import com.jargoyle.dto.ProcessingStatusEvent;

import java.util.UUID;

public interface DocumentStatusNotifier {
    void notify(UUID documentId, ProcessingStatusEvent event);
    void complete(UUID documentId);
}

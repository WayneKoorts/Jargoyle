package com.jargoyle.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class DocumentProcessingDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingDispatcher.class);

    private final TaskExecutor documentProcessingExecutor;
    private final DocumentProcessingService documentProcessingService;

    public DocumentProcessingDispatcher(
            @Qualifier("documentProcessingExecutor") TaskExecutor documentProcessingExecutor,
            DocumentProcessingService documentProcessingService) {

        this.documentProcessingExecutor = documentProcessingExecutor;
        this.documentProcessingService = documentProcessingService;
    }

    public void dispatch(UUID documentId) {
        log.debug("Dispatching document {} for background processing", documentId);
        documentProcessingExecutor.execute(() -> {
            try {
                documentProcessingService.processDocument(documentId);
            } catch (Exception ex) {
                // Last line of defence: if the catch-all in processDocument itself
                // throws (e.g. DB error while marking FAILED), we log here rather
                // than letting it vanish into the thread pool's uncaught handler.
                log.error("Unhandled exception escaped processing pipeline for document {}", documentId, ex);
            }
        });
    }
}
package com.jargoyle.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.jargoyle.repository.DocumentRepository;

@Service
@Async("documentProcessingExecutor")
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final TextExtractionService textExtractionService;
    // private final SummaryGenerationService summaryGenerationService;

    public DocumentProcessingService(
            DocumentRepository documentRepository,
            TextExtractionService textExtractionService) {
        this.documentRepository = documentRepository;
        this.textExtractionService = textExtractionService;
    }

}

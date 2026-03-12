package com.jargoyle.service;

import com.jargoyle.dto.DocumentSummaryResult;
import com.jargoyle.entity.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SummaryGenerationService {

    private final ChatClient chatClient;
    private static final Logger log = LoggerFactory.getLogger(SummaryGenerationService.class);

    public SummaryGenerationService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem(
                 """
                    You are a document analysis assistant. You inspect complex legal or policy documents and are able
                    to provide plain language summaries, extract key facts, and categorise given documents so that
                    laypeople can understand their contents.
                    """)
                .build();
    }

    public DocumentSummaryResult generateDocumentSummary(String extractedText) {
        log.debug("Generating document summary for \"{}...\"",
                extractedText.substring(0, (extractedText.length() < 10 ? extractedText.length() - 1 : 10)));

        DocumentSummaryResult result = chatClient.prompt()
                .user(u -> u.text("""
                        Analyse the following document and provide:
                        - A plain-English summary
                        - Key facts (amounts, dates, deadlines, parties)
                        - Flagged jargon terms with definitions
                        - A short descriptive title
                        - A document type classification (one of: {types})
                        
                        Document text:
                        {text}
                        """)
                        .param("types", String.join(", ", DocumentType.names()))
                        .param("text", extractedText))
                .call()
                .entity(DocumentSummaryResult.class);

        if (result != null) {
            log.debug("Generated document summary for document titled \"{}\"", result.title());
        } else {
            log.warn("Document summary generation returned null.");
        }

        return result;
    }

}

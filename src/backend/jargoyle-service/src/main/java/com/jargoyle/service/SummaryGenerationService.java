package com.jargoyle.service;

import com.jargoyle.dto.DocumentSummaryResult;
import com.jargoyle.entity.DocumentType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SummaryGenerationService {

    private final ChatClient chatClient;

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

        return result;
    }

}

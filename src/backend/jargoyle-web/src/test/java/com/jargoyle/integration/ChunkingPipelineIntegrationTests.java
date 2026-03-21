package com.jargoyle.integration;

import com.jargoyle.entity.*;
import com.jargoyle.repository.DocumentChunkRepository;
import com.jargoyle.repository.DocumentRepository;
import com.jargoyle.repository.DocumentSummaryRepository;
import com.jargoyle.repository.UserRepository;
import com.jargoyle.service.DocumentProcessingService;
import com.jargoyle.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test for the full document processing pipeline with a real
 * PostgreSQL database (pgvector). Verifies that a document flows through
 * text extraction, chunking, embedding generation, and summary generation,
 * ending in {@link DocumentStatus#READY} with all artefacts persisted.
 *
 * <p>External API dependencies ({@link ChatModel}, {@link EmbeddingModel})
 * are mocked so the test runs without network access. The database layer
 * is real — Flyway migrations run against a Testcontainers PostgreSQL
 * instance with the pgvector extension.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@TestPropertySource(properties = {
        // Clear the auto-config exclusions from application.yml so that
        // DataSource, Hibernate, and Flyway are active for the test.
        "spring.autoconfigure.exclude=",
        // Allow bean overriding to handle IDE classpath differences. VS Code
        // merges test sources from all modules, so TestJpaConfiguration from
        // jargoyle-repository and JargoyleApplication both register repositories.
        "spring.main.allow-bean-definition-overriding=true",
        // Dummy OAuth2 credentials — not used but required by auto-config.
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        // Dummy API key — the real model is mocked, but auto-config reads this.
        "spring.ai.openai.api-key=test-key",
        // Hibernate validates that entities match the Flyway-managed schema.
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        // Use small chunking parameters so short test text produces multiple chunks.
        "jargoyle.rag.chunk.target-tokens=40",
        "jargoyle.rag.chunk.overlap-tokens=8",
        "jargoyle.rag.chunk.min-tokens=10"
})
class ChunkingPipelineIntegrationTests {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // External API mocks — replace the auto-configured beans.
    @MockitoBean
    ChatModel chatModel;

    @MockitoBean
    EmbeddingModel embeddingModel;

    // No storage profile is active, so no StorageService bean exists.
    // The mock satisfies the constructor dependency in DocumentProcessingService.
    @MockitoBean
    StorageService storageService;

    @Autowired
    DocumentProcessingService documentProcessingService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    DocumentRepository documentRepository;

    @Autowired
    DocumentChunkRepository documentChunkRepository;

    @Autowired
    DocumentSummaryRepository documentSummaryRepository;

    // Enough text to produce several chunks with the small test parameters.
    private static final String TEST_TEXT = """
            Section 1: Introduction
            This agreement establishes the terms and conditions for the provision
            of professional consulting services between the parties named herein.
            The effective date of this agreement is the first day of January 2026.

            Section 2: Payment Terms
            The client shall pay a monthly fee of five hundred pounds sterling for
            the services described in Schedule A attached to this agreement.
            Payment is due on the first business day of each calendar month.

            Section 3: Termination
            Either party may terminate this agreement by providing thirty days of
            written notice to the other party. Upon termination, all outstanding
            payments shall become immediately due and payable in full.
            """;

    private static final String SUMMARY_JSON = """
            {
                "title": "Service Agreement",
                "documentType": "CONTRACT",
                "plainSummary": "An agreement for the provision of consulting services.",
                "keyFacts": { "amounts": [], "dates": [], "parties": [] },
                "flaggedTerms": []
            }
            """;

    @BeforeEach
    void setUp() {
        // ChatModel returns a valid summary response when called.
        var chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(SUMMARY_JSON))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        // EmbeddingModel returns 1536-dimensional vectors for each text in the batch.
        // The actual values don't matter — we just verify embeddings are stored.
        when(embeddingModel.embed(ArgumentMatchers.<String>anyList()))
                .thenAnswer(invocation -> {
                    List<String> texts = invocation.getArgument(0);
                    return texts.stream()
                            .map(t -> {
                                var embedding = new float[1536];
                                embedding[0] = 0.1f;
                                return embedding;
                            })
                            .toList();
                });
    }

    @Test
    void processDocument_textDocument_producesChunksWithEmbeddingsAndSummary() {
        // Arrange — create the User → Document entity chain.
        var user = new User();
        user.setEmail("pipeline-test@example.com");
        user.setDisplayName("Pipeline Test User");
        user.setOauthProvider("google");
        user.setOauthSubject("pipeline-test-subject");
        user = userRepository.save(user);

        var document = new Document();
        document.setUser(user);
        document.setInputType(InputType.TEXT);
        document.setOriginalFilename("agreement.txt");
        document.setStorageKey("not-used-for-text");
        document.setStatus(DocumentStatus.UPLOADING);
        document.setExtractedText(TEST_TEXT);
        document = documentRepository.save(document);
        var documentId = document.getId();

        // Act — run the full pipeline synchronously.
        documentProcessingService.processDocument(documentId);

        // Assert — verify the document reached READY with all artefacts persisted.
        var updatedDocument = documentRepository.findById(documentId).orElseThrow();
        assertThat(updatedDocument.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(updatedDocument.getTitle()).isEqualTo("Service Agreement");
        assertThat(updatedDocument.getDocumentType()).isEqualTo(DocumentType.CONTRACT);

        // Verify chunks were created in order.
        var chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndex(documentId);
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks).extracting(DocumentChunk::getChunkIndex)
                .isSorted();

        // Verify every chunk has content, a token count, and a 1536-dimensional embedding.
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getContent()).isNotBlank();
            assertThat(chunk.getTokenCount()).isPositive();
            assertThat(chunk.getEmbedding()).isNotNull();
            assertThat(chunk.getEmbedding()).hasSize(1536);
        });

        // Verify a document summary was persisted.
        var summary = documentSummaryRepository.findByDocumentId(documentId);
        assertThat(summary).isPresent();
        assertThat(summary.get().getPlainSummary())
                .isEqualTo("An agreement for the provision of consulting services.");
    }
}

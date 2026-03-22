package com.jargoyle.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.jargoyle.entity.Document;
import com.jargoyle.entity.DocumentChunk;
import com.jargoyle.entity.DocumentStatus;
import com.jargoyle.entity.DocumentType;
import com.jargoyle.entity.InputType;
import com.jargoyle.entity.User;

import jakarta.persistence.EntityManager;

// Boots only the JPA slice: entities, repositories, Hibernate, Flyway.
// No web layer, no security, no service beans — keeps the test fast.
@DataJpaTest
// Tells Spring Boot NOT to replace the DataSource with an embedded H2.
// We want it to use the real PostgreSQL container instead.
@AutoConfigureTestDatabase(replace = Replace.NONE)
// Activates Testcontainers lifecycle management — starts/stops containers
// aligned with the test class lifecycle.
@Testcontainers
class DocumentChunkRepositoryTests {

    // A single PostgreSQL container shared across all test methods in this class.
    // The "static" is important — without it, Testcontainers would start a new
    // container for every single test method (very slow).
    // We use the pgvector image because our migrations enable the vector extension.
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    // Injects the container's randomly-assigned connection details into Spring's
    // environment *before* the application context starts. This is how the
    // DataSource ends up pointing at the container instead of a real database.
    // Think of it like overriding appsettings.json values in a .NET test fixture.
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.locations", () -> "filesystem:../jargoyle-web/src/main/resources/db/migration");
    }

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    // EntityManager lets us persist the parent entities (User, Document) that
    // our chunks depend on via foreign keys. We could inject UserRepository and
    // DocumentRepository, but EntityManager is more direct for test setup.
    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private Document testDocument1;
    private Document testDocument2;

    @BeforeEach
    void setUp() {
        // Build the entity graph needed to satisfy the foreign key chain:
        // User -> Document -> DocumentChunk
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setDisplayName("Test User");
        testUser.setOauthProvider("google");
        testUser.setOauthSubject("test-subject-123");
        entityManager.persist(testUser);

        testDocument1 = createDocument("Document 1");
        testDocument2 = createDocument("Document 2");

        // Flush forces Hibernate to execute the INSERTs now, so the rows exist
        // in the database before our test queries run. Without this, Hibernate
        // might defer the INSERTs and the native SQL query wouldn't see them.
        entityManager.flush();
    }

    @Test
    void findSimilarChunks_returnsChunksOrderedBySimilarity() {
        // Arrange
        // We use tiny 3D vectors to make the maths intuitive, padded to 1536
        // dimensions to match the column definition.
        //
        // The query vector points in the direction [1, 0, 0].
        // Chunk A's vector [0.9, 0.1, 0] is very close to the query (small cosine distance).
        // Chunk B's vector [0.1, 0.9, 0] points mostly away (larger cosine distance).
        // Chunk C has no embedding (null) — the query filters these out.
        var chunkA = createChunk(testDocument1, 0, "Closest chunk", padVector(0.9f, 0.1f, 0f));
        var chunkB = createChunk(testDocument1, 1, "Further chunk", padVector(0.1f, 0.9f, 0f));
        var chunkC = createChunk(testDocument1, 2, "No embedding", null);
        documentChunkRepository.saveAll(List.of(chunkA, chunkB, chunkC));
        entityManager.flush();

        // Act
        var queryEmbedding = vectorToString(padVector(1f, 0f, 0f));
        var maxChunks = 2;
        var result = documentChunkRepository.findSimilarChunks(testDocument1.getId(), queryEmbedding, maxChunks);

        // Assert
        assertThat(result).extracting(DocumentChunk::getContent)
            .containsExactly("Closest chunk", "Further chunk");
    }

    @Test
    void findSimilarChunks_withLimit_respectsMaxChunks() {
        // Arrange
        var chunkA = createChunk(testDocument1, 0, "Closest chunk", padVector(0.9f, 0.1f, 0f));
        var chunkB = createChunk(testDocument1, 1, "Further chunk", padVector(0.1f, 0.9f, 0f));
        var chunkC = createChunk(testDocument1, 2, "No embedding", null);
        documentChunkRepository.saveAll(List.of(chunkA, chunkB, chunkC));
        entityManager.flush();

        // Act
        var queryEmbedding = vectorToString(padVector(1f, 0f, 0f));
        var maxChunks = 1;
        var result = documentChunkRepository.findSimilarChunks(testDocument1.getId(), queryEmbedding, maxChunks);

        // Assert
        assertThat(result).extracting(DocumentChunk::getContent)
            .containsExactly("Closest chunk");
    }

    @Test
    void findSimilarChunks_multipleDocuments_returnsRequestedDocChunksOnly() {
        // Arrange
        var doc1Chunk = createChunk(testDocument1, 0, "Doc1 chunk", padVector(0.9f, 0.1f, 0f));
        var doc2Chunk = createChunk(testDocument2, 0, "Doc2 chunk", padVector(0.9f, 0.1f, 0f));
        documentChunkRepository.saveAll(List.of(doc1Chunk, doc2Chunk));
        entityManager.flush();

        // Act
        var queryEmbedding = vectorToString(padVector(1f, 0f, 0f));
        var maxChunks = 10; // Request more chunks than exist to ensure we get all matches.
        var result = documentChunkRepository.findSimilarChunks(testDocument1.getId(), queryEmbedding, maxChunks);

        // Assert
        assertThat(result).extracting(DocumentChunk::getContent)
            .containsExactly("Doc1 chunk");
    }

    @Test
    void findSimilarChunks_noMatchingChunks_returnsEmpty() {
        // Arrange
        var chunkA = createChunk(testDocument1, 0, "No embedding", null);
        documentChunkRepository.save(chunkA);
        entityManager.flush();

        // Act
        var queryEmbedding = vectorToString(padVector(1f, 0f, 0f));
        var maxChunks = 2;
        var result = documentChunkRepository.findSimilarChunks(testDocument1.getId(), queryEmbedding, maxChunks);

        // Assert
        assertThat(result).isEmpty();
    }

    // --- Helper methods ---

    private Document createDocument(String title) {
        var document = new Document();
        document.setUser(testUser);
        document.setTitle(title);
        document.setDocumentType(DocumentType.CONTRACT);
        document.setInputType(InputType.PDF);
        document.setOriginalFilename(title.toLowerCase().replace(" ", "_") + ".pdf");
        document.setStorageKey(UUID.randomUUID().toString());
        document.setStatus(DocumentStatus.READY);
        entityManager.persist(document);
        return document;
    }

    private DocumentChunk createChunk(
        Document document,
        int index,
        String content,
        float[] embedding) {

        var chunk = new DocumentChunk();
        chunk.setDocument(document);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setEmbedding(embedding);
        chunk.setTokenCount(10);
        return chunk;
    }

    /** Pads a few meaningful dimensions out to 1536 (the column's vector size). */
    private float[] padVector(float... significantValues) {
        var vector = new float[1536];
        System.arraycopy(significantValues, 0, vector, 0, significantValues.length);
        return vector;
    }

    /** Converts a float[] to the pgvector string literal format: "[0.1,0.2,...]" */
    private String vectorToString(float[] vector) {
        return Arrays.toString(vector);
    }
}

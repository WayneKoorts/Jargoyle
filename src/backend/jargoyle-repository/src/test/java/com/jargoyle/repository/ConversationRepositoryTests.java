package com.jargoyle.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

import com.jargoyle.entity.Conversation;
import com.jargoyle.entity.Document;
import com.jargoyle.entity.DocumentStatus;
import com.jargoyle.entity.DocumentType;
import com.jargoyle.entity.InputType;
import com.jargoyle.entity.User;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class ConversationRepositoryTests {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.locations", () -> "filesystem:../jargoyle-web/src/main/resources/db/migration");
    }

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private User otherUser;
    private Document testDocument;

    @BeforeEach
    void setUp() {
        testUser = createUser("owner@example.com", "Owner", "owner-subject");
        otherUser = createUser("other@example.com", "Other", "other-subject");
        testDocument = createDocument(testUser, "Test Document");
        entityManager.flush();
    }

    @Test
    void findByIdAndUserId_ownerUser_returnsConversation() {
        // Arrange
        var conversation = createConversation(testDocument, "Owner's chat");
        entityManager.flush();

        // Act
        var result = conversationRepository.findByIdAndUserId(
            conversation.getId(), testUser.getId());

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Owner's chat");
    }

    @Test
    void findByIdAndUserId_differentUser_returnsEmpty() {
        // Arrange
        var conversation = createConversation(testDocument, "Owner's chat");
        entityManager.flush();

        // Act — otherUser does not own testDocument, so should get nothing.
        var result = conversationRepository.findByIdAndUserId(
            conversation.getId(), otherUser.getId());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndUserId_nonExistentConversation_returnsEmpty() {
        // Act — a random UUID that doesn't match any conversation.
        var result = conversationRepository.findByIdAndUserId(
            UUID.randomUUID(), testUser.getId());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void findByDocumentId_orderedByCreatedAtDescThenIdDesc() {
        // Arrange — create three conversations, then assign deterministic
        // timestamps via native SQL to avoid flakiness from @CreationTimestamp
        // assigning identical values during rapid inserts.  We also set
        // lastMessageAt to the *opposite* order to prove it is no longer
        // used for sorting.
        var oldest = createConversation(testDocument, "Oldest");
        var middle = createConversation(testDocument, "Middle");
        var newest = createConversation(testDocument, "Newest");
        entityManager.flush();

        var baseTime = Instant.parse("2025-01-01T00:00:00Z");
        setConversationTimestamps(oldest, baseTime,                    baseTime.plusSeconds(300));
        setConversationTimestamps(middle, baseTime.plusSeconds(100),    baseTime.plusSeconds(200));
        setConversationTimestamps(newest, baseTime.plusSeconds(200),    baseTime.plusSeconds(100));
        entityManager.flush();
        entityManager.clear();

        // Act
        var results = conversationRepository
                .findByDocumentIdOrderByCreatedAtDescIdDesc(testDocument.getId());

        // Assert — newest-created first, oldest last.
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getTitle()).isEqualTo("Newest");
        assertThat(results.get(1).getTitle()).isEqualTo("Middle");
        assertThat(results.get(2).getTitle()).isEqualTo("Oldest");
    }

    @Test
    void findByDocumentId_excludesOtherDocuments() {
        // Arrange — conversation on a different document should not appear.
        var otherDocument = createDocument(testUser, "Other Document");
        createConversation(testDocument, "Included");
        createConversation(otherDocument, "Excluded");
        entityManager.flush();

        // Act
        var results = conversationRepository
                .findByDocumentIdOrderByCreatedAtDescIdDesc(testDocument.getId());

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Included");
    }

    // --- Helper methods ---

    private User createUser(String email, String displayName, String oauthSubject) {
        var user = new User();
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setOauthProvider("google");
        user.setOauthSubject(oauthSubject);
        entityManager.persist(user);
        return user;
    }

    private Document createDocument(User owner, String title) {
        var document = new Document();
        document.setUser(owner);
        document.setTitle(title);
        document.setDocumentType(DocumentType.CONTRACT);
        document.setInputType(InputType.PDF);
        document.setOriginalFilename(title.toLowerCase().replace(" ", "_") + ".pdf");
        document.setStorageKey(UUID.randomUUID().toString());
        document.setStatus(DocumentStatus.READY);
        entityManager.persist(document);
        return document;
    }

    private Conversation createConversation(Document document, String title) {
        var conversation = new Conversation();
        conversation.setDocument(document);
        conversation.setTitle(title);
        conversation.setLastMessageAt(Instant.now());
        entityManager.persist(conversation);
        return conversation;
    }

    /**
     * Sets both {@code created_at} and {@code last_message_at} via native SQL
     * so the test controls timestamps independently of {@code @CreationTimestamp}.
     */
    private void setConversationTimestamps(
            Conversation conversation, Instant createdAt, Instant lastMessageAt) {
        entityManager.createNativeQuery(
                "update conversations set created_at = :createdAt, last_message_at = :lastMessageAt where id = :id")
            .setParameter("createdAt", createdAt)
            .setParameter("lastMessageAt", lastMessageAt)
            .setParameter("id", conversation.getId())
            .executeUpdate();
    }

}

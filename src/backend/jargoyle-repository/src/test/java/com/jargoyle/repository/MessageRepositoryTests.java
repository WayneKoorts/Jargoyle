package com.jargoyle.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

import com.jargoyle.entity.Conversation;
import com.jargoyle.entity.Document;
import com.jargoyle.entity.DocumentStatus;
import com.jargoyle.entity.DocumentType;
import com.jargoyle.entity.InputType;
import com.jargoyle.entity.Message;
import com.jargoyle.entity.MessageRole;
import com.jargoyle.entity.User;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class MessageRepositoryTests {

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
    private MessageRepository messageRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private Document testDocument;
    private Conversation testConversation;

    @BeforeEach
    void setUp() {
        testUser = createUser("test@example.com", "Test User", "test-subject-123");
        testDocument = createDocument(testUser, "Test Document");
        testConversation = createConversation(testDocument, "Test Conversation");
        entityManager.flush();
    }

    // --- findRecentByConversationId tests ---

    @Test
    void findRecentByConversationId_returnsLimitedMessagesInDescOrder() {
        // Arrange — three messages with controlled timestamps so ordering is deterministic.
        var oldest = createMessage(testConversation, MessageRole.USER, "First question");
        var middle = createMessage(testConversation, MessageRole.ASSISTANT, "First answer");
        var newest = createMessage(testConversation, MessageRole.USER, "Second question");
        entityManager.flush();

        // Space the timestamps apart so the ORDER BY is unambiguous.
        setMessageCreatedAt(oldest, Instant.parse("2026-01-01T10:00:00Z"));
        setMessageCreatedAt(middle, Instant.parse("2026-01-01T11:00:00Z"));
        setMessageCreatedAt(newest, Instant.parse("2026-01-01T12:00:00Z"));

        // Act — request only 2, should get the newest two in descending order.
        var result = messageRepository.findRecentByConversationId(
            testConversation.getId(), 2);

        // Assert
        assertThat(result).extracting(Message::getContent)
            .containsExactly("Second question", "First answer");
    }

    @Test
    void findRecentByConversationId_limitExceedsCount_returnsAll() {
        // Arrange
        var first = createMessage(testConversation, MessageRole.USER, "Hello");
        var second = createMessage(testConversation, MessageRole.ASSISTANT, "Hi there");
        entityManager.flush();

        setMessageCreatedAt(first, Instant.parse("2026-01-01T10:00:00Z"));
        setMessageCreatedAt(second, Instant.parse("2026-01-01T11:00:00Z"));

        // Act — limit 10 but only 2 messages exist.
        var result = messageRepository.findRecentByConversationId(
            testConversation.getId(), 10);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Message::getContent)
            .containsExactly("Hi there", "Hello");
    }

    @Test
    void findRecentByConversationId_differentConversation_isolated() {
        // Arrange — messages in two separate conversations.
        var otherConversation = createConversation(testDocument, "Other Conversation");
        createMessage(testConversation, MessageRole.USER, "In test conversation");
        createMessage(otherConversation, MessageRole.USER, "In other conversation");
        entityManager.flush();

        // Act
        var result = messageRepository.findRecentByConversationId(
            testConversation.getId(), 10);

        // Assert — only the message from testConversation should appear.
        assertThat(result).extracting(Message::getContent)
            .containsExactly("In test conversation");
    }

    // --- countByConversationIds tests ---

    @Test
    void countByConversationIds_multipleConversations_returnsCorrectCounts() {
        // Arrange
        var conversation2 = createConversation(testDocument, "Second Conversation");
        createMessage(testConversation, MessageRole.USER, "Q1");
        createMessage(testConversation, MessageRole.ASSISTANT, "A1");
        createMessage(testConversation, MessageRole.USER, "Q2");
        createMessage(conversation2, MessageRole.USER, "Only question");
        entityManager.flush();

        // Act
        var result = messageRepository.countByConversationIds(
            List.of(testConversation.getId(), conversation2.getId()));

        // Assert — result is List<Object[]> where each row is [conversationId, count].
        assertThat(result).hasSize(2);

        // Convert to a map for easier assertion regardless of row order.
        var counts = result.stream()
            .collect(java.util.stream.Collectors.toMap(
                row -> (UUID) row[0],
                row -> (Long) row[1]));

        assertThat(counts.get(testConversation.getId())).isEqualTo(3L);
        assertThat(counts.get(conversation2.getId())).isEqualTo(1L);
    }

    @Test
    void countByConversationIds_conversationWithNoMessages_notIncludedInResults() {
        // Arrange — testConversation has one message; emptyConversation has none.
        var emptyConversation = createConversation(testDocument, "Empty Conversation");
        createMessage(testConversation, MessageRole.USER, "A question");
        entityManager.flush();

        // Act
        var result = messageRepository.countByConversationIds(
            List.of(testConversation.getId(), emptyConversation.getId()));

        // Assert — GROUP BY produces no row for a conversation with zero messages.
        assertThat(result).hasSize(1);
        assertThat((UUID) result.get(0)[0]).isEqualTo(testConversation.getId());
        assertThat((Long) result.get(0)[1]).isEqualTo(1L);
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

    private Message createMessage(Conversation conversation, MessageRole role, String content) {
        var message = new Message();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        entityManager.persist(message);
        return message;
    }

    /**
     * Overwrites the @CreationTimestamp-managed created_at column via native SQL,
     * then refreshes the entity so Hibernate's in-memory state matches the database.
     * This lets us control message ordering in tests without needing a setter.
     */
    private void setMessageCreatedAt(Message message, Instant timestamp) {
        entityManager.createNativeQuery(
            "UPDATE messages SET created_at = :ts WHERE id = :id")
            .setParameter("ts", timestamp)
            .setParameter("id", message.getId())
            .executeUpdate();
        entityManager.refresh(message);
    }

}

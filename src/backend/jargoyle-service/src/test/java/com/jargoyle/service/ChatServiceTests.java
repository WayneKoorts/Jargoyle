package com.jargoyle.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.jargoyle.dto.ChatStreamEvent;
import com.jargoyle.entity.Conversation;
import com.jargoyle.entity.Document;
import com.jargoyle.entity.DocumentChunk;
import com.jargoyle.entity.DocumentStatus;
import com.jargoyle.entity.DocumentSummary;
import com.jargoyle.entity.DocumentType;
import com.jargoyle.entity.Message;
import com.jargoyle.entity.MessageRole;
import com.jargoyle.repository.ConversationRepository;
import com.jargoyle.repository.DocumentChunkRepository;
import com.jargoyle.repository.DocumentSummaryRepository;
import com.jargoyle.repository.MessageRepository;
import com.jargoyle.service.exception.ConversationNotFoundException;
import com.jargoyle.service.exception.DocumentNotReadyException;
import com.jargoyle.service.properties.ChatProperties;
import com.jargoyle.service.properties.RetrievalProperties;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChatService}. All external dependencies (ChatModel,
 * repositories, EmbeddingService) are mocked so tests run without a database
 * or API keys.
 *
 * <p>Streaming assertions use Project Reactor's {@link StepVerifier}, which
 * subscribes to the {@link Flux} and verifies each emitted element in order.
 */
public class ChatServiceTests {

    private ChatModel mockChatModel;
    private ConversationRepository mockConversationRepository;
    private MessageRepository mockMessageRepository;
    private DocumentChunkRepository mockDocumentChunkRepository;
    private DocumentSummaryRepository mockDocumentSummaryRepository;
    private EmbeddingService mockEmbeddingService;
    private RetrievalProperties retrievalProperties;
    private ChatProperties chatProperties;
    private ChatService sut;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final String userQuestion = "What is the total amount due?";

    @BeforeEach
    void setUp() {
        mockChatModel = mock(ChatModel.class);
        mockConversationRepository = mock(ConversationRepository.class);
        mockMessageRepository = mock(MessageRepository.class);
        mockDocumentChunkRepository = mock(DocumentChunkRepository.class);
        mockDocumentSummaryRepository = mock(DocumentSummaryRepository.class);
        mockEmbeddingService = mock(EmbeddingService.class);
        retrievalProperties = new RetrievalProperties(100_000, 200);
        chatProperties = new ChatProperties(10, 2000, 4000);

        sut = new ChatService(
                ChatClient.builder(mockChatModel),
                mockConversationRepository,
                mockMessageRepository,
                mockDocumentChunkRepository,
                mockDocumentSummaryRepository,
                mockEmbeddingService,
                retrievalProperties,
                chatProperties);
    }

    // ── Test helpers ──────────────────────────────────────────────────

    /**
     * Configures the mock ChatModel to return a streaming response with
     * the given tokens. Each token becomes a separate ChatResponse in the
     * Flux, simulating how Spring AI streams LLM output.
     */
    private void setStreamingResponse(String... tokens) {
        var responses = Arrays.stream(tokens)
                .map(token -> new ChatResponse(
                        List.of(new Generation(new AssistantMessage(token)))))
                .toList();
        when(mockChatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.fromIterable(responses));
    }

    /**
     * Creates a mock Conversation with a READY document, wired up for
     * the standard test scenario.
     */
    private Conversation createReadyConversation() {
        var document = mock(Document.class);
        when(document.getId()).thenReturn(documentId);
        when(document.getStatus()).thenReturn(DocumentStatus.READY);
        when(document.getDocumentType()).thenReturn(DocumentType.BILL);

        var conversation = mock(Conversation.class);
        when(conversation.getId()).thenReturn(conversationId);
        when(conversation.getDocument()).thenReturn(document);

        return conversation;
    }

    /**
     * Creates a test DocumentChunk with the given index and content.
     */
    private DocumentChunk createTestChunk(int index, String content) {
        var chunk = mock(DocumentChunk.class);
        when(chunk.getId()).thenReturn(UUID.randomUUID());
        when(chunk.getChunkIndex()).thenReturn(index);
        when(chunk.getContent()).thenReturn(content);
        when(chunk.getTokenCount()).thenReturn(content.length() / 4);
        return chunk;
    }

    /**
     * Creates a test Message with the given role, content, and token count.
     */
    private Message createTestMessage(MessageRole role, String content) {
        var message = mock(Message.class);
        when(message.getId()).thenReturn(UUID.randomUUID());
        when(message.getRole()).thenReturn(role);
        when(message.getContent()).thenReturn(content);
        when(message.getTokenCount()).thenReturn(content.length() / 4);
        return message;
    }

    /**
     * Sets up all the standard mocks for a happy-path chat flow: a READY
     * conversation, embedding, chunks, summary, empty history, and a
     * streaming response.
     */
    private Conversation setUpHappyPath(String... responseTokens) {
        var conversation = createReadyConversation();
        when(mockConversationRepository.findByIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(conversation));

        // Embedding mock.
        var fakeEmbedding = new float[]{0.1f, 0.2f, 0.3f};
        when(mockEmbeddingService.embed(userQuestion)).thenReturn(fakeEmbedding);
        when(mockEmbeddingService.toVectorLiteral(fakeEmbedding)).thenReturn("[0.1, 0.2, 0.3]");

        // Chunk retrieval mock.
        var chunk = createTestChunk(0, "The total amount due is £150.00.");
        when(mockDocumentChunkRepository.findSimilarChunks(
                eq(documentId), anyString(), anyInt()))
                .thenReturn(List.of(chunk));
        when(mockDocumentChunkRepository.countByDocumentId(documentId))
                .thenReturn(1L);

        // Summary mock.
        var summary = mock(DocumentSummary.class);
        when(summary.getPlainSummary()).thenReturn("This is an electricity bill for March 2026.");
        when(mockDocumentSummaryRepository.findByDocumentId(documentId))
                .thenReturn(Optional.of(summary));

        // Empty history.
        when(mockMessageRepository.findRecentByConversationId(eq(conversationId), anyInt()))
                .thenReturn(List.of());

        // Message save returns a message with an ID.
        when(mockMessageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            var saved = mock(Message.class);
            when(saved.getId()).thenReturn(UUID.randomUUID());
            return saved;
        });

        setStreamingResponse(responseTokens);
        return conversation;
    }

    // ── Happy-path tests ──────────────────────────────────────────────

    @Nested
    class HappyPath {

        @Test
        void chat_validConversation_emitsTokensThenCompleteEvent() {
            setUpHappyPath("Based ", "on ", "your document, ", "the total is £150.");

            var result = sut.chat(conversationId, userId, userQuestion);

            StepVerifier.create(result)
                    .expectNext(ChatStreamEvent.token("Based "))
                    .expectNext(ChatStreamEvent.token("on "))
                    .expectNext(ChatStreamEvent.token("your document, "))
                    .expectNext(ChatStreamEvent.token("the total is £150."))
                    .expectNextMatches(event ->
                            "COMPLETE".equals(event.type())
                            && event.messageId() != null
                            && event.sourceChunks() != null
                            && event.sourceChunks().size() == 1)
                    .verifyComplete();
        }

        @Test
        void chat_savesUserMessageBeforeStreaming() {
            setUpHappyPath("Response.");

            sut.chat(conversationId, userId, userQuestion).blockLast();

            var messageCaptor = ArgumentCaptor.forClass(Message.class);
            // save() is called twice: once for the user message, once for
            // the assistant message. Capture all invocations.
            verify(mockMessageRepository, org.mockito.Mockito.atLeast(1))
                    .save(messageCaptor.capture());

            var savedMessages = messageCaptor.getAllValues();
            var userMsg = savedMessages.stream()
                    .filter(m -> m.getRole() == MessageRole.USER)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("User message not saved"));

            assertThat(userMsg.getContent()).isEqualTo(userQuestion);
            assertThat(userMsg.getRole()).isEqualTo(MessageRole.USER);
        }

        @Test
        void chat_savesAssistantMessageOnCompletion() {
            setUpHappyPath("Hello ", "world.");

            sut.chat(conversationId, userId, userQuestion).blockLast();

            var messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(mockMessageRepository, org.mockito.Mockito.atLeast(2))
                    .save(messageCaptor.capture());

            var savedMessages = messageCaptor.getAllValues();
            var assistantMsg = savedMessages.stream()
                    .filter(m -> m.getRole() == MessageRole.ASSISTANT)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Assistant message not saved"));

            assertThat(assistantMsg.getContent()).isEqualTo("Hello world.");
            assertThat(assistantMsg.getSourceChunks()).hasSize(1);
            assertThat(assistantMsg.getTokenCount()).isNotNull();
        }

        @Test
        void chat_updatesConversationLastMessageAt() {
            var conversation = setUpHappyPath("Response.");

            sut.chat(conversationId, userId, userQuestion).blockLast();

            verify(conversation).setLastMessageAt(any(Instant.class));
            verify(mockConversationRepository).save(conversation);
        }

        @Test
        void chat_embedsQuestionAndRetrievesChunks() {
            setUpHappyPath("Response.");

            sut.chat(conversationId, userId, userQuestion).blockLast();

            verify(mockEmbeddingService).embed(userQuestion);
            verify(mockEmbeddingService).toVectorLiteral(any(float[].class));
            verify(mockDocumentChunkRepository).findSimilarChunks(
                    eq(documentId), eq("[0.1, 0.2, 0.3]"), eq(200));
        }

        @Test
        void chat_promptContainsDocumentSummaryAndChunks() {
            setUpHappyPath("Response.");

            sut.chat(conversationId, userId, userQuestion).blockLast();

            var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
            verify(mockChatModel).stream(promptCaptor.capture());

            var capturedPrompt = promptCaptor.getValue();
            var systemMessage = capturedPrompt.getInstructions().stream()
                    .filter(m -> m.getMessageType() == MessageType.SYSTEM)
                    .map(org.springframework.ai.chat.messages.Message::getText)
                    .findFirst()
                    .orElseThrow();

            assertThat(systemMessage).contains("electricity bill for March 2026");
            assertThat(systemMessage).contains("The total amount due is £150.00.");
            assertThat(systemMessage).contains("[Section 1]");
            assertThat(systemMessage).contains("bill");
            assertThat(systemMessage).contains("DOCUMENT CONTENT (full document)");
        }

        @Test
        void chat_promptContainsConversationHistory() {
            var conversation = createReadyConversation();
            when(mockConversationRepository.findByIdAndUserId(conversationId, userId))
                    .thenReturn(Optional.of(conversation));

            when(mockEmbeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(mockEmbeddingService.toVectorLiteral(any())).thenReturn("[0.1]");
            when(mockDocumentChunkRepository.findSimilarChunks(any(), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(mockDocumentChunkRepository.countByDocumentId(any()))
                    .thenReturn(0L);
            when(mockDocumentSummaryRepository.findByDocumentId(any()))
                    .thenReturn(Optional.empty());

            // History returned in DESC order (newest first) by the repository.
            var oldUserMsg = createTestMessage(MessageRole.USER, "What is this document?");
            var oldAssistantMsg = createTestMessage(MessageRole.ASSISTANT, "This is a bill.");
            when(mockMessageRepository.findRecentByConversationId(eq(conversationId), anyInt()))
                    .thenReturn(List.of(oldAssistantMsg, oldUserMsg));

            when(mockMessageRepository.save(any(Message.class))).thenAnswer(invocation -> {
                var saved = mock(Message.class);
                when(saved.getId()).thenReturn(UUID.randomUUID());
                return saved;
            });

            setStreamingResponse("Response.");
            sut.chat(conversationId, userId, userQuestion).blockLast();

            var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
            verify(mockChatModel).stream(promptCaptor.capture());

            var systemMessage = promptCaptor.getValue().getInstructions().stream()
                    .filter(m -> m.getMessageType() == MessageType.SYSTEM)
                    .map(org.springframework.ai.chat.messages.Message::getText)
                    .findFirst()
                    .orElseThrow();

            // History should appear in chronological order (oldest first),
            // even though the repository returned newest first.
            int userIndex = systemMessage.indexOf("What is this document?");
            int assistantIndex = systemMessage.indexOf("This is a bill.");
            assertThat(userIndex).isGreaterThan(-1);
            assertThat(assistantIndex).isGreaterThan(userIndex);
        }
    }

    // ── Error-handling tests ──────────────────────────────────────────

    @Nested
    class ErrorHandling {

        @Test
        void chat_conversationNotFound_throwsConversationNotFoundException() {
            when(mockConversationRepository.findByIdAndUserId(conversationId, userId))
                    .thenReturn(Optional.empty());

            var result = sut.chat(conversationId, userId, userQuestion);

            StepVerifier.create(result)
                    .expectError(ConversationNotFoundException.class)
                    .verify();
        }

        @Test
        void chat_documentNotReady_throwsDocumentNotReadyException() {
            var document = mock(Document.class);
            when(document.getId()).thenReturn(documentId);
            when(document.getStatus()).thenReturn(DocumentStatus.PROCESSING);

            var conversation = mock(Conversation.class);
            when(conversation.getDocument()).thenReturn(document);
            when(mockConversationRepository.findByIdAndUserId(conversationId, userId))
                    .thenReturn(Optional.of(conversation));

            var result = sut.chat(conversationId, userId, userQuestion);

            StepVerifier.create(result)
                    .expectError(DocumentNotReadyException.class)
                    .verify();
        }

        @Test
        void chat_llmErrorDuringStreaming_emitsErrorEvent() {
            var conversation = createReadyConversation();
            when(mockConversationRepository.findByIdAndUserId(conversationId, userId))
                    .thenReturn(Optional.of(conversation));
            when(mockEmbeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(mockEmbeddingService.toVectorLiteral(any())).thenReturn("[0.1]");
            when(mockDocumentChunkRepository.findSimilarChunks(any(), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(mockDocumentChunkRepository.countByDocumentId(any()))
                    .thenReturn(0L);
            when(mockDocumentSummaryRepository.findByDocumentId(any()))
                    .thenReturn(Optional.empty());
            when(mockMessageRepository.findRecentByConversationId(any(), anyInt()))
                    .thenReturn(List.of());
            when(mockMessageRepository.save(any(Message.class))).thenAnswer(invocation -> {
                var saved = mock(Message.class);
                when(saved.getId()).thenReturn(UUID.randomUUID());
                return saved;
            });

            // Simulate LLM failure mid-stream.
            when(mockChatModel.stream(any(Prompt.class)))
                    .thenReturn(Flux.error(new RuntimeException("LLM service unavailable")));

            var result = sut.chat(conversationId, userId, userQuestion);

            StepVerifier.create(result)
                    .expectNextMatches(event ->
                            "ERROR".equals(event.type())
                            && event.content().contains("Something went wrong"))
                    .verifyComplete();
        }

        @Test
        void chat_llmErrorDuringStreaming_doesNotSaveAssistantMessage() {
            var conversation = createReadyConversation();
            when(mockConversationRepository.findByIdAndUserId(conversationId, userId))
                    .thenReturn(Optional.of(conversation));
            when(mockEmbeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(mockEmbeddingService.toVectorLiteral(any())).thenReturn("[0.1]");
            when(mockDocumentChunkRepository.findSimilarChunks(any(), anyString(), anyInt()))
                    .thenReturn(List.of());
            when(mockDocumentChunkRepository.countByDocumentId(any()))
                    .thenReturn(0L);
            when(mockDocumentSummaryRepository.findByDocumentId(any()))
                    .thenReturn(Optional.empty());
            when(mockMessageRepository.findRecentByConversationId(any(), anyInt()))
                    .thenReturn(List.of());
            when(mockMessageRepository.save(any(Message.class))).thenAnswer(invocation -> {
                var saved = mock(Message.class);
                when(saved.getId()).thenReturn(UUID.randomUUID());
                return saved;
            });

            when(mockChatModel.stream(any(Prompt.class)))
                    .thenReturn(Flux.error(new RuntimeException("LLM failure")));

            sut.chat(conversationId, userId, userQuestion).blockLast();

            // Only the user message should be saved, not an assistant message.
            var messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(mockMessageRepository).save(messageCaptor.capture());

            var savedMessages = messageCaptor.getAllValues();
            assertThat(savedMessages).hasSize(1);
            assertThat(savedMessages.getFirst().getRole()).isEqualTo(MessageRole.USER);
        }
    }

    // ── History trimming tests ────────────────────────────────────────

    @Nested
    class HistoryTrimming {

        @Test
        void trimHistory_withinBudget_returnsAllMessages() {
            var messages = List.of(
                    createTestMessage(MessageRole.USER, "Hello"),
                    createTestMessage(MessageRole.ASSISTANT, "Hi there"),
                    createTestMessage(MessageRole.USER, "How are you?"),
                    createTestMessage(MessageRole.ASSISTANT, "I'm well."));

            var result = sut.trimHistory(messages, 2000);

            assertThat(result).hasSize(4);
        }

        @Test
        void trimHistory_exceedsBudget_trimsOldestMessages() {
            // Each message is ~25 tokens (100 chars / 4). Budget of 60 tokens
            // fits about 2 messages plus the mandatory 2 = 2 kept from budget.
            var msg1 = createTestMessage(MessageRole.USER,
                    "A".repeat(100));
            var msg2 = createTestMessage(MessageRole.ASSISTANT,
                    "B".repeat(100));
            var msg3 = createTestMessage(MessageRole.USER,
                    "C".repeat(100));
            var msg4 = createTestMessage(MessageRole.ASSISTANT,
                    "D".repeat(100));

            // Budget of 60 tokens: last 2 messages cost 50 tokens (25+25),
            // leaving 10 tokens — not enough for msg2 (25 tokens).
            var result = sut.trimHistory(List.of(msg1, msg2, msg3, msg4), 60);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().getContent()).isEqualTo("C".repeat(100));
        }

        @Test
        void trimHistory_alwaysIncludesMinimumTwoMessages() {
            // Even if the last 2 messages exceed the budget, they are still kept.
            var msg1 = createTestMessage(MessageRole.USER,
                    "A".repeat(400));
            var msg2 = createTestMessage(MessageRole.ASSISTANT,
                    "B".repeat(400));
            var msg3 = createTestMessage(MessageRole.USER,
                    "C".repeat(400));

            // Budget of 10 tokens, but last 2 must still be included.
            var result = sut.trimHistory(List.of(msg1, msg2, msg3), 10);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().getContent()).isEqualTo("B".repeat(400));
            assertThat(result.getLast().getContent()).isEqualTo("C".repeat(400));
        }

        @Test
        void trimHistory_emptyHistory_returnsEmptyList() {
            var result = sut.trimHistory(List.of(), 2000);

            assertThat(result).isEmpty();
        }

        @Test
        void trimHistory_singleMessage_returnsIt() {
            var msg = createTestMessage(MessageRole.USER, "Hello");
            var result = sut.trimHistory(List.of(msg), 2000);

            assertThat(result).hasSize(1);
        }
    }

    // ── Chunk budget tests ─────────────────────────────────────────────

    @Nested
    class ChunkBudgeting {

        @Test
        void selectChunksWithinBudget_allChunksFit_returnsAll() {
            var chunks = List.of(
                    createTestChunk(0, "A".repeat(400)),
                    createTestChunk(1, "B".repeat(400)),
                    createTestChunk(2, "C".repeat(400)));

            var result = sut.selectChunksWithinBudget(chunks, 1000);

            assertThat(result).hasSize(3);
        }

        @Test
        void selectChunksWithinBudget_budgetExceeded_trimsLeastRelevant() {
            // Each chunk is ~100 tokens (400 chars / 4). Budget of 200 fits 2.
            var chunks = List.of(
                    createTestChunk(0, "A".repeat(400)),
                    createTestChunk(1, "B".repeat(400)),
                    createTestChunk(2, "C".repeat(400)));

            var result = sut.selectChunksWithinBudget(chunks, 200);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getChunkIndex()).isEqualTo(0);
            assertThat(result.get(1).getChunkIndex()).isEqualTo(1);
        }

        @Test
        void selectChunksWithinBudget_alwaysIncludesAtLeastOneChunk() {
            var chunks = List.of(
                    createTestChunk(0, "A".repeat(2000)));

            var result = sut.selectChunksWithinBudget(chunks, 10);

            assertThat(result).hasSize(1);
        }

        @Test
        void selectChunksWithinBudget_emptyList_returnsEmpty() {
            var result = sut.selectChunksWithinBudget(List.of(), 1000);

            assertThat(result).isEmpty();
        }
    }

    // ── Source attribution tests ──────────────────────────────────────

    @Nested
    class SourceAttribution {

        @Test
        void chat_sourceChunksAreRecordedAtRetrievalTime() {
            var conversation = createReadyConversation();
            when(mockConversationRepository.findByIdAndUserId(conversationId, userId))
                    .thenReturn(Optional.of(conversation));

            when(mockEmbeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
            when(mockEmbeddingService.toVectorLiteral(any())).thenReturn("[0.1]");

            var chunk1 = createTestChunk(0, "First chunk content here.");
            var chunk2 = createTestChunk(1, "Second chunk content here.");
            when(mockDocumentChunkRepository.findSimilarChunks(any(), anyString(), anyInt()))
                    .thenReturn(List.of(chunk1, chunk2));
            when(mockDocumentChunkRepository.countByDocumentId(any()))
                    .thenReturn(2L);

            when(mockDocumentSummaryRepository.findByDocumentId(any()))
                    .thenReturn(Optional.empty());
            when(mockMessageRepository.findRecentByConversationId(any(), anyInt()))
                    .thenReturn(List.of());
            when(mockMessageRepository.save(any(Message.class))).thenAnswer(invocation -> {
                var saved = mock(Message.class);
                when(saved.getId()).thenReturn(UUID.randomUUID());
                return saved;
            });

            setStreamingResponse("Response.");

            var result = sut.chat(conversationId, userId, userQuestion);

            StepVerifier.create(result)
                    .expectNextMatches(event -> "TOKEN".equals(event.type()))
                    .expectNextMatches(event ->
                            "COMPLETE".equals(event.type())
                            && event.sourceChunks().size() == 2
                            && event.sourceChunks().get(0).chunkIndex() == 0
                            && event.sourceChunks().get(1).chunkIndex() == 1)
                    .verifyComplete();
        }

        @Test
        void chat_shortChunkPreview_usesFullContent() {
            setUpHappyPath("Response.");
            // The default chunk in setUpHappyPath has short content (<150 chars).

            var result = sut.chat(conversationId, userId, userQuestion);

            StepVerifier.create(result)
                    .expectNextMatches(event -> "TOKEN".equals(event.type()))
                    .expectNextMatches(event -> {
                        var preview = event.sourceChunks().getFirst().preview();
                        return preview.equals("The total amount due is £150.00.");
                    })
                    .verifyComplete();
        }
    }

    // ── Token estimation tests ────────────────────────────────────────

    @Nested
    class TokenEstimation {

        @Test
        void estimateTokenCount_usesLengthDividedByFour() {
            assertThat(sut.estimateTokenCount("hello world!")).isEqualTo(3);
        }

        @Test
        void estimateTokenCount_emptyString_returnsZero() {
            assertThat(sut.estimateTokenCount("")).isEqualTo(0);
        }

        @Test
        void estimateTokenCount_nullString_returnsZero() {
            assertThat(sut.estimateTokenCount(null)).isEqualTo(0);
        }
    }
}

package com.jargoyle.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jargoyle.dto.ConversationResponse;
import com.jargoyle.dto.CreateConversationResponse;
import com.jargoyle.dto.MessageResponse;
import com.jargoyle.entity.Conversation;
import com.jargoyle.entity.Message;
import com.jargoyle.repository.ConversationRepository;
import com.jargoyle.repository.DocumentRepository;
import com.jargoyle.repository.DocumentSummaryRepository;
import com.jargoyle.repository.MessageRepository;
import com.jargoyle.service.exception.ConversationNotFoundException;
import com.jargoyle.service.exception.DocumentNotFoundException;

/**
 * Business logic for conversation CRUD operations.
 *
 * <p>Handles creating conversations, listing conversations for a document,
 * and retrieving paginated message history. The actual chat flow (RAG
 * orchestration, LLM streaming) is handled separately by {@link ChatService}.
 *
 * <p>All operations verify ownership — a user can only access conversations
 * that belong to their own documents.
 *
 * @see ChatService
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final DocumentRepository documentRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final DocumentSummaryRepository documentSummaryRepository;
    private final SuggestedQuestionService suggestedQuestionService;

    public ConversationService(
            DocumentRepository documentRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            DocumentSummaryRepository documentSummaryRepository,
            SuggestedQuestionService suggestedQuestionService) {

        this.documentRepository = documentRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.documentSummaryRepository = documentSummaryRepository;
        this.suggestedQuestionService = suggestedQuestionService;
    }

    /**
     * Creates a new conversation for the given document.
     *
     * <p>Verifies document ownership, persists an empty conversation, and
     * returns the conversation ID along with suggested starter questions
     * tailored to the document type.
     *
     * @param userId     the authenticated user's ID
     * @param documentId the document to create a conversation for
     * @return the new conversation with suggested questions
     * @throws DocumentNotFoundException if the document does not exist or
     *                                   is not owned by the user
     */
    @Transactional
    public CreateConversationResponse createConversation(UUID userId, UUID documentId) {
        var document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        var conversation = new Conversation();
        conversation.setDocument(document);
        conversation.setLastMessageAt(Instant.now());
        conversationRepository.save(conversation);

        log.debug("Created conversation {} for document {}", conversation.getId(), documentId);

        // Retrieve the plain summary for suggested questions (future dynamic use).
        var plainSummary = documentSummaryRepository.findByDocumentId(documentId)
                .map(ds -> ds.getPlainSummary())
                .orElse(null);

        var suggestions = suggestedQuestionService.getSuggestions(
                document.getDocumentType(), plainSummary);

        return new CreateConversationResponse(
                conversation.getId(), documentId, suggestions);
    }

    /**
     * Lists all conversations for a document, sorted by most recent
     * activity first.
     *
     * <p>Message counts are fetched in a single batch query to avoid N+1
     * performance problems.
     *
     * @param userId     the authenticated user's ID
     * @param documentId the document whose conversations to list
     * @return conversations sorted by {@code lastMessageAt} descending
     * @throws DocumentNotFoundException if the document does not exist or
     *                                   is not owned by the user
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(UUID userId, UUID documentId) {
        // Verify document ownership.
        documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        var conversations = conversationRepository
                .findByDocumentIdOrderByLastMessageAtDesc(documentId);

        if (conversations.isEmpty()) {
            return List.of();
        }

        // Batch-fetch message counts to avoid N+1 queries.
        var conversationIds = conversations.stream()
                .map(Conversation::getId)
                .toList();
        var countRows = messageRepository.countByConversationIds(conversationIds);
        var countsByConversationId = countRows.stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Long) row[1]).intValue()));

        return conversations.stream()
                .map(c -> toConversationResponse(c, countsByConversationId))
                .toList();
    }

    /**
     * Retrieves paginated message history for a conversation, newest first.
     *
     * @param userId         the authenticated user's ID
     * @param conversationId the conversation whose messages to retrieve
     * @param pageable       pagination parameters (default size: 50)
     * @return a page of messages in reverse chronological order
     * @throws ConversationNotFoundException if the conversation does not
     *                                       exist or is not owned by the user
     */
    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID userId, UUID conversationId, Pageable pageable) {
        // Verify conversation ownership (joins through document → user).
        conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        return messageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(this::toMessageResponse);
    }

    /**
     * Maps a {@link Conversation} entity to a {@link ConversationResponse} DTO,
     * looking up the message count from the pre-fetched counts map.
     */
    private ConversationResponse toConversationResponse(
            Conversation conversation,
            Map<UUID, Integer> countsByConversationId) {

        var messageCount = countsByConversationId
                .getOrDefault(conversation.getId(), 0);

        return new ConversationResponse(
                conversation.getId(),
                conversation.getDocument().getId(),
                conversation.getTitle(),
                messageCount,
                conversation.getCreatedAt(),
                conversation.getLastMessageAt());
    }

    /**
     * Maps a {@link Message} entity to a {@link MessageResponse} DTO.
     */
    private MessageResponse toMessageResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getSourceChunks(),
                message.getCreatedAt());
    }
}

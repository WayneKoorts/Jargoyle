package com.jargoyle.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jargoyle.dto.ChatRequest;
import com.jargoyle.dto.ChatStreamEvent;
import com.jargoyle.dto.ConversationResponse;
import com.jargoyle.dto.CreateConversationResponse;
import com.jargoyle.dto.MessageResponse;
import com.jargoyle.entity.User;
import com.jargoyle.service.ChatService;
import com.jargoyle.service.ConversationService;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

/**
 * REST controller for conversation and message endpoints.
 *
 * <p>Provides four endpoints that complete the chat feature:
 * <ul>
 *   <li>Create a conversation for a document</li>
 *   <li>List conversations for a document</li>
 *   <li>Retrieve paginated message history</li>
 *   <li>Send a message and stream the AI response via SSE</li>
 * </ul>
 *
 * <p>Endpoints span two URL hierarchies ({@code /api/documents/...} and
 * {@code /api/conversations/...}), so no class-level {@code @RequestMapping}
 * is used.
 *
 * @see ConversationService
 * @see ChatService
 */
@RestController
public class ConversationController {

    private final ConversationService conversationService;
    private final ChatService chatService;

    public ConversationController(
            ConversationService conversationService,
            ChatService chatService) {

        this.conversationService = conversationService;
        this.chatService = chatService;
    }

    /**
     * Creates a new conversation for the specified document.
     *
     * <p>Returns the conversation ID and suggested starter questions
     * tailored to the document type.
     *
     * @param user       the authenticated user (resolved via {@link CurrentUser})
     * @param documentId the document to create a conversation for
     * @return 201 Created with the new conversation and suggested questions
     */
    @PostMapping("/api/documents/{documentId}/conversations")
    public ResponseEntity<CreateConversationResponse> createConversation(
            @CurrentUser User user,
            @PathVariable UUID documentId) {

        var response = conversationService.createConversation(user.getId(), documentId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists all conversations for a document, sorted newest-to-oldest
     * by creation time.
     *
     * @param user       the authenticated user
     * @param documentId the document whose conversations to list
     * @return 200 OK with the conversation list (may be empty)
     */
    @GetMapping("/api/documents/{documentId}/conversations")
    public ResponseEntity<List<ConversationResponse>> listConversations(
            @CurrentUser User user,
            @PathVariable UUID documentId) {

        var conversations = conversationService.listConversations(user.getId(), documentId);

        return ResponseEntity.ok(conversations);
    }

    /**
     * Retrieves paginated message history for a conversation.
     *
     * <p>Messages are returned newest-first. The frontend reverses each
     * page for chronological display and uses "load more" to fetch older
     * pages.
     *
     * @param user           the authenticated user
     * @param conversationId the conversation whose messages to retrieve
     * @param pageable       pagination parameters (default size: 50)
     * @return 200 OK with the paginated message list
     */
    @GetMapping("/api/conversations/{conversationId}/messages")
    public Page<MessageResponse> getMessages(
            @CurrentUser User user,
            @PathVariable UUID conversationId,
            Pageable pageable) {

        return conversationService.getMessages(user.getId(), conversationId, pageable);
    }

    /**
     * Sends a user message and streams the AI response via Server-Sent Events.
     *
     * <p>The response is a {@link Flux} of {@link ChatStreamEvent} objects:
     * <ul>
     *   <li>{@code TOKEN} — partial response text as the LLM generates it</li>
     *   <li>{@code COMPLETE} — final event with the persisted message ID and
     *       source chunk references</li>
     *   <li>{@code ERROR} — emitted if an error occurs mid-stream</li>
     * </ul>
     *
     * <p>Spring MVC adapts the reactive {@code Flux} return type and writes
     * each element as an SSE {@code data:} frame.
     *
     * @param user           the authenticated user
     * @param conversationId the conversation to send the message in
     * @param request        the user's message (validated: not blank, max 5000 chars)
     * @return an SSE stream of chat events
     */
    @PostMapping(
            path = "/api/conversations/{conversationId}/messages",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatStreamEvent> chat(
            @CurrentUser User user,
            @PathVariable UUID conversationId,
            @Valid @RequestBody ChatRequest request) {

        return chatService.chat(conversationId, user.getId(), request.content());
    }
}

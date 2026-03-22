package com.jargoyle.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.jargoyle.dto.ChatStreamEvent;
import com.jargoyle.dto.SourceChunkReference;
import com.jargoyle.entity.Conversation;
import com.jargoyle.entity.Document;
import com.jargoyle.entity.DocumentChunk;
import com.jargoyle.entity.DocumentStatus;
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

/**
 * RAG orchestration service that coordinates the full chat flow for a
 * conversation about an uploaded document.
 *
 * <p>When a user asks a question, this service:
 * <ol>
 *   <li>Verifies conversation ownership and document readiness</li>
 *   <li>Persists the user's message</li>
 *   <li>Embeds the question and retrieves the most relevant document chunks</li>
 *   <li>Assembles a prompt with the document summary, retrieved chunks, and
 *       conversation history</li>
 *   <li>Streams the LLM response as {@link ChatStreamEvent} instances</li>
 *   <li>Persists the assistant's response with source chunk attribution</li>
 * </ol>
 *
 * <p>The returned {@link Flux} emits {@code TOKEN} events as the LLM generates
 * its answer, followed by a single {@code COMPLETE} event containing the
 * persisted message ID and the source chunks that grounded the response. If an
 * error occurs mid-stream, an {@code ERROR} event is emitted and no partial
 * assistant message is saved.
 *
 * @see ChatStreamEvent
 * @see EmbeddingService
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** Maximum number of characters to include in a source chunk preview. */
    private static final int SOURCE_PREVIEW_LENGTH = 150;

    // Characters-per-token heuristic used throughout the project for
    // approximate token counting. English text with OpenAI's tokeniser
    // averages roughly 4 characters per token.
    private static final int CHARS_PER_TOKEN = 4;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are Jargoyle, a friendly document explainer that helps regular people \
            understand their %s. You speak in plain, clear English — no jargon, \
            no legalese.

            Rules:
            - Only answer based on the document content provided below. Do not use general \
              knowledge or make assumptions beyond what the document states.
            - If the answer is not in the document content below, say so clearly: "I can't \
              find that in your document."
            - When referencing specific amounts, dates, or terms, quote them exactly from \
              the document.
            - Keep answers concise but thorough. Use bullet points for lists.
            - If the document uses jargon, explain it in parentheses.
            - Reminder: You provide plain-English interpretations, not legal or financial advice.

            --- DOCUMENT SUMMARY ---
            %s

            --- DOCUMENT CONTENT (%s) ---
            %s

            --- CONVERSATION HISTORY ---
            %s
            """;

    private final ChatClient chatClient;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentSummaryRepository documentSummaryRepository;
    private final EmbeddingService embeddingService;
    private final TitleGenerationService titleGenerationService;
    private final RetrievalProperties retrievalProperties;
    private final ChatProperties chatProperties;

    /**
     * Creates a new {@code ChatService}.
     *
     * <p>The {@link ChatClient} is built without a default system prompt
     * because the system message is assembled dynamically for each request
     * (it includes the document summary, retrieved chunks, and conversation
     * history).
     *
     * @param chatClientBuilder        Spring AI's auto-configured builder
     * @param conversationRepository   for loading and verifying conversations
     * @param messageRepository        for loading history and saving messages
     * @param documentChunkRepository  for retrieving similar chunks
     * @param documentSummaryRepository for loading the document's plain summary
     * @param embeddingService         for embedding the user's question
     * @param titleGenerationService   for generating conversation titles via LLM
     * @param retrievalProperties      retrieval configuration (top-K)
     * @param chatProperties           chat prompt budget configuration
     */
    public ChatService(
            ChatClient.Builder chatClientBuilder,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            DocumentChunkRepository documentChunkRepository,
            DocumentSummaryRepository documentSummaryRepository,
            EmbeddingService embeddingService,
            TitleGenerationService titleGenerationService,
            RetrievalProperties retrievalProperties,
            ChatProperties chatProperties) {

        this.chatClient = chatClientBuilder.build();
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentSummaryRepository = documentSummaryRepository;
        this.embeddingService = embeddingService;
        this.titleGenerationService = titleGenerationService;
        this.retrievalProperties = retrievalProperties;
        this.chatProperties = chatProperties;
    }

    /**
     * Orchestrates the full RAG chat flow for a single user question.
     *
     * <p>Returns a cold {@link Flux} — no work is performed until a subscriber
     * subscribes. The blocking preparation steps (ownership verification,
     * message persistence, embedding, chunk retrieval, prompt construction)
     * execute on the subscribing thread, followed by the reactive LLM
     * streaming phase.
     *
     * @param conversationId the conversation to continue
     * @param userId         the authenticated user's ID (for ownership verification)
     * @param userQuestion   the user's question text
     * @return a cold Flux that emits TOKEN events, then a COMPLETE or ERROR event
     * @throws ConversationNotFoundException if the conversation does not exist
     *                                       or is not owned by the user
     * @throws DocumentNotReadyException     if the document has not finished processing
     */
    public Flux<ChatStreamEvent> chat(UUID conversationId, UUID userId, String userQuestion) {
        return Flux.defer(() -> {
            // Steps 1-2: verify ownership and document readiness.
            var conversation = loadAndVerifyConversation(conversationId, userId);
            var document = conversation.getDocument();

            // Step 3: persist the user's message immediately so it appears
            // in history even if the LLM call fails.
            saveUserMessage(conversation, userQuestion);

            // Steps 4-5: embed the question and retrieve similar chunks
            // within the token budget.
            var chunks = retrieveRelevantChunks(document.getId(), userQuestion);
            var sourceChunkReferences = buildSourceChunkReferences(chunks);

            // Build a coverage description so the LLM knows whether it has
            // the full document or a subset.
            var totalChunkCount = (int) documentChunkRepository.countByDocumentId(document.getId());
            var coverageDescription = buildCoverageDescription(chunks, totalChunkCount);
            log.debug("Selected {} of {} chunks within budget of {} tokens",
                    chunks.size(), totalChunkCount,
                    retrievalProperties.maxContextTokens());

            // Step 6: load and trim conversation history.
            var history = loadTrimmedHistory(conversationId);

            // Step 7: assemble the full prompt.
            var summary = documentSummaryRepository.findByDocumentId(document.getId())
                    .map(ds -> ds.getPlainSummary())
                    .orElse("No summary available.");
            var systemPrompt = buildSystemPrompt(document, summary, chunks, history,
                    coverageDescription);

            // Steps 8-11: stream the LLM response, accumulate, and persist.
            var responseBuilder = new StringBuilder();

            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userQuestion)
                    .stream()
                    .content()
                    .map(token -> {
                        responseBuilder.append(token);
                        return ChatStreamEvent.token(token);
                    })
                    .concatWith(Flux.defer(() -> {
                        var assistantMessage = saveAssistantMessage(
                                conversation,
                                responseBuilder.toString(),
                                sourceChunkReferences);
                        generateTitleIfNeeded(conversation, userQuestion);
                        return Flux.just(ChatStreamEvent.complete(
                                assistantMessage.getId().toString(),
                                sourceChunkReferences));
                    }))
                    .onErrorResume(error -> {
                        log.error("Error during LLM streaming for conversation {}",
                                conversationId, error);
                        return Flux.just(ChatStreamEvent.error(
                                "Something went wrong generating the response. Please try again."));
                    });
        });
    }

    /**
     * Loads a conversation by ID with ownership verification. The JPQL query
     * joins through the document to the user, ensuring the conversation belongs
     * to the given user. Also verifies the document has finished processing.
     */
    private Conversation loadAndVerifyConversation(UUID conversationId, UUID userId) {
        var conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        var document = conversation.getDocument();
        if (document.getStatus() != DocumentStatus.READY) {
            throw new DocumentNotReadyException(document.getId());
        }

        return conversation;
    }

    /**
     * Persists the user's message and updates the conversation's
     * {@code lastMessageAt} timestamp. The message is saved immediately so it
     * appears in the conversation history even if the LLM call subsequently
     * fails.
     */
    private void saveUserMessage(Conversation conversation, String userQuestion) {
        var message = new Message();
        message.setConversation(conversation);
        message.setRole(MessageRole.USER);
        message.setContent(userQuestion);
        message.setTokenCount(estimateTokenCount(userQuestion));
        messageRepository.save(message);

        conversation.setLastMessageAt(Instant.now());
        conversationRepository.save(conversation);
    }

    /**
     * Embeds the user's question and retrieves document chunks ordered by
     * cosine similarity, including as many as fit within the configured
     * token budget.
     *
     * <p>For small documents where all chunks fit within the budget, every
     * chunk is returned — giving the LLM full document context. For large
     * documents, the most relevant chunks are prioritised.
     */
    private List<DocumentChunk> retrieveRelevantChunks(UUID documentId, String userQuestion) {
        var queryEmbedding = embeddingService.embed(userQuestion);
        var vectorLiteral = embeddingService.toVectorLiteral(queryEmbedding);
        var similarChunks = documentChunkRepository.findSimilarChunks(
                documentId, vectorLiteral, retrievalProperties.maxChunks());
        return selectChunksWithinBudget(similarChunks, retrievalProperties.maxContextTokens());
    }

    /**
     * Selects as many chunks as fit within the given token budget, preserving
     * the similarity ordering from the database query.
     *
     * <p>For small documents where the total token count of all chunks is
     * within the budget, every chunk is included — giving the LLM full
     * document context. For large documents, the most relevant chunks (by
     * cosine similarity) are prioritised until the budget is filled.
     *
     * <p>At least one chunk is always included, even if it alone exceeds the
     * budget. This mirrors the minimum-retention guarantee in
     * {@link #trimHistory}.
     *
     * @param similarityOrderedChunks chunks ordered by cosine similarity
     *                                (most similar first)
     * @param tokenBudget             maximum total tokens of chunk content
     *                                to include
     * @return the selected chunks, still in similarity order
     */
    List<DocumentChunk> selectChunksWithinBudget(
            List<DocumentChunk> similarityOrderedChunks, int tokenBudget) {

        var selected = new ArrayList<DocumentChunk>();
        int totalTokens = 0;

        for (var chunk : similarityOrderedChunks) {
            int chunkTokens = chunk.getTokenCount();
            if (totalTokens + chunkTokens > tokenBudget && !selected.isEmpty()) {
                break;
            }
            // Always include at least one chunk, even if it exceeds the budget.
            selected.add(chunk);
            totalTokens += chunkTokens;
        }

        return selected;
    }

    /**
     * Builds a human-readable description of how much document content is
     * included in the prompt. Returns "full document" when all chunks are
     * present, or "most relevant N of M sections" otherwise.
     *
     * <p>This descriptor is inserted into the system prompt header so the
     * LLM knows whether it has complete or partial document coverage.
     *
     * @param selectedChunks  the chunks that were selected for the prompt
     * @param totalChunkCount the total number of chunks for the document
     * @return a concise coverage description
     */
    private String buildCoverageDescription(List<DocumentChunk> selectedChunks,
                                            int totalChunkCount) {
        if (selectedChunks.size() >= totalChunkCount) {
            return "full document";
        }
        return String.format("most relevant %d of %d sections",
                selectedChunks.size(), totalChunkCount);
    }

    /**
     * Loads recent messages for the conversation, reverses them into
     * chronological order, and trims to fit within the token budget.
     *
     * <p>The repository returns messages in descending {@code created_at}
     * order (newest first). This method reverses the list so the prompt
     * presents the conversation history chronologically.
     */
    private List<Message> loadTrimmedHistory(UUID conversationId) {
        var recentMessages = messageRepository.findRecentByConversationId(
                conversationId, chatProperties.maxHistoryMessages());

        // Reverse from newest-first to chronological order.
        var chronological = new ArrayList<>(recentMessages);
        Collections.reverse(chronological);

        return trimHistory(chronological, chatProperties.maxHistoryTokens());
    }

    /**
     * Trims a chronologically-ordered message list to fit within the given
     * token budget, keeping the most recent messages.
     *
     * <p>The algorithm works backwards from the end of the list (most recent),
     * summing token counts, and stops including messages when the next one
     * would exceed the budget. At least two messages (one user + one assistant
     * exchange) are always retained for conversational continuity.
     *
     * @param chronologicalMessages messages in chronological order (oldest first)
     * @param tokenBudget           maximum total tokens to include
     * @return a sublist of the most recent messages that fit within the budget
     */
    List<Message> trimHistory(List<Message> chronologicalMessages, int tokenBudget) {
        if (chronologicalMessages.size() <= 2) {
            return chronologicalMessages;
        }

        int totalTokens = 0;
        int startIndex = chronologicalMessages.size();

        // Always include the last 2 messages for conversational continuity.
        int minIncluded = 2;
        for (int i = chronologicalMessages.size() - 1;
                i >= chronologicalMessages.size() - minIncluded; i--) {
            totalTokens += tokenCountOf(chronologicalMessages.get(i));
            startIndex = i;
        }

        // Work backwards, adding older messages while within budget.
        for (int i = chronologicalMessages.size() - minIncluded - 1; i >= 0; i--) {
            int messageTokens = tokenCountOf(chronologicalMessages.get(i));
            if (totalTokens + messageTokens > tokenBudget) {
                break;
            }
            totalTokens += messageTokens;
            startIndex = i;
        }

        return chronologicalMessages.subList(startIndex, chronologicalMessages.size());
    }

    /**
     * Assembles the full system prompt from the template, substituting the
     * document type, summary, document content, coverage description, and
     * conversation history.
     *
     * <p>Chunks are sorted by {@code chunkIndex} before being written into the
     * prompt so the LLM reads the document in its original order, regardless
     * of the similarity-based order used during budget selection.
     */
    private String buildSystemPrompt(
            Document document,
            String summary,
            List<DocumentChunk> chunks,
            List<Message> history,
            String coverageDescription) {

        var documentTypeName = document.getDocumentType() != null
                ? document.getDocumentType().name().toLowerCase().replace('_', ' ')
                : "document";

        // Sort chunks by document order for natural reading flow.
        var orderedChunks = chunks.stream()
                .sorted(Comparator.comparingInt(DocumentChunk::getChunkIndex))
                .toList();

        var chunksText = new StringBuilder();
        for (var chunk : orderedChunks) {
            chunksText.append("[Section ").append(chunk.getChunkIndex() + 1).append("]\n");
            chunksText.append(chunk.getContent()).append("\n\n");
        }

        var historyText = new StringBuilder();
        for (var message : history) {
            historyText.append(message.getRole().name()).append(": ");
            historyText.append(message.getContent()).append("\n");
        }

        return String.format(SYSTEM_PROMPT_TEMPLATE,
                documentTypeName,
                summary,
                coverageDescription,
                chunksText.toString(),
                historyText.toString());
    }

    /**
     * Maps retrieved document chunks to {@link SourceChunkReference} DTOs,
     * capturing the chunk ID, index, and a short preview of the content.
     * These references are recorded at retrieval time (before the LLM
     * responds) to honestly represent what the model was given as context.
     */
    private List<SourceChunkReference> buildSourceChunkReferences(List<DocumentChunk> chunks) {
        return chunks.stream()
                .map(chunk -> new SourceChunkReference(
                        chunk.getId(),
                        chunk.getChunkIndex(),
                        truncatePreview(chunk.getContent())))
                .toList();
    }

    /**
     * Persists the assistant's response message with source chunk attribution.
     */
    private Message saveAssistantMessage(
            Conversation conversation,
            String responseContent,
            List<SourceChunkReference> sourceChunks) {

        var message = new Message();
        message.setConversation(conversation);
        message.setRole(MessageRole.ASSISTANT);
        message.setContent(responseContent);
        message.setSourceChunks(sourceChunks);
        message.setTokenCount(estimateTokenCount(responseContent));
        return messageRepository.save(message);
    }

    /**
     * Generates and persists a conversation title when this is the first
     * message exchange. Uses the user's opening question to produce a short,
     * descriptive title via the LLM.
     *
     * <p>Title generation failures are logged but never propagated — the
     * conversation continues to work with a {@code null} title, displayed
     * as "New conversation" in the frontend.
     */
    private void generateTitleIfNeeded(Conversation conversation, String userQuestion) {
        if (conversation.getTitle() != null) {
            return;
        }
        try {
            var title = titleGenerationService.generateTitle(userQuestion);
            if (title != null && !title.isBlank()) {
                conversation.setTitle(title);
                conversationRepository.save(conversation);
                log.debug("Generated title \"{}\" for conversation {}",
                        title, conversation.getId());
            }
        } catch (Exception ex) {
            log.warn("Title generation failed for conversation {}; leaving title as null",
                    conversation.getId(), ex);
        }
    }

    /**
     * Estimates the token count of a text using the characters-per-token
     * heuristic. English text with OpenAI's tokeniser averages roughly 4
     * characters per token.
     */
    int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / CHARS_PER_TOKEN;
    }

    /**
     * Returns the token count for a message, falling back to the heuristic
     * estimate if the stored count is null.
     */
    private int tokenCountOf(Message message) {
        return message.getTokenCount() != null
                ? message.getTokenCount()
                : estimateTokenCount(message.getContent());
    }

    /**
     * Truncates text to {@link #SOURCE_PREVIEW_LENGTH} characters for use
     * as a source chunk preview. Attempts to break at a word boundary to
     * avoid cutting words in half.
     */
    private String truncatePreview(String text) {
        if (text == null || text.length() <= SOURCE_PREVIEW_LENGTH) {
            return text;
        }

        // Try to break at the last space before the limit.
        int breakPoint = text.lastIndexOf(' ', SOURCE_PREVIEW_LENGTH);
        if (breakPoint <= 0) {
            breakPoint = SOURCE_PREVIEW_LENGTH;
        }

        return text.substring(0, breakPoint) + "...";
    }
}

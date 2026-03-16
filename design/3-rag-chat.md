# RAG & Chat — Feature Specification

*Phase 2 implementation detail for the Jargoyle conversational Q&A pipeline.*

This document expands on the Phase 2 section of the [main specification](1-jargoyle-spec.md) with enough detail to implement the RAG-powered chat system end-to-end. It covers database schema (pgvector), backend services, frontend components, and the integration points between them.

**Design philosophy**: Phase 1 delivered the vertical slice from upload to summary. Phase 2 completes the core product loop — users can now *ask questions* about their documents and receive answers grounded in the actual content. The existing async pipeline, SSE infrastructure, and ownership model are all reused and extended. Nothing built in Phase 1 needs reworking; Phase 2 slots in alongside it.

---

## 1. Scope

### In scope

- **Chunking pipeline** — split extracted text into overlapping semantic chunks, generate vector embeddings, store in PostgreSQL via pgvector
- **pgvector extension setup** — enable the extension, create the `document_chunks` table with a `vector(1536)` column
- **Chat endpoint with RAG retrieval** — embed the user's question, retrieve top-K similar chunks scoped to the document, build a context-enriched prompt, call the LLM
- **SSE streaming for chat responses** — stream LLM response tokens to the frontend in real-time via `text/event-stream`
- **Conversation persistence** — `conversations` and `messages` tables; source chunk attribution on assistant messages
- **React chat UI** — message list with streaming token display, input box, conversation history, source attribution
- **Suggested starter questions** — context-aware questions generated from the document type and summary
- **Source chunk attribution** — track which document chunks were used to ground each response; display in the UI

### Out of scope (deferred to later phases)

- Image upload and vision LLM extraction (Phase 3)
- "Show source" highlighting in the original document view (Phase 3 — requires the original document panel)
- Cross-document search or multi-document conversations
- S3 or cloud storage (production hardening)
- Rate limiting on chat endpoints
- Conversation renaming or deletion via UI

---

## 2. Database Migrations

### V5 — Enable pgvector extension

**File**: `src/backend/jargoyle-web/src/main/resources/db/migration/V5__enable_pgvector_extension.sql`

```sql
create extension if not exists vector;
```

**Design notes:**

- **Why a separate migration?** Enabling an extension is a DDL operation that should happen before any tables reference the `vector` type. Keeping it in its own migration makes the dependency explicit and means it runs (and is recorded by Flyway) independently of table creation. If the extension is already enabled (e.g. on a shared database), `if not exists` makes this idempotent.
- **pgvector must be installed on the PostgreSQL instance.** The Docker Compose file for local development should use the `pgvector/pgvector:pg17` image (or equivalent) rather than the plain `postgres` image. This is a deployment prerequisite, not a Flyway concern.

### V6 — Document chunks table

**File**: `src/backend/jargoyle-web/src/main/resources/db/migration/V6__create_document_chunks_table.sql`

```sql
create table document_chunks (
    id uuid primary key,
    document_id uuid not null references documents(id) on delete cascade,
    chunk_index integer not null,
    content text not null,
    embedding vector(1536),
    token_count integer not null,
    created_at timestamp with time zone not null default now()
);

create index idx_document_chunks_document_id on document_chunks(document_id);
create index idx_document_chunks_document_id_chunk_index on document_chunks(document_id, chunk_index);
```

**Design notes:**

- **`chunk_index`** — the ordering position of this chunk within the document (0-based). Essential for reassembling chunks in document order when displaying source attribution. Also allows "show the surrounding context" by fetching `chunk_index - 1` and `chunk_index + 1`.
- **`content`** — the text of the chunk. Stored alongside the embedding so that retrieval returns both the text (for prompt construction) and the vector (for the search itself) without a second query.
- **`embedding` as `vector(1536)`** — 1536 dimensions matches OpenAI's `text-embedding-3-small` model, which is the embedding model we shall use. The `vector` type is provided by the pgvector extension. **Nullable** because the chunk is created first (during text splitting) and the embedding is written in a subsequent batch step. This two-phase approach allows the chunking logic to be tested independently of the embedding API.
- **`token_count`** — the number of tokens in this chunk, counted at chunking time. Used for context window budget management when building chat prompts: we can sum `token_count` across retrieved chunks to stay within limits without re-tokenising at query time.
- **No `vector` index (yet)** — pgvector supports IVFFlat and HNSW indexes for approximate nearest-neighbour search. At current scale (hundreds of chunks per document, not millions), exact search via `ORDER BY embedding <=> query_vector LIMIT K` is fast enough. Adding an HNSW index later is a single `CREATE INDEX` statement and requires no code changes. Over-indexing now would add complexity (index tuning parameters, reindexing on insert) without measurable benefit.
- **`on delete cascade`** on `document_id` — when a document is deleted, all its chunks are removed. This is consistent with the `document_summaries` cascade behaviour from Phase 1.
- **Composite index on `(document_id, chunk_index)`** — serves the "get all chunks for a document in order" query, which is needed for both the original-text display and debugging the chunking pipeline.

### V7 — Conversations table

**File**: `src/backend/jargoyle-web/src/main/resources/db/migration/V7__create_conversations_table.sql`

```sql
create table conversations (
    id uuid primary key,
    document_id uuid not null references documents(id) on delete cascade,
    title varchar(255),
    created_at timestamp with time zone not null default now(),
    last_message_at timestamp with time zone not null default now()
);

create index idx_conversations_document_id on conversations(document_id);
create index idx_conversations_document_id_last_message_at on conversations(document_id, last_message_at desc);
```

**Design notes:**

- **No `user_id` column** — this is deliberate. Ownership is inferred through the relationship: `conversation → document → user`. Adding `user_id` here would be denormalisation. The question is whether this costs us query performance.
    - **For "list conversations for a document"**: The query is `WHERE document_id = ?`, and the caller has already verified document ownership. No join needed — the `document_id` index is sufficient.
    - **For "get conversation by ID"**: The query joins through `documents` to verify `user_id`. This is a single join on two UUID primary keys — negligible cost.
    - **If this becomes a bottleneck**, adding `user_id` later is a single migration + a non-null backfill from `documents.user_id`. The normalised design is preferred until profiling shows otherwise.
- **`title`** — nullable. Defaults to null; can be auto-generated from the first message ("Conversation about late fees...") or set explicitly. Useful for the sidebar UI when a user has multiple conversations on the same document.
- **`last_message_at`** — denormalised timestamp updated every time a message is added. Avoids a `MAX(created_at)` subquery on the `messages` table when sorting conversations by recency. This is the one denormalisation that pays for itself immediately — the "list conversations" query is called on every document page load.
- **`on delete cascade`** on `document_id` — deleting a document removes all its conversations (and their messages, via the next migration's cascade).
- **Composite index on `(document_id, last_message_at desc)`** — serves the dashboard query "conversations for this document, most recent first".

### V8 — Messages table

**File**: `src/backend/jargoyle-web/src/main/resources/db/migration/V8__create_messages_table.sql`

```sql
create table messages (
    id uuid primary key,
    conversation_id uuid not null references conversations(id) on delete cascade,
    role varchar(20) not null,
    content text not null,
    source_chunks jsonb,
    token_count integer,
    created_at timestamp with time zone not null default now()
);

create index idx_messages_conversation_id on messages(conversation_id);
create index idx_messages_conversation_id_created_at on messages(conversation_id, created_at);
```

**Design notes:**

- **`role` as `varchar(20)`** — stores `'USER'` or `'ASSISTANT'`. Using a varchar rather than a database enum is consistent with how `DocumentStatus`, `DocumentType`, and `Role` are stored throughout the project. Validation happens in the Java `MessageRole` enum.
- **`content`** — the full message text. For user messages, this is exactly what they typed. For assistant messages, this is the complete LLM response (accumulated from the stream). Stored as `text` because responses can be long.
- **`source_chunks` as JSONB** — an array of chunk reference objects for assistant messages. Structure:
  ```json
  [
    { "chunkId": "uuid-here", "chunkIndex": 3, "preview": "First 100 chars of chunk..." },
    { "chunkId": "uuid-here", "chunkIndex": 7, "preview": "First 100 chars of chunk..." }
  ]
  ```
  **Why JSONB rather than a join table?** The source chunk references are written once (when the assistant message is created) and read as-is for display. They are never queried independently ("find all messages that reference chunk X" is not a use case). A join table (`message_source_chunks`) would add an extra table, an extra join on every message read, and an extra insert per chunk per message — all for query flexibility we don't need. JSONB is the right tool for write-once, read-with-parent data. Nullable because user messages have no source chunks.
- **`token_count`** — the number of tokens in this message. Nullable (we may not always count). Used for managing the conversation history window: when building the prompt for a new message, we sum `token_count` from the most recent messages and stop including messages when the budget is exhausted. This avoids re-tokenising the full conversation history on every chat request.
- **Composite index on `(conversation_id, created_at)`** — serves the "messages in this conversation, chronological order" query. Also supports the "last N messages" query used for building the conversation history portion of the prompt.
- **`on delete cascade`** on `conversation_id` — deleting a conversation removes all its messages. Combined with the cascade on `conversations.document_id`, deleting a document removes everything: summary, chunks, conversations, messages.

---

## 3. Backend Components

### 3.1 Entities

#### `DocumentChunk`

**Package**: `com.jargoyle.entity`

Follows the same entity patterns as `Document` and `DocumentSummary` — UUID primary key with `@GeneratedValue(strategy = GenerationType.UUID)`, `@CreationTimestamp`, explicit getters and setters, no-arg constructor.

| Field | Type | JPA annotations | Notes |
|-------|------|-----------------|-------|
| `id` | `UUID` | `@Id @GeneratedValue(strategy = UUID)` | |
| `document` | `Document` | `@ManyToOne(fetch = LAZY)` `@JoinColumn(name = "document_id")` | Many chunks per document |
| `chunkIndex` | `int` | | 0-based ordering within the document |
| `content` | `String` | `@Column(columnDefinition = "text")` | The chunk text |
| `embedding` | `float[]` | `@Column(columnDefinition = "vector(1536)")` | See pgvector mapping note below |
| `tokenCount` | `int` | | Token count at chunking time |
| `createdAt` | `Instant` | `@CreationTimestamp` | |

**pgvector column mapping approach**: Hibernate 6.6+ (which ships with Spring Boot 4.0.3) does not have native `vector` type support. There are two practical approaches:

1. **Use a `float[]` field with a custom Hibernate `UserType`** that maps between Java `float[]` and PostgreSQL's `vector` type. This is the most explicit approach and keeps full control.
2. **Use the `io.hypersistence:hypersistence-utils-hibernate-63` library** which provides `@Type(PostgreSQLVectorType.class)` for automatic mapping.

**Recommendation: option 1 — custom `UserType`.** A custom `VectorType` class (roughly 40 lines) that implements `org.hibernate.usertype.UserType<float[]>` avoids adding a third-party library for a single type mapping and makes the pgvector integration explicit rather than magical. The type is registered on the field via `@Type(VectorType.class)`.

**Why not Spring AI's `PgVectorStore`?** Spring AI's `PgVectorStore` is designed for a general-purpose vector store — it manages its own table schema, its own embedding calls, and its own similarity search. We need ownership-scoped search (only search within one user's document), we want the chunks to have a foreign key to `documents`, and we want to control the chunking logic. Using `PgVectorStore` would mean fighting the abstraction at every turn. Custom repository queries with pgvector's `<=>` (cosine distance) operator give us full control with minimal code.

#### `Conversation`

| Field | Type | JPA annotations | Notes |
|-------|------|-----------------|-------|
| `id` | `UUID` | `@Id @GeneratedValue(strategy = UUID)` | |
| `document` | `Document` | `@ManyToOne(fetch = LAZY)` `@JoinColumn(name = "document_id")` | |
| `title` | `String` | | Nullable — auto-generated or user-set |
| `createdAt` | `Instant` | `@CreationTimestamp` | |
| `lastMessageAt` | `Instant` | | Updated programmatically on each new message |

**Why no `@UpdateTimestamp` on `lastMessageAt`?** `@UpdateTimestamp` fires on *any* update to the entity. We only want `lastMessageAt` updated when a new message is added, not when the conversation title is edited. Manual updates in the service layer give precise control.

#### `Message`

| Field | Type | JPA annotations | Notes |
|-------|------|-----------------|-------|
| `id` | `UUID` | `@Id @GeneratedValue(strategy = UUID)` | |
| `conversation` | `Conversation` | `@ManyToOne(fetch = LAZY)` `@JoinColumn(name = "conversation_id")` | |
| `role` | `MessageRole` | `@Enumerated(STRING)` | `USER` or `ASSISTANT` |
| `content` | `String` | `@Column(columnDefinition = "text")` | Full message text |
| `sourceChunks` | `String` | `@Column(columnDefinition = "jsonb")` `@JdbcTypeCode(SqlTypes.JSON)` | JSON array of chunk references. Nullable. |
| `tokenCount` | `Integer` | | Nullable. Token count for budget management. |
| `createdAt` | `Instant` | `@CreationTimestamp` | |

**Why store `sourceChunks` as a raw JSON `String`?** Same rationale as `keyFacts` and `flaggedTerms` on `DocumentSummary` — the data is written once by the service layer and passed through to the frontend as-is. Mapping it to Java objects would create a parallel structure for no querying benefit.

### 3.2 Enums

#### `MessageRole`

```
USER, ASSISTANT
```

Two values only. The `SYSTEM` role exists in the LLM API but is never persisted — system prompts are constructed dynamically at query time, not stored as messages. Keeping `SYSTEM` out of the enum prevents accidental persistence of system prompt content (which could leak internal prompt engineering to the frontend).

### 3.3 Repositories

#### `DocumentChunkRepository`

```java
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(UUID documentId);

    void deleteByDocumentId(UUID documentId);

    @Query(value = """
        select dc.* from document_chunks dc
        where dc.document_id = :documentId
          and dc.embedding is not null
        order by dc.embedding <=> cast(:queryEmbedding as vector)
        limit :topK
        """, nativeQuery = true)
    List<DocumentChunk> findTopKSimilar(
        @Param("documentId") UUID documentId,
        @Param("queryEmbedding") String queryEmbedding,
        @Param("topK") int topK);

    long countByDocumentId(UUID documentId);
}
```

**Why `findTopKSimilar` uses a native query**: Spring Data JPA's query derivation cannot express pgvector's `<=>` cosine distance operator or the `cast(:queryEmbedding as vector)` conversion. A native query is unavoidable here.

**Why pass the embedding as a `String`?** pgvector accepts vector literals as strings in the format `'[0.1, 0.2, ...]'`. Passing the embedding as a string and casting it in SQL avoids JDBC driver complications with the `vector` type. The service layer converts the `float[]` to this string format before calling the repository.

**Why `dc.embedding is not null`?** Chunks are created before embeddings are generated (two-phase pipeline). This guard prevents newly-created-but-not-yet-embedded chunks from appearing in search results during the brief window between chunking and embedding.

**Why `findByDocumentIdOrderByChunkIndex`?** Used for displaying all chunks of a document in order (e.g. for debugging, for the "show source" UI, and for the chunking step in tests).

#### `ConversationRepository`

```java
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByDocumentIdOrderByLastMessageAtDesc(UUID documentId);

    @Query("""
        select c from Conversation c
        join c.document d
        where c.id = :conversationId
          and d.user.id = :userId
        """)
    Optional<Conversation> findByIdAndUserId(
        @Param("conversationId") UUID conversationId,
        @Param("userId") UUID userId);

    long countByDocumentId(UUID documentId);
}
```

**Why `findByIdAndUserId` uses a JPQL query rather than method name derivation?** The ownership check traverses two relationships: `conversation → document → user`. Spring Data's method name derivation would be `findByIdAndDocumentUserId` which works but is fragile (it depends on Hibernate correctly resolving the property path through two associations). An explicit JPQL query makes the join path clear and testable.

**Why return `List` not `Page` for `findByDocumentIdOrderByLastMessageAtDesc`?** A single document is unlikely to have more than a handful of conversations. Pagination adds complexity without value here. If this assumption proves wrong, switching to `Page` is a single method signature change.

#### `MessageRepository`

```java
public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationIdOrderByCreatedAtAsc(
        UUID conversationId, Pageable pageable);

    @Query(value = """
        select m.* from messages m
        where m.conversation_id = :conversationId
        order by m.created_at desc
        limit :limit
        """, nativeQuery = true)
    List<Message> findRecentByConversationId(
        @Param("conversationId") UUID conversationId,
        @Param("limit") int limit);

    long countByConversationId(UUID conversationId);
}
```

**Why two query methods?** They serve different purposes:

- `findByConversationIdOrderByCreatedAtAsc` — paginated, chronological order. Serves the UI's message history display (oldest first, with "load more" for earlier messages).
- `findRecentByConversationId` — unpaginated, most-recent-first, limited. Serves prompt construction: "give me the last N messages for conversation context". The native query is used because Spring Data's derived query names cannot express `LIMIT` without `Pageable` (and we want a simple `List`, not a `Page`).

**Note**: `findRecentByConversationId` returns messages in descending `created_at` order (newest first). The service layer reverses this list before inserting into the prompt, because the LLM expects conversation history in chronological order.

### 3.4 DTOs

#### `ChatRequest`

Request body for `POST /api/conversations/{id}/messages`:

```java
public record ChatRequest(
    @NotBlank @Size(max = 5000) String content
) {}
```

- **`content`** — the user's question. `@NotBlank` rejects null, empty, and whitespace-only strings. `@Size(max = 5000)` prevents excessively long messages that would consume the token budget. 5000 characters is roughly 1250 tokens — enough for a detailed question with context, but not so much that it crowds out retrieved chunks and history.

#### `ChatStreamEvent`

SSE event payload, sent as `text/event-stream`:

```java
public record ChatStreamEvent(
    String type,
    String content,
    String messageId,
    List<SourceChunkReference> sourceChunks
) {
    public static ChatStreamEvent token(String content) {
        return new ChatStreamEvent("TOKEN", content, null, null);
    }

    public static ChatStreamEvent complete(String messageId, List<SourceChunkReference> sourceChunks) {
        return new ChatStreamEvent("COMPLETE", null, messageId, sourceChunks);
    }

    public static ChatStreamEvent error(String message) {
        return new ChatStreamEvent("ERROR", message, null, null);
    }
}
```

**Three event types**:
- `TOKEN` — a partial response token. `content` contains the text fragment. Sent rapidly as the LLM generates output.
- `COMPLETE` — the response is finished. `messageId` is the UUID of the persisted assistant message. `sourceChunks` lists the chunks that grounded the response. The frontend uses this to stop the streaming indicator and render source attribution.
- `ERROR` — something went wrong mid-stream. `content` contains a user-facing error message.

**Why a `type` field rather than SSE event names?** SSE supports named events (`event: token\ndata: ...`), but using a `type` field in the JSON data is simpler for the frontend to parse — it's just a JSON property rather than requiring event listener registration per type. Both approaches work; this one keeps the parsing logic in one place.

#### `SourceChunkReference`

```java
public record SourceChunkReference(
    UUID chunkId,
    int chunkIndex,
    String preview
) {}
```

- **`chunkId`** — the UUID of the `DocumentChunk` that was used. Allows the frontend to link to or highlight the specific chunk.
- **`chunkIndex`** — the chunk's position within the document (0-based). Allows the frontend to say "Section 4" or show position context without a round-trip.
- **`preview`** — the first 150 characters of the chunk's content. Displayed in the UI as a tooltip or expandable reference so the user can see *what* was referenced without loading the full chunk.

**Why `List<SourceChunkReference>` rather than `List<UUID>`?** Returning just IDs would require the frontend to make a separate API call to fetch chunk details for display. Including `chunkIndex` and `preview` eliminates that round-trip. The cost is a slightly larger SSE payload, but the data is small (3 fields per chunk, ~5 chunks per response).

#### `ConversationResponse`

```java
public record ConversationResponse(
    UUID id,
    UUID documentId,
    String title,
    int messageCount,
    Instant createdAt,
    Instant lastMessageAt
) {}
```

#### `MessageResponse`

```java
public record MessageResponse(
    UUID id,
    String role,
    String content,
    List<SourceChunkReference> sourceChunks,
    Instant createdAt
) {}
```

- **`sourceChunks`** — parsed from the JSONB column. Null for user messages, populated for assistant messages. The backend deserialises the JSON into `SourceChunkReference` objects so the frontend receives typed data rather than raw JSON.

#### `SuggestedQuestion`

```java
public record SuggestedQuestion(
    String text,
    String category
) {}
```

- **`text`** — the question to display (e.g. "What happens if I pay this bill late?").
- **`category`** — a grouping label (e.g. "Costs", "Deadlines", "Rights"). Allows the frontend to visually group suggestions if desired.

#### `CreateConversationResponse`

```java
public record CreateConversationResponse(
    UUID id,
    UUID documentId,
    List<SuggestedQuestion> suggestedQuestions
) {}
```

Returned by `POST /api/documents/{id}/conversations`. Includes suggested questions so the UI can display them immediately without a second request.

### 3.5 Services

#### `ChunkingService`

**Package**: `com.jargoyle.service`

Responsible for splitting extracted text into semantic chunks suitable for embedding and retrieval.

```java
@Service
public class ChunkingService {

    public List<TextChunk> chunkText(String extractedText)
}
```

Where `TextChunk` is an internal record:

```java
public record TextChunk(
    int index,
    String content,
    int tokenCount
) {}
```

**Algorithm — section-aware splitting with token-based fallback**:

1. **Attempt section-aware splitting first**. Scan the text for structural markers:
   - Headings (lines in ALL CAPS, or lines followed by a blank line that are shorter than 80 chars)
   - Numbered clauses (lines starting with patterns like `1.`, `1.1`, `(a)`, `(i)`)
   - Section separators (lines of dashes, equals signs, or horizontal rules)

   If structural markers are found, split at those boundaries. Each section becomes a candidate chunk.

2. **Check candidate chunks against the token budget**. For each candidate:
   - If it's within the target size (~500 tokens), keep it as-is.
   - If it's too large, apply the token-based fallback splitter to that section.
   - If it's too small (< 100 tokens), merge it with the next section.

3. **Token-based fallback splitting**. For sections that exceed the target size, or for text with no detectable structure:
   - Split on sentence boundaries (`. `, `? `, `! ` followed by a capital letter or newline).
   - Accumulate sentences until the token budget (~500 tokens) is reached.
   - Apply overlap: include the last ~50 tokens of the previous chunk at the start of the next chunk. Overlap ensures that information at chunk boundaries isn't lost during retrieval.

4. **Token counting**. Use a simple heuristic: `text.length() / 4` as an approximation of token count. This is surprisingly accurate for English text with OpenAI's tokeniser (which averages ~4 characters per token). For exact counts, the `jtokkit` library could be used, but the heuristic is sufficient for chunking decisions. The approximate count is stored in `token_count` on the chunk entity.

**Configuration** (via `ChatProperties` — see section 3.6):
- `jargoyle.rag.chunk.target-tokens`: `500` — target chunk size in tokens
- `jargoyle.rag.chunk.overlap-tokens`: `50` — overlap between consecutive chunks
- `jargoyle.rag.chunk.min-tokens`: `100` — minimum chunk size before merging

**Why ~500 tokens as the target?** This is the widely-used sweet spot for RAG:
- Large enough to capture a complete thought or clause (a single sentence is often too fragmented for meaningful retrieval).
- Small enough that retrieval is precise (a 2000-token chunk would include too much irrelevant context alongside the relevant passage).
- With top-K=5 retrieval, five 500-token chunks consume ~2500 tokens of context — leaving plenty of room for the system prompt, conversation history, and the LLM's response in a 16K or 128K context window.

**Why 50 tokens of overlap?** Without overlap, a question about content that spans a chunk boundary would fail to retrieve either chunk fully. 50 tokens (~200 characters, roughly 2-3 sentences) is enough to capture boundary context without significantly inflating storage or retrieval noise.

#### `EmbeddingService`

**Package**: `com.jargoyle.service`

Wrapper around Spring AI's `EmbeddingModel` that handles batch embedding and the conversion between `float[]` and the string format needed for pgvector queries.

```java
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public float[] embed(String text)

    public List<float[]> embedBatch(List<String> texts)

    public String toVectorLiteral(float[] embedding)
}
```

- **`embed(String text)`** — embeds a single text (used for embedding the user's question at query time).
- **`embedBatch(List<String> texts)`** — embeds multiple texts in a single API call (used for embedding all chunks of a document at once). OpenAI's embedding API accepts batch requests, and Spring AI's `EmbeddingModel.embed(List<String>)` uses this capability. Batching is important because a document might have 50+ chunks — 50 individual API calls would be slow and wasteful.
- **`toVectorLiteral(float[] embedding)`** — converts a `float[]` to the pgvector string literal format `[0.1, 0.2, ...]` for use in native SQL queries. This is needed because the `DocumentChunkRepository.findTopKSimilar` method accepts the query embedding as a `String`.

**Embedding model**: OpenAI's `text-embedding-3-small` (1536 dimensions). This is configured via Spring AI's OpenAI starter — the same starter already used for chat in Phase 1. The embedding model is separate from the chat model; both are configured under `spring.ai.openai`:

```yaml
spring:
  ai:
    openai:
      embedding:
        options:
          model: text-embedding-3-small
```

**Why `text-embedding-3-small` rather than `text-embedding-3-large`?** The small model (1536 dimensions) offers excellent retrieval quality for document Q&A at lower cost and faster latency than the large model (3072 dimensions). For document-scale Q&A, the quality difference is negligible. The dimension count also affects storage (each chunk's embedding is 1536 * 4 bytes = ~6 KB).

#### `ChatService`

**Package**: `com.jargoyle.service`

The RAG orchestrator. This is the most complex service in Phase 2 — it coordinates the full flow from user question to streamed response.

```java
@Service
public class ChatService {

    public Flux<ChatStreamEvent> chat(UUID conversationId, UUID userId, String userQuestion)
}
```

**Full algorithm**:

1. **Verify ownership.** Load the conversation via `ConversationRepository.findByIdAndUserId(conversationId, userId)`. Throw `ConversationNotFoundException` if not found (returns 404 — same pattern as document ownership).

2. **Verify document readiness.** Load the conversation's document. If `document.status != READY`, throw `DocumentNotReadyException` — the document must be fully processed (text extracted, chunks embedded, summary generated) before chat is available.

3. **Save the user message.** Create a `Message` entity with `role = USER`, `content = userQuestion`. Persist it immediately so it appears in the conversation history even if the LLM call fails. Update `conversation.lastMessageAt` to now.

4. **Embed the question.** Call `embeddingService.embed(userQuestion)` to generate the query embedding vector.

5. **Retrieve top-K similar chunks.** Call `documentChunkRepository.findTopKSimilar(documentId, embeddingService.toVectorLiteral(queryEmbedding), topK)`. Default `topK` is 5.

6. **Load conversation history.** Call `messageRepository.findRecentByConversationId(conversationId, maxHistoryMessages)`. Default `maxHistoryMessages` is 10. Reverse the list to chronological order.

7. **Build the prompt.** Assemble the full prompt with token budget management (see prompt template and token budget sections below).

8. **Stream the LLM response.** Call `chatClient.prompt().stream()` to get a `Flux<String>` of response tokens. Map each token to a `ChatStreamEvent.token(token)`.

9. **Accumulate the response.** As tokens stream, accumulate them into a `StringBuilder` to build the complete response text.

10. **On stream completion:** Save the assistant message with:
    - `role = ASSISTANT`
    - `content = accumulatedResponse`
    - `sourceChunks = JSON-serialised list of SourceChunkReference` from the chunks retrieved in step 5
    - `tokenCount = approximate token count of the response`

    Emit a final `ChatStreamEvent.complete(messageId, sourceChunkReferences)`.

11. **On stream error:** Emit a `ChatStreamEvent.error(userFacingMessage)`. Log the full error. Do *not* save a partial assistant message — the user can retry.

**Why `Flux<ChatStreamEvent>` rather than `void` with SSE callbacks?** Spring WebFlux's `Flux` is the natural return type for a streamed response. Even though the application uses Spring MVC (not WebFlux), Spring MVC supports returning `Flux` from controller methods — it automatically converts to an SSE stream when the `produces` media type is `text/event-stream`. This is cleaner than manually managing `SseEmitter` instances (the Phase 1 approach for processing status), because the chat stream has a clear lifecycle: one request → one stream of tokens → completion. The `SseEmitter` approach from Phase 1 is designed for long-lived connections with multiple unrelated events, which is a different pattern.

**Prompt template**:

```
SYSTEM:
You are Jargoyle, a friendly document explainer that helps regular people
understand their {documentType}. You speak in plain, clear English — no jargon,
no legalese.

Rules:
- Only answer based on the document content provided below. Do not use general
  knowledge or make assumptions beyond what the document states.
- If the answer is not in the document, say so clearly: "I can't find that in
  your document."
- When referencing specific amounts, dates, or terms, quote them exactly from
  the document.
- Keep answers concise but thorough. Use bullet points for lists.
- If the document uses jargon, explain it in parentheses.
- Reminder: You provide plain-English interpretations, not legal or financial advice.

--- DOCUMENT SUMMARY ---
{summary}

--- RELEVANT SECTIONS ---
{retrievedChunks}

--- CONVERSATION HISTORY ---
{conversationHistory}

USER:
{userQuestion}
```

**Why include the summary in the prompt alongside retrieved chunks?** The summary provides broad context that the retrieved chunks might lack. For example, if the user asks "Is this a good deal?", the retrieved chunks might contain specific clauses, but the summary provides the overall picture (document type, total amounts, key dates). The summary costs ~200-400 tokens but significantly improves answer quality for questions that require holistic understanding.

**Token budget management**:

The total context window for `gpt-5-mini` is large (128K tokens), but being economical with tokens reduces cost and latency. The budget allocation:

| Component | Token budget | Rationale |
|-----------|-------------|-----------|
| System prompt | ~300 | Fixed template with variable substitutions |
| Document summary | ~400 | The `plainSummary` from `document_summaries` |
| Retrieved chunks (top-5) | ~2500 | 5 chunks * ~500 tokens each |
| Conversation history | ~2000 | Last 10 messages, trimmed if needed |
| User question | ~300 | Capped by the 5000-character request limit |
| Response headroom | ~4000 | Space for the LLM to generate its answer |
| **Total target** | **~9500** | Well within even a 16K context window |

**History trimming algorithm**: If the conversation history exceeds the budget:
1. Start with the most recent message and work backwards.
2. Sum `token_count` for each message.
3. Stop including messages when adding the next one would exceed the history budget.
4. Always include at least the last 2 messages (one user + one assistant) for conversational continuity.

This is implemented as a utility method on `ChatService`:

```java
private List<Message> trimHistory(List<Message> recentMessages, int tokenBudget)
```

**Source attribution**: The chunk IDs are captured at step 5 (retrieval), *before* the LLM generates its response. This is important: we record which chunks the model was *given* as context, not which chunks the model's output is most similar to. This approach is honest — it shows the user "here's what Jargoyle was looking at when it answered" rather than making a post-hoc claim about which sources the answer "came from". The LLM might synthesise information across chunks or rephrase content, and that's fine — the source attribution shows the input context, not the output derivation.

#### `SuggestedQuestionService`

**Package**: `com.jargoyle.service`

Generates context-aware starter questions for a document.

```java
@Service
public class SuggestedQuestionService {

    public List<SuggestedQuestion> getSuggestions(DocumentType documentType, String plainSummary)
}
```

**Two-tier approach**:

1. **Static suggestions by document type** — a curated map of document types to relevant questions. These are always available, even before the summary is generated:

   | Document type | Suggested questions |
   |---------------|-------------------|
   | `BILL` | "What am I being charged for?", "What happens if I pay late?", "What's the biggest line item?" |
   | `INSURANCE` | "What's actually covered?", "What are the exclusions?", "How do I make a claim?" |
   | `RENTAL` | "What are my obligations as a tenant?", "Can the rent be increased?", "What's the notice period?" |
   | `MORTGAGE` | "What's the interest rate?", "Are there early repayment penalties?", "What fees are included?" |
   | `BANK_TERMS` | "What are the account fees?", "What are the overdraft terms?", "How is interest calculated?" |
   | `CONTRACT` | "What are the key obligations?", "What are the termination conditions?", "Are there penalty clauses?" |
   | `GOVERNMENT` | "What action do I need to take?", "Are there any deadlines?", "What are the consequences of non-compliance?" |
   | `MEDICAL` | "What does this diagnosis mean?", "What treatment is recommended?", "What are the next steps?" |
   | `TAX` | "What's my total tax liability?", "Are there any deductions I should know about?", "When is this due?" |
   | `OTHER` | "What is this document about?", "Are there any deadlines?", "What should I do next?" |

2. **Dynamic suggestions from the summary** (future enhancement, not in initial implementation). The LLM could analyse the summary and generate document-specific questions like "Why was there a $15 charge on 2 February?" — but this adds an extra LLM call per conversation creation. For Phase 2, the static suggestions are sufficient. The service interface supports this extension without changes.

**Why a service rather than a static map in the controller?** The service allows future enhancement (LLM-generated questions) without changing the controller. It also allows injecting configuration (e.g. maximum number of suggestions) and is testable in isolation.

#### Integration with `DocumentProcessingService`

The existing async pipeline in `DocumentProcessingService` currently runs: extract text → generate summary → save results.

Phase 2 adds two new steps: **chunk text** and **embed chunks**. The updated pipeline:

1. **Update status** → `PROCESSING`
2. **Extract text** (existing) → delegates to `TextExtractionService`
3. **Chunk text** (new) → delegates to `ChunkingService`, saves `DocumentChunk` entities (without embeddings)
4. **Generate embeddings** (new) → delegates to `EmbeddingService.embedBatch()`, updates chunk entities with embeddings
5. **Generate summary** (existing) → delegates to `SummaryGenerationService`
6. **Update status** → `READY`

**Why chunking runs before summary generation, not in parallel?** Two reasons:

- **Ordering guarantees**: The document should only reach `READY` status when *all* processing is complete — text extracted, chunks embedded, summary generated. Running chunking and summary in parallel would require coordinating two async operations to determine when both are done, adding complexity.
- **Error handling simplicity**: The sequential pipeline has one error path. If chunking fails, the document goes to `FAILED` before summary generation is attempted. If they ran in parallel, one could succeed while the other fails, leaving the document in an inconsistent state.

**Why not chunk after summary generation?** Summary generation uses the full extracted text, not chunks, so it doesn't depend on chunking. But chunking and embedding are the slower operations (embedding 50+ chunks via the API). Running chunking first means the embedding API calls and the summary LLM call don't compete for API rate limits. In practice, the order doesn't matter much — the total time is the sum of all steps regardless.

New SSE status events for the pipeline:
- `"Splitting document into sections..."` (during chunking)
- `"Generating embeddings..."` (during embedding)

### 3.6 Controller

#### `ConversationController`

**Base path**: split across two resource paths to match the REST resource hierarchy.

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/api/documents/{documentId}/conversations` | Create new conversation | `201 Created` + `CreateConversationResponse` |
| `GET` | `/api/documents/{documentId}/conversations` | List conversations for a document | `200 OK` + `List<ConversationResponse>` |
| `GET` | `/api/conversations/{conversationId}/messages` | Get message history (paged) | `200 OK` + `Page<MessageResponse>` |
| `POST` | `/api/conversations/{conversationId}/messages` | Send message, stream response | `text/event-stream` + `Flux<ChatStreamEvent>` |

**Create conversation** (`POST /api/documents/{documentId}/conversations`):
- Verifies document ownership via `documentService.getById(userId, documentId)`.
- Creates a new `Conversation` entity.
- Returns `201 Created` with the conversation ID and suggested questions.
- Does not require a first message — the conversation is created empty. The frontend sends the first message as a separate request, which allows the user to choose a suggested question or type their own.

**List conversations** (`GET /api/documents/{documentId}/conversations`):
- Verifies document ownership.
- Returns all conversations for the document, sorted by `lastMessageAt` descending.
- Includes `messageCount` for each conversation so the UI can show "5 messages" without loading them.

**Get messages** (`GET /api/conversations/{conversationId}/messages`):
- Verifies ownership via `conversationRepository.findByIdAndUserId(conversationId, userId)`.
- Returns paginated messages in chronological order (oldest first).
- Default page size: 50.
- `sourceChunks` JSON is deserialised into `List<SourceChunkReference>` for assistant messages.

**Chat endpoint** (`POST /api/conversations/{conversationId}/messages`):

This is the core endpoint. It accepts a `ChatRequest` body and returns a streamed SSE response.

```java
@PostMapping(
    path = "/{conversationId}/messages",
    produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ChatStreamEvent> chat(
    @CurrentUser User user,
    @PathVariable UUID conversationId,
    @Valid @RequestBody ChatRequest request) {

    return chatService.chat(conversationId, user.getId(), request.content());
}
```

**Why `Flux` return type with `text/event-stream`?** Spring MVC supports reactive return types — when a controller method returns `Flux<T>` with `produces = "text/event-stream"`, Spring automatically serialises each element as an SSE `data:` event. This is cleaner than the `SseEmitter` approach used for processing status because:

1. The chat stream has a clear request-response lifecycle (POST → stream of tokens → done), unlike the status endpoint which is a long-lived subscription.
2. `Flux` composes naturally with Spring AI's streaming API (`chatClient.prompt().stream().content()` returns a `Flux<String>`).
3. Error handling is built into the `Flux` operators (`.onErrorResume`, `.doOnComplete`), whereas `SseEmitter` requires callback registration.

**Why POST with SSE instead of WebSocket?** WebSocket would be the natural choice for bidirectional chat, but this is a request-response pattern: the user sends one message, gets one streamed response. SSE over POST handles this cleanly without the connection management overhead of WebSocket. The `EventSource` browser API only supports GET, but we're using `fetch` with `ReadableStream` on the frontend (see section 4.1), which supports POST.

**Security**: Both conversation creation and the chat endpoint verify ownership through the document relationship. The `@CurrentUser` annotation (already implemented in Phase 1) resolves the authenticated user.

### 3.7 Configuration

#### New Gradle dependencies

**`jargoyle-service/build.gradle.kts`** — add:

```kotlin
// Spring AI embedding support (shared with the existing OpenAI chat starter)
// The spring-ai-starter-model-openai already includes embedding support.
// No additional dependency needed — EmbeddingModel is auto-configured by the same starter.

// Reactive streams support for Flux return types
implementation("io.projectreactor:reactor-core")
```

**`jargoyle-web/build.gradle.kts`** — add:

```kotlin
// Reactive support for Flux in Spring MVC controllers
implementation("io.projectreactor:reactor-core")
```

**`jargoyle-repository/build.gradle.kts`** — no changes needed. The pgvector SQL type is handled in native queries; no Java-side pgvector library dependency is required.

**Note on pgvector JDBC**: The PostgreSQL JDBC driver does not natively understand the `vector` type. When reading `vector` columns, it returns them as `PGobject`. The custom Hibernate `UserType` (mentioned in section 3.1) handles this conversion. For native query parameters, passing the vector as a string with `cast(:param as vector)` avoids the issue entirely.

#### Application properties

Add to `application.yml`:

```yaml
spring:
  ai:
    openai:
      embedding:
        options:
          model: text-embedding-3-small

jargoyle:
  rag:
    chunk:
      target-tokens: 500
      overlap-tokens: 50
      min-tokens: 100
    retrieval:
      top-k: 5
    chat:
      max-history-messages: 10
      max-history-tokens: 2000
      max-response-tokens: 4000
```

These properties are bound to a `ChatProperties` record via `@ConfigurationProperties`:

```java
@ConfigurationProperties(prefix = "jargoyle.rag")
public record ChatProperties(
    ChunkProperties chunk,
    RetrievalProperties retrieval,
    ChatLimits chat
) {
    public record ChunkProperties(int targetTokens, int overlapTokens, int minTokens) {
        public ChunkProperties {
            if (targetTokens <= 0) targetTokens = 500;
            if (overlapTokens <= 0) overlapTokens = 50;
            if (minTokens <= 0) minTokens = 100;
        }
    }

    public record RetrievalProperties(int topK) {
        public RetrievalProperties {
            if (topK <= 0) topK = 5;
        }
    }

    public record ChatLimits(int maxHistoryMessages, int maxHistoryTokens, int maxResponseTokens) {
        public ChatLimits {
            if (maxHistoryMessages <= 0) maxHistoryMessages = 10;
            if (maxHistoryTokens <= 0) maxHistoryTokens = 2000;
            if (maxResponseTokens <= 0) maxResponseTokens = 4000;
        }
    }
}
```

This follows the existing `DocumentProcessingProperties` pattern — `@ConfigurationProperties` record with defensive defaults in the compact constructor.

#### pgvector in Docker Compose

Update `compose.yml` to use the pgvector image:

```yaml
db:
  image: pgvector/pgvector:pg17
  # ... rest of the config unchanged
```

This is a drop-in replacement for the `postgres:17` image — it's the same PostgreSQL with the pgvector extension pre-installed.

### 3.8 Error Handling

Chat-specific error scenarios:

| Scenario | Exception | HTTP status | User-facing message |
|----------|-----------|-------------|---------------------|
| Document not in `READY` status | `DocumentNotReadyException` | 409 Conflict | "This document is still being processed. Chat will be available once processing is complete." |
| Conversation not found / not owned | `ConversationNotFoundException` | 404 Not Found | "Conversation not found." |
| Document has no chunks (edge case) | `DocumentNotReadyException` | 409 Conflict | "This document hasn't been fully processed yet." |
| LLM failure during streaming | Caught in `Flux.onErrorResume` | 200 (SSE stream) | `ChatStreamEvent.error("Something went wrong generating the response. Please try again.")` |
| Empty conversation (no messages) | N/A — not an error | N/A | The first message is simply the first message. No special handling needed. |

**Why 409 Conflict for "document not ready"?** The resource exists but is not in a valid state for the requested operation. This is more specific than 400 (the request itself is well-formed) or 404 (the document exists). 409 signals a temporary condition that will resolve itself when processing completes.

**Error during streaming**: This is the tricky case. Once the SSE stream has started, the HTTP status is already 200. If the LLM fails mid-stream, we cannot change the status code. Instead, we send an `ERROR` event in the stream and complete the stream. The frontend watches for `ERROR` events and displays them appropriately. The partial response (tokens sent before the error) is discarded — the assistant message is not persisted.

New exception classes (in `com.jargoyle.service.exception`):
- `ConversationNotFoundException` — extends `RuntimeException`
- `DocumentNotReadyException` — extends `RuntimeException`

Both are handled by the existing `GlobalExceptionHandler` via `@ExceptionHandler` methods that map them to the appropriate status codes and response bodies.

---

## 4. Frontend Components

### 4.1 API Layer

#### New types — `api/chat.ts`

```typescript
export interface Conversation {
  id: string
  documentId: string
  title: string | null
  messageCount: number
  createdAt: string
  lastMessageAt: string
}

export interface CreateConversationResult {
  id: string
  documentId: string
  suggestedQuestions: SuggestedQuestion[]
}

export interface SuggestedQuestion {
  text: string
  category: string
}

export interface Message {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  sourceChunks: SourceChunkReference[] | null
  createdAt: string
}

export interface SourceChunkReference {
  chunkId: string
  chunkIndex: number
  preview: string
}

export interface ChatStreamEvent {
  type: 'TOKEN' | 'COMPLETE' | 'ERROR'
  content: string | null
  messageId: string | null
  sourceChunks: SourceChunkReference[] | null
}
```

#### Fetch functions — `api/chat.ts`

```typescript
export function createConversation(documentId: string): Promise<CreateConversationResult>

export function fetchConversations(documentId: string): Promise<Conversation[]>

export function fetchMessages(conversationId: string, page?: number): Promise<Page<Message>>

export async function* streamChat(
  conversationId: string,
  content: string
): AsyncGenerator<ChatStreamEvent>
```

**SSE stream handling — `fetch` with `ReadableStream`**:

The `streamChat` function uses `fetch` (not `EventSource`) because the chat endpoint is a POST request and `EventSource` only supports GET. The implementation:

```typescript
export async function* streamChat(
  conversationId: string,
  content: string
): AsyncGenerator<ChatStreamEvent> {
  const response = await fetch(
    `${API_BASE_URL}/conversations/${conversationId}/messages`,
    {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content }),
    }
  )

  if (!response.ok) {
    throw new Error(`Chat error: ${response.status}`)
  }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })

    // SSE events are separated by double newlines.
    // Each event has the format: "data: {json}\n\n"
    const events = buffer.split('\n\n')
    buffer = events.pop()! // Keep the incomplete last part in the buffer

    for (const event of events) {
      const dataLine = event.split('\n').find(line => line.startsWith('data:'))
      if (dataLine) {
        const json = dataLine.slice(5).trim() // Remove "data:" prefix
        if (json) {
          yield JSON.parse(json) as ChatStreamEvent
        }
      }
    }
  }
}
```

**Why `AsyncGenerator` rather than a callback?** An async generator provides a pull-based API that integrates naturally with React's state update model. The consuming hook can `for await...of` over the generator and call `setState` on each event, which React batches efficiently. A callback-based API would work too, but the generator is more composable and testable.

**Why `ReadableStream` + `TextDecoder` rather than a library?** The SSE parsing logic is ~20 lines. Adding a library (`eventsource-parser`, `sse.js`, etc.) for this would be over-engineering. The parsing is straightforward: split on `\n\n`, find lines starting with `data:`, parse the JSON. This approach also avoids the dependency management burden of evaluating library quality and maintenance.

### 4.2 Hooks

#### `useConversations`

```typescript
export function useConversations(documentId: string) {
  const { data, isLoading, refetch } = useQuery({
    queryKey: ['conversations', documentId],
    queryFn: () => fetchConversations(documentId),
  })

  return {
    conversations: data ?? [],
    isLoading,
    refetch,
  }
}
```

#### `useMessages`

```typescript
export function useMessages(conversationId: string) {
  const { data, isLoading, fetchNextPage, hasNextPage } = useInfiniteQuery({
    queryKey: ['messages', conversationId],
    queryFn: ({ pageParam = 0 }) => fetchMessages(conversationId, pageParam),
    getNextPageParam: (lastPage) => lastPage.last ? undefined : lastPage.number + 1,
    initialPageParam: 0,
  })

  return {
    messages: data?.pages.flatMap(p => p.content) ?? [],
    isLoading,
    loadMore: fetchNextPage,
    hasMore: hasNextPage,
  }
}
```

**Why `useInfiniteQuery` for messages?** Message history can be long. `useInfiniteQuery` supports "load older messages" pagination naturally — the user scrolls up and clicks "Load more" to fetch the previous page. The messages are flattened from pages into a single array for rendering.

#### `useChatStream`

The most complex hook — manages the SSE streaming state.

```typescript
export function useChatStream(conversationId: string) {
  const [streamingContent, setStreamingContent] = useState('')
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const sendMessage = useCallback(async (content: string) => {
    setIsStreaming(true)
    setStreamingContent('')
    setError(null)

    // Optimistic update: add the user message to the cache immediately
    queryClient.setQueryData(
      ['messages', conversationId],
      (old: InfiniteData<Page<Message>> | undefined) => {
        // Append the user message to the last page
        // ...optimistic update logic
      }
    )

    try {
      for await (const event of streamChat(conversationId, content)) {
        switch (event.type) {
          case 'TOKEN':
            setStreamingContent(prev => prev + event.content)
            break
          case 'COMPLETE':
            // Invalidate the messages query to refetch with the persisted assistant message
            queryClient.invalidateQueries({ queryKey: ['messages', conversationId] })
            // Also invalidate conversations to update lastMessageAt
            queryClient.invalidateQueries({ queryKey: ['conversations'] })
            setStreamingContent('')
            break
          case 'ERROR':
            setError(event.content ?? 'Something went wrong.')
            break
        }
      }
    } catch (err) {
      setError('Failed to send message. Please try again.')
    } finally {
      setIsStreaming(false)
    }
  }, [conversationId, queryClient])

  return {
    sendMessage,
    streamingContent,
    isStreaming,
    error,
  }
}
```

**Optimistic updates for user messages**: When the user sends a message, it's added to the local React Query cache *immediately* (before the API call returns). This makes the UI feel instant — the user's message appears in the chat list without waiting for the server round-trip. The assistant's response then streams in below it.

**Why `invalidateQueries` on COMPLETE rather than manually adding the assistant message to the cache?** The `COMPLETE` event includes the `messageId` and `sourceChunks`, but the full `MessageResponse` has additional fields (exact timestamps, etc.) that come from the server. Invalidating the query triggers a refetch that replaces the optimistic data with the authoritative server response. This is simpler than trying to construct a complete `MessageResponse` from SSE event data, and the refetch happens in the background while the user reads the response.

### 4.3 Components

#### `ChatInterface`

The main chat component. Contains the message list, streaming display, and input box.

**Props**: `conversationId: string`, `suggestedQuestions: SuggestedQuestion[]`

**Layout**:
```
┌──────────────────────────────┐
│  Message history (scrollable)│
│  ┌────────────────────────┐  │
│  │ USER: "What's the..."  │  │
│  └────────────────────────┘  │
│  ┌────────────────────────┐  │
│  │ ASSISTANT: "Based on..." │ │
│  │ [Source: Section 3, 7]  │ │
│  └────────────────────────┘  │
│  ┌────────────────────────┐  │
│  │ ▊ (streaming cursor)    │ │
│  └────────────────────────┘  │
│                              │
│  ┌──────────────────┐ [Send]│
│  │ Ask a question... │       │
│  └──────────────────┘       │
└──────────────────────────────┘
```

**Behaviour**:
- Auto-scrolls to the bottom when new messages or streaming tokens arrive.
- Input is disabled while streaming (`isStreaming` from `useChatStream`).
- On empty conversation (no messages), shows `SuggestedQuestions` instead of the message list.
- The streaming content is displayed as a "ghost" assistant message below the actual message history, with a pulsing cursor indicator.

#### `MessageBubble`

Renders a single message — user or assistant.

**Props**: `message: Message`, `isStreaming?: boolean`

- **User messages**: right-aligned, accent background colour. No source attribution.
- **Assistant messages**: left-aligned, light background. If `sourceChunks` is present, renders source attribution links below the message text.
- **Streaming state**: when `isStreaming` is true, shows a pulsing cursor at the end of the text and omits source attribution (it arrives in the `COMPLETE` event).

Markdown rendering for assistant messages is a nice-to-have but not essential for Phase 2. Plain text with line breaks is sufficient. If markdown is desired, `react-markdown` is a lightweight library that handles it.

#### `SuggestedQuestions`

Displays clickable starter questions when a conversation has no messages.

**Props**: `questions: SuggestedQuestion[]`, `onSelect: (question: string) => void`

- Renders as a grid of pill-shaped buttons.
- Grouped by `category` if categories are present.
- Clicking a question calls `onSelect(question.text)`, which triggers `sendMessage` in the parent.

#### `SourceChunkDisplay`

Shows source attribution on assistant messages.

**Props**: `sourceChunks: SourceChunkReference[]`

- Renders as a row of small, numbered tags: "Section 1", "Section 4", etc. (using `chunkIndex + 1` for human-friendly numbering).
- Hovering or clicking a tag shows a tooltip or expandable panel with the chunk `preview` text.
- In Phase 3, clicking could scroll to and highlight the source in the original document panel. For Phase 2, the tooltip preview is sufficient.

#### `ConversationSidebar`

Lists conversations for a document, allowing the user to switch between them.

**Props**: `documentId: string`, `activeConversationId: string | null`, `onSelect: (id: string) => void`, `onNewConversation: () => void`

- Shows conversation titles (or "New conversation" for untitled ones) with `lastMessageAt` timestamps.
- "New conversation" button at the top.
- Active conversation is highlighted.
- On mobile, this collapses into a dropdown or bottom sheet.

### 4.4 Pages

#### Updated `DocumentPage` (at `/documents/:id`)

The document page is restructured into a split layout:

```
┌─────────────────────────────────────────────────┐
│ Header (existing Layout component)               │
├──────────────────────┬──────────────────────────┤
│  Summary Panel (40%) │  Chat Panel (60%)         │
│                      │                           │
│  [Title]             │  [Conversation tabs/list] │
│  [Type badge]        │                           │
│  [Plain summary]     │  [ChatInterface]          │
│  [Key facts]         │                           │
│  [Flagged terms]     │                           │
│                      │                           │
├──────────────────────┴──────────────────────────┤
│ Footer (optional)                                │
└─────────────────────────────────────────────────┘
```

**Behaviour**:
- On first visit to a `READY` document, if no conversations exist, automatically create one (via `POST /api/documents/{id}/conversations`). Display suggested questions in the chat panel.
- The summary panel is scrollable independently of the chat panel.
- The chat panel takes the majority of the width because it's the primary interaction point once the user has read the summary.

**Mobile responsive**:
- Below the `md` breakpoint (768px), the split layout becomes a tabbed interface: **Summary** | **Chat**.
- The tabs are rendered at the top of the content area.
- The active tab determines which panel is visible. Defaults to Summary.
- Conversation sidebar becomes a dropdown selector above the chat interface.

#### Routing update

Add to `App.tsx`:

```
/documents/:id → DocumentPage
```

This route was defined in the Phase 1 design but may not have been implemented yet if only the dashboard was built. The `DocumentPage` component handles the `:id` parameter via `useParams()` from React Router.

---

## 5. Testing Approach

### Unit tests

| Test class | What it covers |
|------------|----------------|
| `ChunkingServiceTests` | Section-aware splitting detects headings and clause numbers correctly; token-based fallback splits at sentence boundaries; overlap is applied correctly; very short sections are merged; empty text produces zero chunks; single-sentence text produces one chunk |
| `EmbeddingServiceTests` | `embed()` calls `EmbeddingModel` correctly; `embedBatch()` passes all texts in a single call; `toVectorLiteral()` produces correct format `[0.1, 0.2, ...]`; handles empty input gracefully |
| `ChatServiceTests` | Full RAG flow with mocked dependencies: user message is saved, embedding is generated, chunks are retrieved, prompt is constructed correctly (includes summary, chunks, history, question), stream events are emitted in correct order (TOKEN... → COMPLETE), assistant message is saved on completion with source chunks, error mid-stream emits ERROR event and does not save assistant message |
| `SuggestedQuestionServiceTests` | Returns correct questions for each document type; handles null document type (returns OTHER questions); returns non-empty list for all types |
| `ChatPropertiesTests` | Defaults are applied when properties are zero or negative; positive values are preserved |

**Mocking the LLM**: For `ChatServiceTests`, mock the `ChatClient` to return a `Flux<String>` of canned tokens (e.g. `Flux.just("Based ", "on ", "your ", "document, ", "...")` ). This tests the streaming orchestration and accumulation logic without making real API calls.

**Mocking the EmbeddingModel**: Return a fixed `float[]` of the correct dimension (1536). The actual vector values don't matter for testing the retrieval flow — the repository is also mocked.

### Integration tests

| Test class | What it covers |
|------------|----------------|
| `ChunkingPipelineIntegrationTests` | Full document processing with chunking: upload document → text extracted → chunks created → embeddings generated → summary created → document is READY. Uses Testcontainers with pgvector, mock `ChatModel`, and mock `EmbeddingModel`. Verifies chunk count, chunk ordering, and that embeddings are stored. |
| `ChatIntegrationTests` | Full chat flow: create conversation → send message → receive streamed response → message persisted with source chunks. Uses Testcontainers with pgvector, pre-seeded document with chunks and embeddings, mock `ChatModel`. Verifies conversation and message persistence, source chunk attribution, conversation `lastMessageAt` update. |
| `ConversationOwnershipTests` | User A creates a document and conversation. User B attempts to access the conversation — receives 404. User B attempts to create a conversation on User A's document — receives 404. |

**pgvector in Testcontainers**: Use `PostgreSQLContainer` with the `pgvector/pgvector:pg17` Docker image. This ensures the `vector` extension is available during tests. The Flyway migrations (including V5 which enables the extension) run automatically.

### Repository tests

| Test class | What it covers |
|------------|----------------|
| `DocumentChunkRepositoryTests` | `findTopKSimilar` returns chunks ordered by cosine similarity; only returns chunks for the specified document (not other documents); respects the `topK` limit; excludes chunks with null embeddings; `findByDocumentIdOrderByChunkIndex` returns correct ordering |
| `ConversationRepositoryTests` | `findByIdAndUserId` returns empty for wrong user; `findByDocumentIdOrderByLastMessageAtDesc` returns correct ordering; cascade delete removes conversations when document is deleted |
| `MessageRepositoryTests` | `findRecentByConversationId` returns correct number of messages in descending order; `findByConversationIdOrderByCreatedAtAsc` pagination works correctly; cascade delete removes messages when conversation is deleted |

Use `@DataJpaTest` with Testcontainers (pgvector image). For `DocumentChunkRepositoryTests`, pre-seed chunks with known embedding values (simple synthetic vectors) to verify cosine similarity ordering.

---

## 6. Implementation Order

Each step produces something testable. Don't move to the next step until the current one compiles and passes its tests.

### Step 1 — Database migrations and pgvector setup

Create V5 (enable pgvector extension), V6 (document_chunks), V7 (conversations), V8 (messages). Update `compose.yml` to use the `pgvector/pgvector:pg17` image. Run the application to verify Flyway applies all migrations cleanly. Check the database to confirm tables, constraints, indexes, and the vector type are correct.

**Depends on**: nothing (migrations are standalone)
**Testable by**: `bootRun` succeeds, Hibernate validation passes

### Step 2 — New entities and enum

Create `MessageRole` enum, `DocumentChunk` entity, `Conversation` entity, `Message` entity. Create the custom `VectorType` Hibernate UserType for the `embedding` column. Verify with `bootRun` — Hibernate's `ddl-auto: validate` will confirm entities match the schema.

**Depends on**: Step 1 (tables must exist for validation)
**Testable by**: `bootRun` succeeds without Hibernate validation errors

### Step 3 — Repositories and DTOs

Create `DocumentChunkRepository`, `ConversationRepository`, `MessageRepository`. Create all DTO records: `ChatRequest`, `ChatStreamEvent`, `SourceChunkReference`, `ConversationResponse`, `MessageResponse`, `SuggestedQuestion`, `CreateConversationResponse`. Create new exception classes. Write repository tests with Testcontainers (pgvector image).

**Depends on**: Step 2 (entities must exist)
**Testable by**: All repository tests pass

### Step 4 — ChunkingService

Create `ChunkingService` with section-aware splitting and token-based fallback. Create the internal `TextChunk` record. Create `ChatProperties` configuration record. Write comprehensive unit tests for the chunking heuristics.

**Depends on**: nothing (pure logic, no Spring dependencies)
**Testable by**: All chunking tests pass

### Step 5 — EmbeddingService

Add embedding model configuration to `application.yml`. Create `EmbeddingService`. Write unit tests with a mocked `EmbeddingModel`. Verify the batch embedding and vector literal conversion logic.

**Depends on**: Spring AI OpenAI starter (already present from Phase 1)
**Testable by**: Embedding service tests pass

### Step 6 — Update DocumentProcessingService pipeline

Add `ChunkingService` and `EmbeddingService` as dependencies of `DocumentProcessingService`. Insert the chunking and embedding steps into the async pipeline (between text extraction and summary generation). Update SSE status events. Write integration tests for the extended pipeline.

**Depends on**: Steps 3, 4, 5
**Testable by**: Processing pipeline integration tests pass (document goes from UPLOADING to READY with chunks and embeddings stored)

### Step 7 — SuggestedQuestionService

Create `SuggestedQuestionService` with static question maps. Write unit tests for each document type.

**Depends on**: nothing
**Testable by**: Suggested question tests pass

### Step 8 — ChatService

Create `ChatService` with the full RAG orchestration flow. Implement prompt construction, token budget management, history trimming. Write unit tests with mocked dependencies (ChatClient, EmbeddingService, repositories).

**Depends on**: Steps 3, 5, 7
**Testable by**: Chat service tests pass

### Step 9 — ConversationController

Create `ConversationController` with all four endpoints. Wire up to `ChatService`, `SuggestedQuestionService`, and repositories. Register new exception handlers in `GlobalExceptionHandler`. Write integration tests for the full chat flow.

**Depends on**: Steps 7, 8
**Testable by**: Controller integration tests pass; manual testing via Swagger UI or curl

### Step 10 — Frontend API layer and types

Create `api/chat.ts` with TypeScript types and fetch functions, including the `streamChat` async generator. Test manually in the browser console against the running backend.

**Depends on**: Step 9 (backend endpoints must be running)
**Testable by**: Browser console can create conversations and stream chat responses

### Step 11 — Frontend hooks

Create `useConversations`, `useMessages`, `useChatStream` hooks. Test by wiring them to a minimal test component.

**Depends on**: Step 10
**Testable by**: Hooks fetch data and stream correctly in a test component

### Step 12 — Frontend components and page

Build `ChatInterface`, `MessageBubble`, `SuggestedQuestions`, `SourceChunkDisplay`, `ConversationSidebar`. Update `DocumentPage` to the split layout. Add the `/documents/:id` route. Add mobile responsive behaviour.

**Depends on**: Step 11
**Testable by**: Full manual flow: upload a document → read the summary → ask questions → see streamed responses with source attribution

---

## 7. Verification Checklist

- [ ] All Phase 2 spec items addressed: chunking pipeline, embeddings, chat endpoint, SSE streaming, conversation persistence, chat UI, suggested questions, source attribution
- [ ] No Phase 3+ items included: no image upload, no vision LLM, no "show source" document highlighting, no S3
- [ ] pgvector extension enabled and working in local Docker Compose
- [ ] Document chunks are created during processing and have embeddings
- [ ] Chat responses are streamed via SSE (POST with `Flux` return type)
- [ ] Conversation ownership is enforced through the document relationship
- [ ] Source chunk attribution is recorded at retrieval time (before LLM responds), not inferred from output
- [ ] Token budget management prevents prompt overflow
- [ ] Existing Phase 1 functionality is unchanged: upload, summary generation, document CRUD, processing status SSE
- [ ] Processing pipeline extension is backward-compatible: documents uploaded before Phase 2 still work (they have no chunks; chat returns a "document not ready" error, or chunks could be backfilled)
- [ ] Entity patterns match existing codebase: UUID PKs, `@CreationTimestamp`, explicit getters/setters, no-arg constructors
- [ ] Migration patterns match existing codebase: snake_case, `varchar` with limits, `timestamp with time zone`, cascade deletes
- [ ] DTO patterns match existing codebase: Java records
- [ ] Repository patterns match existing codebase: ownership-scoped queries, `Optional` returns for single entities
- [ ] Frontend patterns match existing codebase: `apiClient` wrapper, React Query hooks, TypeScript interfaces
- [ ] British English used throughout: code, comments, messages, documentation

---

### Critical Files for Implementation
- `src/backend/jargoyle-service/.../service/DocumentProcessingService.java` — core pipeline to extend with chunking and embedding steps
- `src/backend/jargoyle-web/build.gradle.kts` — dependencies to add (reactor-core for Flux support)
- `src/backend/jargoyle-service/.../service/SummaryGenerationService.java` — pattern to follow for ChatService's ChatClient usage
- `src/frontend/src/api/client.ts` — pattern to follow for the SSE streaming fetch function
- `src/backend/jargoyle-model/.../entity/DocumentSummary.java` — entity pattern to follow for new entities (DocumentChunk, Conversation, Message)

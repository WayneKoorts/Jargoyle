# File Upload & Summary Generation — Feature Specification

*Phase 1 implementation detail for the Jargoyle document processing pipeline.*

This document expands on the Phase 1 section of the [main specification](jargoyle-spec.md) with enough detail to implement the file upload pipeline end-to-end. It covers database schema, backend services, frontend pages, and the integration points between them.

**Design philosophy**: Everything here targets a working vertical slice — upload a PDF or paste text, extract the content, generate a plain-English summary, and display it. The architecture is designed so Phase 2 (RAG chunking, embeddings, chat) slots in without reworking what's already built.

---

## 1. Scope

### In scope

- **PDF file upload** via drag-and-drop or file picker
- **Pasted text** as an alternative input method
- **File storage** on the local filesystem (dev profile)
- **PDF text extraction** using Apache PDFBox
- **LLM summary generation** via Spring AI's `ChatClient` (provider-agnostic)
- **Processing status updates** — backend SSE endpoint; frontend polling initially
- **Document CRUD** — list, view, update title/type, delete
- **Frontend pages** — upload page, document view page, updated dashboard with document cards

### Out of scope (deferred to later phases)

- Image upload and vision LLM extraction (Phase 3)
- RAG chunking, embeddings, and `document_chunks` table (Phase 2)
- Chat / conversation endpoints (Phase 2)
- S3 or cloud storage (production hardening)
- Admin endpoints (stretch goal)

---

## 2. Database Migrations

### V2 — Documents table

**File**: `src/backend/src/main/resources/db/migration/V2__create_documents_table.sql`

```sql
create table documents (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    title varchar(255),
    document_type varchar(50),
    original_filename varchar(255),
    input_type varchar(20) not null,
    storage_key varchar(500),
    extracted_text text,
    status varchar(20) not null default 'UPLOADING',
    error_message text,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create index idx_documents_user_id on documents(user_id);
create index idx_documents_user_id_created_at on documents(user_id, created_at desc);
```

**Design notes:**

- **`title` is nullable** — it starts null because the LLM generates the title during summary generation. Users can override it later via the PATCH endpoint.
- **`document_type` is nullable** for the same reason — the LLM classifies the document type during processing. Storing it as a `varchar` rather than a database enum avoids needing a migration every time a new type is added; validation happens in Java.
- **`original_filename` is nullable** because pasted-text documents don't have one.
- **`storage_key`** holds the path to the stored file (relative to the storage root). Null for pasted-text documents where the content lives entirely in `extracted_text`.
- **`extracted_text`** holds the full plain text extracted from the PDF, or the raw pasted text. This column is the source material for summary generation now and for chunking in Phase 2.
- **`status` defaults to `'UPLOADING'`** rather than `'PROCESSING'` — the document record is created first, then the file is stored, then processing begins. This captures the full lifecycle. The possible values are: `UPLOADING`, `PROCESSING`, `READY`, `FAILED`.
- **`error_message`** captures failure details when status is `FAILED`. Nullable — only populated on failure.
- **`updated_at`** tracks the last modification. The entity will manage this with `@UpdateTimestamp`.
- **`on delete cascade`** on `user_id` — if a user is deleted, their documents go too. This is the right default for a consumer app where users own their data.
- **Composite index on `(user_id, created_at desc)`** — the dashboard query is "my documents, newest first". This index serves that query directly.

### V3 — Document summaries table

**File**: `src/backend/src/main/resources/db/migration/V3__create_document_summaries_table.sql`

```sql
create table document_summaries (
    id uuid primary key,
    document_id uuid not null unique references documents(id) on delete cascade,
    plain_summary text not null,
    key_facts jsonb not null default '{}',
    flagged_terms jsonb not null default '[]',
    generated_at timestamp with time zone not null default now()
);
```

**Design notes:**

- **One-to-one with documents** — enforced by the `unique` constraint on `document_id`. A separate table rather than columns on `documents` because: (a) the summary data is large and not always needed (dashboard listing doesn't need it), and (b) it can be regenerated independently without touching the document record.
- **`key_facts` as JSONB** — structured data (amounts, dates, deadlines, parties) that varies by document type. JSONB allows flexible structure without schema changes. Defaults to empty object `'{}'`.
- **`flagged_terms` as JSONB** — array of `{ term, definition }` objects. Defaults to empty array `'[]'`.
- **`on delete cascade`** — summary is deleted when the parent document is deleted.
- **No separate index on `document_id`** — the `unique` constraint creates one automatically.

### What about `document_chunks`?

The `document_chunks` table (embeddings, vector search) is deferred to Phase 2. The schema designed here deliberately keeps extracted text on the `documents` table so that summary generation can work without chunks. When Phase 2 adds chunking, the extracted text gets split and embedded, but the column on `documents` remains as a convenient full-text reference.

---

## 3. Backend Components

### 3.1 Entities

#### `Document`

**Package**: `com.jargoyle.entity`

Follows the same patterns as `User.java` — UUID primary key with `@GeneratedValue(strategy = GenerationType.UUID)`, `@CreationTimestamp` for `createdAt`, explicit getters and setters, no-arg constructor for JPA.

| Field | Type | JPA annotations | Notes |
|-------|------|-----------------|-------|
| `id` | `UUID` | `@Id @GeneratedValue(strategy = UUID)` | |
| `user` | `User` | `@ManyToOne(fetch = LAZY)` `@JoinColumn(name = "user_id")` | Owning side of the relationship |
| `title` | `String` | | Nullable — set during processing |
| `documentType` | `DocumentType` | `@Enumerated(STRING)` | Nullable — classified by LLM |
| `originalFilename` | `String` | | Nullable (no filename for pasted text) |
| `inputType` | `InputType` | `@Enumerated(STRING)` | `PDF` or `TEXT` |
| `storageKey` | `String` | | Nullable (no file for pasted text) |
| `extractedText` | `String` | `@Column(columnDefinition = "text")` | |
| `status` | `DocumentStatus` | `@Enumerated(STRING)` | |
| `errorMessage` | `String` | `@Column(columnDefinition = "text")` | Nullable |
| `createdAt` | `Instant` | `@CreationTimestamp` | |
| `updatedAt` | `Instant` | `@UpdateTimestamp` | |

**Why `@ManyToOne(fetch = LAZY)` on `user`?** Eager fetching would load the full `User` entity every time a document is loaded, even when we only need the document data. Most document operations already know the user ID from the security context — they don't need the `User` entity itself. Lazy loading avoids that unnecessary join.

#### `DocumentSummary`

| Field | Type | JPA annotations | Notes |
|-------|------|-----------------|-------|
| `id` | `UUID` | `@Id @GeneratedValue(strategy = UUID)` | |
| `document` | `Document` | `@OneToOne(fetch = LAZY)` `@JoinColumn(name = "document_id")` | |
| `plainSummary` | `String` | `@Column(columnDefinition = "text")` | |
| `keyFacts` | `String` | `@Column(columnDefinition = "jsonb")` | Raw JSON string — see note below |
| `flaggedTerms` | `String` | `@Column(columnDefinition = "jsonb")` | Raw JSON string |
| `generatedAt` | `Instant` | `@CreationTimestamp` | |

**Why store JSONB as `String` rather than a mapped object?** The JSON structure comes directly from the LLM response and is passed through to the frontend as-is. Mapping it to Java objects would mean maintaining parallel structures in Java and TypeScript that exist only to shuttle data through. Storing it as a raw JSON string and letting the frontend parse it keeps things simple. If we later need to query within the JSON (e.g. "find all documents with a due date"), we can add a Hibernate JSON type or a database view — but for Phase 1, pass-through is the right call.

### 3.2 Enums

#### `DocumentStatus`

```
UPLOADING, PROCESSING, READY, FAILED
```

`UPLOADING` captures the brief window between creating the database record and starting background processing. This matters because the upload itself (writing the file to disk) can fail — and the status should reflect that the document hasn't even begun processing yet.

#### `DocumentType`

```
BILL, INSURANCE, RENTAL, MORTGAGE, BANK_TERMS, CONTRACT, GOVERNMENT, MEDICAL, TAX, OTHER
```

The main spec lists six values. Four more are included here (`CONTRACT`, `GOVERNMENT`, `MEDICAL`, `TAX`) because they're common document types that users will realistically upload, and adding them now avoids needing to think about it later. The LLM will classify into one of these values during summary generation. Since the column is a `varchar` in the database (not a DB-level enum), adding more values in future is a code-only change.

#### `InputType`

```
PDF, IMAGE, TEXT
```

`IMAGE` is included now even though image upload is Phase 3. Since this enum maps to a database `varchar`, the value doesn't need to exist in the database until it's used — but having it in the Java enum now means Phase 3 won't need to modify any existing code that switches on `InputType`.

### 3.3 Repositories

#### `DocumentRepository`

```java
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Document> findByIdAndUserId(UUID id, UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);
}
```

**Why `findByIdAndUserId` rather than `findById` + a separate ownership check?** This pushes the ownership filter into the SQL query itself, which means there's no window where you've loaded someone else's document into memory. It's also impossible to forget the check — if you use this method, ownership is enforced by definition. Every document-level endpoint uses this pattern.

**Why return `Optional`?** To force callers to handle the "not found" case explicitly. The controller returns 404 for both "doesn't exist" and "belongs to someone else" — no information leakage about other users' documents.

#### `DocumentSummaryRepository`

```java
public interface DocumentSummaryRepository extends JpaRepository<DocumentSummary, UUID> {

    Optional<DocumentSummary> findByDocumentId(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
```

### 3.4 DTOs

DTOs are Java `record` types, matching the pattern established by `UserDto`. They define the API contract — what the frontend sees.

#### `DocumentResponse`

Returned by `GET /api/documents/{id}`:

```java
public record DocumentResponse(
    UUID id,
    String title,
    String documentType,       // Enum name as string, or null
    String inputType,          // "PDF" or "TEXT"
    String originalFilename,
    String status,
    String errorMessage,       // Only present when status is FAILED
    DocumentSummaryResponse summary,  // Null while processing
    Instant createdAt
) {}
```

#### `DocumentSummaryResponse`

Nested within `DocumentResponse`:

```java
public record DocumentSummaryResponse(
    String plainSummary,
    String keyFacts,           // Raw JSON string — frontend parses
    String flaggedTerms        // Raw JSON string — frontend parses
) {}
```

**Why pass raw JSON strings for `keyFacts` and `flaggedTerms`?** Same reasoning as the entity — these are LLM-generated JSON structures that the frontend renders directly. Parsing them into Java objects on the backend and then serialising them back to JSON for the API response would be a round-trip that adds complexity without value. The frontend TypeScript types define the structure.

#### `DocumentListResponse`

Returned by `GET /api/documents`:

```java
public record DocumentListResponse(
    UUID id,
    String title,
    String documentType,
    String inputType,
    String status,
    Instant createdAt
) {}
```

A slimmed-down version of `DocumentResponse` — no summary, no extracted text. The dashboard listing only needs metadata.

#### `DocumentUpdateRequest`

Request body for `PATCH /api/documents/{id}`:

```java
public record DocumentUpdateRequest(
    @Size(max = 255) String title,
    String documentType
) {}
```

Both fields are optional (nullable). The controller applies only the non-null fields. `documentType` is validated against the `DocumentType` enum in the service layer.

#### `ProcessingStatusEvent`

Sent over SSE from `GET /api/documents/{id}/status`:

```java
public record ProcessingStatusEvent(
    String status,       // "UPLOADING", "PROCESSING", "READY", "FAILED"
    String step,         // Human-readable: "Extracting text...", "Generating summary..."
    String errorMessage  // Only present on FAILED
) {}
```

The `step` field provides more granularity than the database `status` column — it describes what's happening within the `PROCESSING` state. This powers the friendly progress messages in the UI ("Jargoyle is reading your document...", "Picking out the important bits...").

#### `DocumentSummaryResult`

Internal DTO (not exposed via API) for passing the LLM's structured response between services:

```java
public record DocumentSummaryResult(
    String plainSummary,
    String keyFacts,        // JSON string
    String flaggedTerms,    // JSON string
    String title,           // LLM-generated title
    String documentType     // LLM-classified type
) {}
```

### 3.5 User resolution — extracting the shared lookup

`AuthController.me()` currently contains the OIDC-to-local-user lookup inline:

```java
var provider = authToken.getAuthorizedClientRegistrationId();
var subject = oidcUser.getName();
var localUser = _userRepository.findByOauthProviderAndOauthSubject(provider, subject);
```

`DocumentController` will need the same lookup — every document endpoint needs the current user's `UUID` to scope queries. Rather than duplicating this logic, extract it into a shared helper.

**Option: `AuthenticatedUserResolver`** — a Spring `@Component` that takes an `OidcUser` + `OAuth2AuthenticationToken` and returns the local `User` entity (or throws). Both `AuthController` and `DocumentController` inject this component.

```java
@Component
public class AuthenticatedUserResolver {

    private final UserRepository _userRepository;

    // Constructor injection...

    /**
     * Resolves the OIDC principal to the local User entity.
     * Throws ResponseStatusException(401) if the user doesn't exist locally.
     */
    public User resolve(OidcUser oidcUser, OAuth2AuthenticationToken authToken) {
        // lookup by provider + subject, throw if not found
    }
}
```

**Why a component rather than a utility method?** It needs the `UserRepository`, so it either needs to be injected (component) or passed as a parameter every time (utility). A component is cleaner — callers just inject it alongside their other dependencies.

### 3.6 Controller

#### `DocumentController`

**Base path**: `/api/documents`

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/api/documents` | Upload file or submit text | `202 Accepted` + `DocumentResponse` |
| `GET` | `/api/documents` | List user's documents (paged) | `200 OK` + `Page<DocumentListResponse>` |
| `GET` | `/api/documents/{id}` | Get document with summary | `200 OK` + `DocumentResponse` |
| `PATCH` | `/api/documents/{id}` | Update title or type | `200 OK` + `DocumentResponse` |
| `DELETE` | `/api/documents/{id}` | Delete document and related data | `204 No Content` |
| `GET` | `/api/documents/{id}/status` | SSE processing status stream | `text/event-stream` |

**Upload endpoint detail (`POST /api/documents`)**:

Accepts `multipart/form-data` with two optional parts:
- `file` — a PDF file (max 10 MB)
- `text` — pasted plain text

Exactly one of `file` or `text` must be provided. The controller validates this and returns `400 Bad Request` if both or neither are present.

Response is `202 Accepted` because the document is created but processing hasn't finished. The response body contains the document in `UPLOADING` status. The client then navigates to the document page and polls for status updates.

**Pagination on the list endpoint**: Uses Spring Data's `Pageable` with sensible defaults — page size 20, sorted by `createdAt` descending. The frontend can override via query parameters (`?page=0&size=20`).

### 3.7 Services

#### `DocumentService`

The CRUD orchestration layer. Handles:

- **Upload**: Creates the `Document` entity, delegates file storage to `StorageService`, sets `extractedText` for pasted-text uploads, then hands off to `DocumentProcessingService` for async processing.
- **Get by ID**: Loads document + summary (ownership-scoped), maps to `DocumentResponse`.
- **List**: Paged query via repository, maps to `DocumentListResponse`.
- **Update**: Applies non-null fields from `DocumentUpdateRequest`, validates `documentType` against the enum.
- **Delete**: Deletes the document (cascades to summary), removes the stored file via `StorageService`.

This service does not call the LLM directly — it delegates all processing to `DocumentProcessingService`. This separation means CRUD operations are fast and synchronous, while the slow LLM work happens in the background.

#### `DocumentProcessingService`

The async processing pipeline. Annotated with `@Async("documentProcessingExecutor")` to run on a named thread pool (see Config section). The pipeline:

1. **Update status** → `PROCESSING`
2. **Extract text** (PDF only) → delegates to `TextExtractionService`
3. **Generate summary** → delegates to `SummaryGenerationService`
4. **Apply results** → sets `title`, `documentType`, `extractedText` on the document; creates `DocumentSummary` entity
5. **Update status** → `READY`

Each step emits a `ProcessingStatusEvent` to any connected SSE clients via `SseEmitterRegistry`.

If any step throws, the pipeline catches the exception, sets status to `FAILED` with the error message, and emits a failure event.

**Why `@Async` rather than a message queue?** For a portfolio project running locally, `@Async` with a `TaskExecutor` is the right level of complexity. It demonstrates understanding of async processing without the operational overhead of RabbitMQ or SQS. The spec's "production considerations" section already calls this out — at scale you'd move to a queue, but the service interface wouldn't change.

#### `StorageService` interface

```java
public interface StorageService {

    /**
     * Stores a file and returns the storage key (path relative to the storage root).
     * The implementation decides the directory structure and filename.
     */
    String store(UUID documentId, String originalFilename, InputStream content);

    /**
     * Loads a stored file as a Resource. Throws if not found.
     */
    Resource load(String storageKey);

    /**
     * Deletes a stored file. No-op if the file doesn't exist.
     */
    void delete(String storageKey);
}
```

**Why an interface?** The main spec calls for local filesystem storage in dev and S3 in production. Even though Phase 1 only implements local storage, defining the interface now means the `DocumentService` codes against the abstraction. Swapping in an S3 implementation later is a new class + a Spring profile — zero changes to existing code.

#### `FileSystemStorageService`

The Phase 1 implementation of `StorageService`. Activated via `@Profile("dev")` or as the default (only implementation present).

- **Storage root**: Configurable via `jargoyle.storage.local.root-dir` (defaults to `./data/uploads`)
- **Directory structure**: `{root}/{userId}/{documentId}/{originalFilename}` — organises files by user and document, making cleanup on delete straightforward
- **File naming**: Preserves the original filename for readability. The `documentId` directory provides uniqueness, so filename collisions aren't possible.

#### `TextExtractionService`

Extracts plain text from PDF files using Apache PDFBox.

```java
public String extractText(InputStream pdfContent)
```

Uses `PDDocument.load()` and `PDFTextStripper` — straightforward PDFBox usage. Returns the extracted text as a single string.

**Why PDFBox rather than Spring AI's `PagePdfDocumentReader`?** Spring AI's PDF reader is designed for the RAG pipeline — it splits documents into `Document` objects with metadata, ready for embedding. For Phase 1, we just need the raw text as a string. PDFBox is simpler and more direct for this purpose. Phase 2 may introduce Spring AI's reader for the chunking pipeline, and that's fine — the two can coexist.

#### `SummaryGenerationService`

Calls the LLM to generate a structured summary of the extracted text.

```java
public DocumentSummaryResult generateSummary(String extractedText)
```

Uses Spring AI's `ChatClient` — the high-level abstraction that works with any configured model provider (Anthropic, OpenAI, Ollama, etc.). The service builds a prompt, calls the model, and parses the structured response.

**Prompt design**: The prompt asks the LLM to return a JSON object containing:
- `title` — a short descriptive title for the document
- `documentType` — classification into one of the known types
- `plainSummary` — narrative plain-English explanation
- `keyFacts` — structured amounts, dates, deadlines, parties
- `flaggedTerms` — jargon terms with definitions

Spring AI's structured output support (via `BeanOutputConverter` or equivalent) can handle the JSON parsing, or the service can parse the response manually. Either approach works — the key point is that the service returns a `DocumentSummaryResult` regardless of how the parsing is done internally.

**Provider-agnostic design**: The service depends on `ChatClient.Builder` (injected by Spring AI auto-configuration), not on any provider-specific class. Switching from OpenAI to Anthropic to Ollama is a configuration change (`application.yml`), not a code change.

### 3.8 Configuration

#### `AsyncConfig`

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "documentProcessingExecutor")
    public TaskExecutor documentProcessingExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("doc-processing-");
        return executor;
    }
}
```

**Why a named executor?** Spring's `@Async` uses a default `SimpleAsyncTaskExecutor` that creates a new thread per task with no pooling. A named `ThreadPoolTaskExecutor` gives bounded concurrency (important because each task makes LLM API calls that cost money) and meaningful thread names in logs.

The pool size is small by design — this is a single-user portfolio project. In production, you'd size this based on expected throughput and LLM API rate limits.

### 3.9 SSE — `SseEmitterRegistry`

An in-memory registry that maps document IDs to active `SseEmitter` instances.

```java
@Component
public class SseEmitterRegistry {

    void register(UUID documentId, SseEmitter emitter);

    void send(UUID documentId, ProcessingStatusEvent event);

    void complete(UUID documentId);
}
```

Internally, uses a `ConcurrentHashMap<UUID, List<SseEmitter>>` — multiple clients can watch the same document. Emitters are removed on completion, timeout, or error (via `SseEmitter.onCompletion` / `onTimeout` / `onError` callbacks).

**Why build this if the frontend uses polling?** Two reasons: (a) the SSE endpoint is part of the API spec and demonstrates the pattern, even if the initial frontend uses polling; (b) if you later want to switch the frontend to SSE (or build a mobile client that prefers it), the backend is already ready.

### 3.10 Validation

Validation happens at the controller level, before any processing begins:

| Check | Rule | Error |
|-------|------|-------|
| Mutual exclusivity | Exactly one of `file` or `text` must be provided | 400 — "Provide either a file or text, not both" |
| File size | Maximum 10 MB | 400 — "File exceeds the 10 MB limit" |
| Content type | Must be `application/pdf` | 400 — "Only PDF files are supported" |
| PDF magic bytes | First bytes must be `%PDF` | 400 — "File does not appear to be a valid PDF" |
| Text length | Pasted text must not be empty or exceed 100,000 characters | 400 — "Text must be between 1 and 100,000 characters" |

**Why check magic bytes rather than trusting the content type?** The `Content-Type` header on a multipart upload is set by the browser based on the file extension, which can be wrong or spoofed. Checking the first four bytes of the file (`%PDF`) is a simple, reliable way to verify it's actually a PDF. This isn't about security paranoia — it's about giving users a clear error message when they accidentally upload the wrong file type.

### 3.11 Error handling

#### `GlobalExceptionHandler`

A `@RestControllerAdvice` class that translates exceptions into consistent JSON error responses. This centralises error formatting so controllers don't need to catch and wrap exceptions individually.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ResponseStatusException → use its status and reason
    // MethodArgumentNotValidException → 400 with field-level errors
    // MaxUploadSizeExceededException → 400 with friendly message
    // Generic Exception → 500 with safe message (no stack traces)
}
```

Response shape (consistent across all error types):

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "File exceeds the 10 MB limit"
}
```

### 3.12 Security

All document endpoints are behind Spring Security's authenticated filter (already configured in `SecurityConfig.java`). Ownership is enforced at the data layer:

- Every query is scoped to the current user's ID via `findByIdAndUserId` / `findByUserIdOrderByCreatedAtDesc`.
- When a document isn't found (either it doesn't exist or it belongs to another user), the response is `404 Not Found` — never `403 Forbidden`. Returning 403 would confirm that the document exists, which leaks information.
- The SSE status endpoint also requires authentication and ownership verification before creating an emitter.

No new security configuration is needed — the existing `SecurityConfig` already secures `/api/**` paths.

---

## 4. Frontend Components

### 4.1 New dependency

**`react-dropzone`** — handles drag-and-drop file uploads with accessibility support, file type filtering, and size validation. It's the library recommended in the main spec's tech stack section.

### 4.2 API layer

#### Types — `api/documents.ts`

TypeScript interfaces matching the backend DTOs:

```typescript
export interface DocumentSummary {
  plainSummary: string
  keyFacts: KeyFacts
  flaggedTerms: FlaggedTerm[]
}

export interface KeyFacts {
  amounts?: KeyFact[]
  dates?: KeyFact[]
  parties?: KeyFact[]
}

export interface KeyFact {
  label: string
  value: string
  context: string
}

export interface FlaggedTerm {
  term: string
  definition: string
}

export interface DocumentDetail {
  id: string
  title: string | null
  documentType: string | null
  inputType: string
  originalFilename: string | null
  status: string
  errorMessage: string | null
  summary: DocumentSummary | null
  createdAt: string
}

export interface DocumentListItem {
  id: string
  title: string | null
  documentType: string | null
  inputType: string
  status: string
  createdAt: string
}
```

**Note on `keyFacts` and `flaggedTerms`**: The backend sends these as raw JSON strings. The frontend parses them into the typed structures above. The `DocumentDetail` type's `summary` field uses the parsed `DocumentSummary` type, so by the time components render, the data is fully typed.

#### Fetch functions — `api/documents.ts`

```typescript
export function fetchDocuments(page = 0): Promise<PageResponse<DocumentListItem>>
export function fetchDocument(id: string): Promise<DocumentDetail>
export function uploadFile(file: File): Promise<DocumentDetail>
export function uploadText(text: string): Promise<DocumentDetail>
export function updateDocument(id: string, data: DocumentUpdateRequest): Promise<DocumentDetail>
export function deleteDocument(id: string): Promise<void>
```

**Multipart upload bypasses `apiClient`**: The existing `apiClient` sets `Content-Type: application/json` and stringifies the body. Multipart file uploads need `Content-Type: multipart/form-data` (set automatically by the browser when using `FormData`) and must *not* stringify the body. The `uploadFile` function uses `fetch` directly with a `FormData` body rather than going through `apiClient`.

`uploadText` can use `apiClient` — it sends `multipart/form-data` as well (with a `text` field instead of a `file` field), so it also uses `fetch` directly for consistency.

### 4.3 Pages

#### `UploadPage`

Two-tab layout — **File Upload** and **Paste Text**.

**File Upload tab**:
- `react-dropzone` drop zone with visual feedback (border highlight on drag-over)
- Accepts `.pdf` only, 10 MB limit (enforced client-side with a clear error message)
- Shows the selected file name and size before upload
- Upload button triggers the `POST /api/documents` call
- On success, navigates to `/documents/{id}`

**Paste Text tab**:
- Large `<textarea>` for pasting document content
- Character count with 100,000 character limit indicator
- Submit button triggers the `POST /api/documents` call with the text body
- On success, navigates to `/documents/{id}`

**Shared behaviour**: Both tabs show a loading state during upload and an error state if the request fails. The page is accessible from the dashboard header and via the `/upload` route.

#### `DocumentPage`

Displays a single document at `/documents/{id}`. The content adapts based on the document's `status`:

**While `UPLOADING` or `PROCESSING`**:
- Friendly progress messages ("Jargoyle is reading your document...", "Picking out the important bits...", "Translating the jargon...")
- Animated indicator (spinner or pulsing dots — Tailwind animation)
- The page polls via React Query (`refetchInterval`) until status is `READY` or `FAILED`

**When `READY`**:
- **Title** — displayed at the top, editable inline (PATCH on blur/enter)
- **Document type badge** — e.g. "Bill", "Insurance", colour-coded
- **Plain summary** — the main narrative explanation
- **Key facts** — amounts, dates, deadlines displayed in a card grid
- **Flagged terms** — expandable list of jargon with definitions (Jargoyle Decoder section from the spec)

**When `FAILED`**:
- Error message displayed with a "Try again" option (re-upload)

**Polling strategy**: React Query's `refetchInterval` option, set to 2 seconds while the document is in a non-terminal state (`UPLOADING` or `PROCESSING`). Once the status reaches `READY` or `FAILED`, polling stops (`refetchInterval: false`). This is simpler and more reliable than SSE for the initial implementation — SSE with session cookies requires additional configuration (the browser's `EventSource` API doesn't send cookies by default in all contexts), and polling every 2 seconds is perfectly fine for a portfolio project.

#### Updated `DashboardPage`

The current `DashboardPage` shows a placeholder message. Replace it with a document list:

- **Header** with Jargoyle branding, user name, "Upload" button, and sign-out button
- **Document cards** — one per document, showing: title (or "Untitled document"), document type badge, input type icon (PDF / text), status indicator, relative timestamp ("2 hours ago")
- **Empty state** — friendly message with a prominent upload CTA when no documents exist ("No documents yet. Upload your first document to get started.")
- **Pagination** — "Load more" button or infinite scroll if there are more than 20 documents

Each card links to `/documents/{id}`.

### 4.4 Shared layout

The `DashboardPage` currently contains its own header. The `UploadPage` and `DocumentPage` will need the same header. Extract it into a **`Layout`** component:

```
<Layout>
  <header>  ← Jargoyle branding, navigation, user info, sign out
  <main>    ← Page content (children)
</Layout>
```

`Layout` takes the `user` and `onLogout` props (or reads them from a context — either approach works). All authenticated pages wrap their content in `<Layout>`.

### 4.5 Routing

Update `App.tsx` to add the new routes:

```
/              → DashboardPage
/upload        → UploadPage
/documents/:id → DocumentPage
*              → Navigate to /
```

All routes remain behind the existing auth gate (`isAuthenticated` check in `AppRoutes`).

---

## 5. Configuration Changes

### 5.1 Backend dependencies

Add to `build.gradle.kts`:

```kotlin
// Spring AI — BOM for version management
implementation(platform("org.springframework.ai:spring-ai-bom:1.0.0"))

// Spring AI — model starter (pick one based on your provider)
// For Anthropic: implementation("org.springframework.ai:spring-ai-anthropic-spring-boot-starter")
// For OpenAI:    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")
// For Ollama:    implementation("org.springframework.ai:spring-ai-ollama-spring-boot-starter")

// PDF text extraction
implementation("org.apache.pdfbox:pdfbox:3.0.4")
```

**Which model starter?** This design is deliberately provider-agnostic — the service layer depends on `ChatClient`, not on any provider. Pick whichever provider you have API keys for. Ollama is a good choice for local development (free, no API key needed, runs locally). The Spring AI BOM manages version alignment across all Spring AI dependencies.

**Note on the BOM version**: The version above is illustrative. Check [Spring AI's releases](https://docs.spring.io/spring-ai/reference/) for the latest version compatible with Spring Boot 4.0.x.

### 5.2 Application properties

Add to `application-dev.yml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

  ai:
    # Provider-specific configuration goes here.
    # Example for Anthropic:
    #   anthropic:
    #     api-key: ${ANTHROPIC_API_KEY}
    #     chat:
    #       options:
    #         model: claude-sonnet-4-20250514
    #
    # Example for OpenAI:
    #   openai:
    #     api-key: ${OPENAI_API_KEY}
    #     chat:
    #       options:
    #         model: gpt-4o-mini
    #
    # Example for Ollama (no API key needed):
    #   ollama:
    #     chat:
    #       options:
    #         model: llama3

jargoyle:
  storage:
    local:
      root-dir: ./data/uploads
```

**Multipart limits**: Both `max-file-size` and `max-request-size` are set to 10 MB. `max-file-size` limits individual files; `max-request-size` limits the entire multipart request. Setting them equal is correct here because we only accept one file per request.

### 5.3 Frontend proxy

Add SSE endpoint proxy to `vite.config.ts`:

No changes needed — the existing `/api` proxy already covers `/api/documents/{id}/status`.

### 5.4 `.gitignore`

Add to the root `.gitignore`:

```
data/
```

This excludes the local file storage directory from version control. Uploaded files should never be committed.

---

## 6. Testing Approach

### Unit tests

| Test class | What it covers |
|------------|----------------|
| `DocumentServiceTests` | CRUD operations — upload creates entity correctly, delete removes stored file, update applies partial fields, ownership scoping |
| `DocumentProcessingServiceTests` | Pipeline steps execute in order, status transitions happen correctly, failure in any step sets FAILED status with error message |
| `TextExtractionServiceTests` | Extracts text from a real test PDF (include a small PDF in test resources), handles empty PDFs, handles encrypted PDFs gracefully |
| `SummaryGenerationServiceTests` | Prompt is constructed correctly, LLM response is parsed into `DocumentSummaryResult`, handles malformed LLM responses |
| `FileSystemStorageServiceTests` | Store creates expected directory structure, load returns correct file, delete removes file, handles non-existent files |
| `SseEmitterRegistryTests` | Register/send/complete lifecycle, multiple emitters per document, emitter cleanup on timeout |

**Mocking the LLM**: For `SummaryGenerationServiceTests`, mock the `ChatClient` to return a canned response. This tests prompt construction and response parsing without making real API calls (which are slow, cost money, and produce non-deterministic output).

### Integration tests

| Test class | What it covers |
|------------|----------------|
| `DocumentUploadIntegrationTests` | Full upload-to-ready flow: POST file → document created → processing runs → summary generated → GET returns complete document. Uses Testcontainers for PostgreSQL and a mock `ChatModel` bean that returns a canned summary. |
| `DocumentCrudIntegrationTests` | List, update, delete operations against a real database. Verifies ownership scoping — user A cannot access user B's documents. |

**Mock `ChatModel`**: Define a `@TestConfiguration` bean that replaces the real `ChatModel` with one that returns a fixed JSON response. This lets integration tests run the full pipeline (including the async processing) without needing an LLM API key.

### Repository tests

| Test class | What it covers |
|------------|----------------|
| `DocumentRepositoryTests` | `findByIdAndUserId` returns empty for wrong user, `findByUserIdOrderByCreatedAtDesc` returns correct order, `deleteByIdAndUserId` only deletes owned documents |
| `DocumentSummaryRepositoryTests` | `findByDocumentId` returns the summary, cascade delete works when parent document is deleted |

Use `@DataJpaTest` with Testcontainers. These tests verify that the custom query methods behave correctly — they're cheap to write and catch subtle bugs in query derivation.

---

## 7. Implementation Order

Each step produces something testable. Don't move to the next step until the current one compiles and passes its tests.

### Step 1 — Database migrations

Create `V2__create_documents_table.sql` and `V3__create_document_summaries_table.sql`. Run the application to verify Flyway applies them cleanly. Check the database to confirm tables, constraints, and indexes are correct.

### Step 2 — Enums and entities

Create `DocumentStatus`, `DocumentType`, `InputType` enums. Create `Document` and `DocumentSummary` entities following `User.java` patterns. Verify with a quick `bootRun` — Hibernate validation mode (`ddl-auto: validate`) will confirm the entities match the schema.

### Step 3 — Repositories and DTOs

Create `DocumentRepository`, `DocumentSummaryRepository`, and all DTO records. Write repository tests with Testcontainers to verify custom queries.

### Step 4 — User resolution helper

Extract `AuthenticatedUserResolver` from the lookup logic in `AuthController`. Refactor `AuthController` to use it. Verify existing auth tests still pass.

### Step 5 — Storage service

Create `StorageService` interface and `FileSystemStorageService`. Write unit tests for store/load/delete. Add `data/` to `.gitignore` and `jargoyle.storage.local.root-dir` to `application-dev.yml`.

### Step 6 — Text extraction service

Add PDFBox dependency. Create `TextExtractionService`. Write unit tests with a small test PDF.

### Step 7 — Summary generation service

Add Spring AI dependencies. Create `SummaryGenerationService` with `ChatClient`. Write unit tests with a mocked `ChatClient`. Configure AI provider properties in `application-dev.yml`.

### Step 8 — Processing pipeline and controller

Create `AsyncConfig`, `SseEmitterRegistry`, `DocumentProcessingService`, `DocumentService`, `DocumentController`, and `GlobalExceptionHandler`. Write integration tests for the full upload-to-ready flow using Testcontainers and a mock `ChatModel`.

### Step 9 — Frontend API layer and routing

Install `react-dropzone`. Create `api/documents.ts` with types and fetch functions. Create the `Layout` component. Update `App.tsx` with new routes.

### Step 10 — Frontend pages

Build `UploadPage`, `DocumentPage`, and update `DashboardPage`. Connect to the API layer. Test the full flow manually: upload a PDF → see processing status → view the summary.

---

## Verification Checklist

- [ ] All Phase 1 spec items addressed: PDF upload, pasted text, text extraction, summary generation, document CRUD, status updates, frontend pages
- [ ] No Phase 2+ items included: no chunking, no embeddings, no chat, no image upload, no S3
- [ ] Database schema is forward-compatible: `document_chunks` can be added in Phase 2 without altering existing tables
- [ ] LLM integration is provider-agnostic: all service code depends on `ChatClient`, not on provider-specific classes
- [ ] Storage is abstraction-backed: `StorageService` interface allows swapping implementations via profiles
- [ ] Ownership is enforced at the data layer: every query is scoped by `userId`
- [ ] Frontend uses polling (simpler) with SSE backend ready for future use
- [ ] Entity patterns match existing codebase: UUID PKs, `@CreationTimestamp`, explicit getters/setters
- [ ] Migration patterns match existing codebase: snake_case, `varchar` with limits, `timestamp with time zone`, defaults
- [ ] DTO patterns match existing codebase: Java records

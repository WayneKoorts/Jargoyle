# Jargoyle — Project Spec

*Guarding you from the fine print.*

A consumer-focused tool that explains everyday documents in plain English. Users upload a phone bill, insurance policy, rental agreement, bank terms, or mortgage document and get a clear, jargon-free explanation — then ask follow-up questions in a chat interface.

**Name**: Jargoyle — jargon + gargoyle. A gargoyle perched on your documents, silently decoding them for you.

**Positioning**: Regular people, not legal professionals. Existing tools (Spellbook, Kira, LegalOn, Juro) are all priced and designed for corporate legal teams. This fills a genuine gap.

**Portfolio angle**: Demonstrates Spring Boot, Spring AI with RAG, relational data modelling, OAuth2, file handling, and a polished React SPA — all in a project that's immediately understandable to any interviewer.

---

## Core User Flow

1. **Upload** — User drops a file (PDF, image, or pasted text). Supported: PDF, PNG, JPG, HEIC, plain text.
2. **Processing** — Document is analysed. For images and scanned PDFs, a vision-capable LLM extracts structured text. For text-based PDFs, standard extraction is used.
3. **Summary** — User receives a plain-English summary: what the document is, the key takeaways, any amounts/dates/deadlines that matter, and flagged jargon with definitions.
4. **Chat** — User asks follow-up questions in a conversational interface. The AI grounds answers in the document content, not general knowledge.
5. **History** — Users can return to any previously uploaded document and continue the conversation.

---

## Data Model

### Entity Relationship

```
User (1) ──── (N) Document (1) ──── (N) Conversation
                     │                       │
                     │                       └── (N) Message
                     │
                     ├── (N) DocumentChunk
                     │
                     └── (1) DocumentSummary
```

### Tables

#### `users`
| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `email` | VARCHAR(255) | Unique, from OAuth provider |
| `display_name` | VARCHAR(255) | From OAuth profile |
| `oauth_provider` | VARCHAR(50) | e.g. `google`, `github` |
| `oauth_subject` | VARCHAR(255) | Provider's unique user ID |
| `created_at` | TIMESTAMP | |
| `last_login_at` | TIMESTAMP | |

#### `documents`
| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `user_id` | UUID | FK → users |
| `title` | VARCHAR(255) | Auto-generated or user-provided |
| `document_type` | VARCHAR(50) | `BILL`, `INSURANCE`, `RENTAL`, `MORTGAGE`, `BANK_TERMS`, `OTHER` |
| `original_filename` | VARCHAR(255) | |
| `input_type` | VARCHAR(20) | `PDF`, `IMAGE`, `TEXT` |
| `storage_key` | VARCHAR(500) | S3/local path to original file |
| `extracted_text` | TEXT | Full extracted text (plain text or OCR output) |
| `status` | VARCHAR(20) | `PROCESSING`, `READY`, `FAILED` |
| `created_at` | TIMESTAMP | |

The `document_type` is inferred by the LLM during processing but can be corrected by the user. It drives the system prompt context — a phone bill gets different treatment than an insurance policy.

#### `document_chunks`
| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `document_id` | UUID | FK → documents |
| `chunk_index` | INTEGER | Ordering within document |
| `content` | TEXT | The text chunk |
| `embedding` | VECTOR(1536) | For semantic search (pgvector) |
| `token_count` | INTEGER | Useful for context window management |

Chunks are the unit of RAG retrieval. Chunking strategy is section-aware where possible (split on headings, clause numbers) with fallback to token-based splitting with overlap.

#### `document_summaries`
| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `document_id` | UUID | FK → documents, unique |
| `plain_summary` | TEXT | The "here's what this document says" narrative |
| `key_facts` | JSONB | Structured: amounts, dates, deadlines, parties |
| `flagged_terms` | JSONB | Jargon terms with plain-English definitions |
| `generated_at` | TIMESTAMP | |

`key_facts` example:
```json
{
  "amounts": [
    { "label": "Total due", "value": "$142.50", "context": "Monthly bill total" },
    { "label": "Late fee", "value": "$15.00", "context": "Applied if not paid by due date" }
  ],
  "dates": [
    { "label": "Due date", "value": "2025-03-15", "context": "Payment deadline" },
    { "label": "Service period", "value": "2025-02-01 to 2025-02-28", "context": "What this bill covers" }
  ]
}
```

`flagged_terms` example:
```json
[
  { "term": "pro rata", "definition": "A proportional charge based on the number of days you actually used the service." },
  { "term": "excess", "definition": "The amount you pay out of pocket before your insurance covers the rest." }
]
```

#### `conversations`
| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `document_id` | UUID | FK → documents |
| `created_at` | TIMESTAMP | |
| `last_message_at` | TIMESTAMP | For sorting in UI |

A document can have multiple conversations — lets users start fresh threads on the same document. In practice most will have one, but the model supports it cleanly.

#### `messages`
| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `conversation_id` | UUID | FK → conversations |
| `role` | VARCHAR(20) | `USER` or `ASSISTANT` |
| `content` | TEXT | Message text |
| `source_chunks` | JSONB | Array of chunk IDs used for grounding (assistant messages only) |
| `created_at` | TIMESTAMP | |

`source_chunks` enables a "show me where it says that" feature — the UI can highlight which parts of the original document the answer was derived from.

### Database Choice

**PostgreSQL with pgvector extension.** Keeps everything in one database — relational data and vector embeddings together. For a portfolio project this is ideal: no separate vector store to manage, and it demonstrates that you understand the tradeoffs (pgvector is fine at portfolio scale; you'd evaluate dedicated vector DBs like Pinecone or Weaviate at production scale).

### Spring Data JPA Notes

- Standard `JpaRepository` interfaces for all entities
- Custom query methods for: documents by user (paged, sorted by recency), chunks by document ordered by index, messages by conversation ordered by timestamp
- `@EntityGraph` on document fetch to avoid N+1 on summary and conversations
- Flyway for migrations

---

## API Design

### Auth

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/oauth2/authorization/{provider}` | Initiate OAuth flow (Spring Security handles) |
| `GET` | `/api/auth/me` | Current user profile |
| `POST` | `/api/auth/logout` | End session |

### Documents

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/documents` | Upload a document (multipart) or submit text |
| `GET` | `/api/documents` | List user's documents (paged) |
| `GET` | `/api/documents/{id}` | Get document with summary |
| `DELETE` | `/api/documents/{id}` | Delete document and all related data |
| `PATCH` | `/api/documents/{id}` | Update title or document type |

**POST `/api/documents`** — Accepts `multipart/form-data`:
- `file` (optional): PDF or image file
- `text` (optional): Pasted text content
- One of `file` or `text` must be provided

Returns `202 Accepted` with the document in `PROCESSING` status. The client polls or connects via SSE for completion.

**GET `/api/documents/{id}`** response shape:
```json
{
  "id": "...",
  "title": "Spark NZ — February 2025 Bill",
  "documentType": "BILL",
  "inputType": "IMAGE",
  "status": "READY",
  "summary": {
    "plainSummary": "This is your monthly phone bill from Spark. Jargoyle spotted a few things worth knowing...",
    "keyFacts": { ... },
    "flaggedTerms": [ ... ]
  },
  "conversations": [
    { "id": "...", "lastMessageAt": "...", "messageCount": 5 }
  ],
  "createdAt": "..."
}
```

### Conversations & Chat

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/documents/{id}/conversations` | Start new conversation on a document |
| `GET` | `/api/conversations/{id}/messages` | Get message history (paged) |
| `POST` | `/api/conversations/{id}/messages` | Send a message, get AI response |

**POST `/api/conversations/{id}/messages`** — This is the core chat endpoint.

Request:
```json
{ "content": "What happens if I pay this bill late?" }
```

Response (streamed via SSE for real-time feel):
```json
{
  "id": "...",
  "role": "ASSISTANT",
  "content": "If you pay after March 15, there's a $15 late fee...",
  "sourceChunks": ["chunk-id-1", "chunk-id-3"],
  "createdAt": "..."
}
```

### Processing Status (SSE)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/documents/{id}/status` | SSE stream for processing progress |

Events: `EXTRACTING_TEXT`, `CHUNKING`, `GENERATING_SUMMARY`, `READY`, `FAILED`.

### Admin (stretch goal)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/admin/stats` | Aggregate usage stats |
| `GET` | `/api/admin/users` | User list (paged) |

---

## Spring AI / RAG Pipeline

### Document Processing Pipeline

```
Upload
  │
  ▼
┌─────────────────────────┐
│   Input Detection        │  Determine: PDF (text-based), PDF (scanned), Image, Text
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│   Text Extraction        │
│                          │
│   Text PDF → Apache PDFBox (or Spring AI's PDF reader)
│   Scanned PDF / Image → Vision LLM (Claude / GPT-4o)
│   Plain text → passthrough
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│   Chunking               │
│                          │
│   1. Attempt section-aware split (headings, clause numbers, table boundaries)
│   2. Fallback: token-based split (~500 tokens, 50 token overlap)
│   3. Generate embeddings for each chunk
│   4. Store chunks + embeddings in document_chunks
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│   Summary Generation     │
│                          │
│   Single LLM call with full extracted text (or chunked if too long):
│   - Plain-English summary
│   - Key facts extraction (amounts, dates, deadlines, parties)
│   - Jargon flagging with definitions
│   - Document type classification
│   Store in document_summaries
└─────────────────────────┘
```

### Vision LLM for Document Extraction

This is a key differentiator. Real-world documents are messy — phone bills have multi-column layouts, tables, fine print, logos overlapping text. Traditional OCR (Tesseract) struggles with these layouts.

**Approach**: Send the image/scanned PDF pages directly to a vision-capable LLM (Claude's vision or GPT-4o) with a structured extraction prompt:

```
You are a document text extractor. Extract ALL text from this document image,
preserving the logical structure. For tables, use markdown table format.
For multi-column layouts, process left-to-right, top-to-bottom.
Include all amounts, dates, fine print, and footnotes.
Do not summarise or interpret — extract verbatim.
```

**Why this is better than server-side OCR for this use case:**
- Understands layout semantics (knows a table is a table, not random text)
- Handles mixed content (logos, watermarks, stamps) gracefully
- Multi-column awareness without pre-processing
- No Tesseract/OpenCV dependency chain to manage

**Cost consideration**: Vision calls are more expensive than text extraction. For text-based PDFs, detect this up front (check if PDF has extractable text layer) and skip the vision path entirely.

### Chat RAG Flow

```
User question
  │
  ▼
┌─────────────────────────┐
│   Embed question          │  Generate embedding for the user's question
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│   Retrieve chunks         │  Cosine similarity search against this document's chunks
│                          │  Top-K (k=5) most relevant chunks
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│   Build prompt            │
│                          │
│   System: You are Jargoyle, a document explainer helping regular people
│           understand their {documentType}. Use plain English.
│           Only answer based on the provided document content.
│           If the answer isn't in the document, say so.
│
│   Context: [retrieved chunks]
│   Summary: [document summary for broader context]
│   History: [last N messages from this conversation]
│
│   User: {question}
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│   LLM call (streamed)     │  Response streamed to client via SSE
│                          │  Source chunk IDs recorded for attribution
└─────────────────────────┘
```

### Spring AI Integration Points

- **`EmbeddingModel`** — For generating embeddings (document chunks and queries). Use OpenAI's `text-embedding-3-small` or Anthropic's embeddings when available.
- **`ChatModel`** — For summary generation and chat. Claude (via Spring AI's Anthropic integration) or OpenAI.
- **`VectorStore`** — Spring AI's `PgVectorStore` backed by PostgreSQL + pgvector.
- **`DocumentReader`** — Spring AI's PDF document reader for text-based PDFs.
- **`TokenTextSplitter`** or custom `TextSplitter` — For chunking. Custom implementation preferred for section-aware splitting.

### Prompt Engineering Notes

The summary generation prompt is important to get right. It should:
- Produce genuinely plain language (not "legalese lite")
- Extract structured data reliably (amounts, dates) — use a two-pass approach if needed: one call for the narrative summary, one with a JSON schema for structured extraction
- Flag jargon contextually (the word "excess" means different things in insurance vs telecommunications)
- Include appropriate caveats ("Jargoyle gives you a plain-English interpretation, not legal advice")

---

## UI/UX Flow

### Tech Stack

- **React 18** with TypeScript
- **React Router** for navigation
- **Tailwind CSS** for styling
- **React Query (TanStack Query)** for server state
- **React Dropzone** for file uploads

### Screens

#### 1. Landing / Login
Clean, consumer-friendly landing. Hero section with Jargoyle branding — a friendly gargoyle mascot perched on a stack of documents. Tagline: "Confused by your phone bill? Jargoyle explains it in plain English." Single OAuth login button (Google to start; GitHub for the developer audience reviewing your portfolio).

#### 2. Dashboard
List of previously uploaded documents, sorted by recency. Each card shows: title, document type badge, date uploaded, number of conversations. Empty state with prominent upload CTA.

#### 3. Upload
**Drag-and-drop zone** — accepts PDF, images (PNG, JPG, HEIC), or a text paste area toggle. File size limit displayed (10MB). On upload, transitions to a processing state with progress indicators (extracting text → analysing → generating summary).

#### 4. Document View (the main event)
Split layout:

**Left panel — Document Summary**
- Plain-English summary at the top
- **Key Facts** section: amounts, dates, deadlines in a clean card layout. Dates that are soon or past get visual emphasis.
- **Jargoyle Decoder** section: flagged terms with expandable definitions. Think tooltip-style but persistent — users can scan the list.
- **Original Document** toggle: view the uploaded file or extracted text. If the AI cited specific chunks, highlight them.

**Right panel — Chat**
- Conversational interface for follow-up questions
- Suggested starter questions based on document type:
  - Bill: "Am I being overcharged for anything?", "What's the biggest line item?", "What happens if I pay late?"
  - Insurance: "What's actually covered?", "What are the exclusions?", "How do I make a claim?"
  - Rental agreement: "What are my obligations?", "Can the landlord raise the rent?", "What's the notice period?"
- Messages stream in (SSE) for a responsive feel
- "Show source" links on AI responses — clicking scrolls/highlights the relevant section in the left panel

#### 5. Mobile Considerations
On mobile, the split layout becomes tabbed: Summary | Chat | Original. The upload flow should support camera capture directly (for photographing paper bills) — this is just an `<input type="file" accept="image/*" capture="environment">`.

### UX Details That Matter for Portfolio

- **Processing feedback**: Don't just show a spinner. Show named stages with Jargoyle personality: "Jargoyle is reading your document...", "Picking out the important bits...", "Translating the jargon...". Makes the AI pipeline tangible and reinforces the brand.
- **Error states**: Graceful handling of unreadable documents, unsupported formats, processing failures. This is the kind of thing interviewers notice.
- **Empty states**: Every list and section should have a thoughtful empty state, not just blank space.
- **Responsive**: Works on phone. The camera-upload flow for paper documents is a compelling demo moment.

---

## Architecture Overview

```
┌──────────────────┐     ┌──────────────────────────────────────────┐
│   React SPA       │────▶│   Spring Boot API                        │
│   (Vite + TS)     │◀────│                                          │
│                    │ SSE │   ┌─────────────┐  ┌─────────────────┐  │
└──────────────────┘     │   │ Controllers  │  │ Spring Security  │  │
                          │   │ (REST + SSE) │  │ (OAuth2 Client)  │  │
                          │   └──────┬──────┘  └─────────────────┘  │
                          │          │                               │
                          │   ┌──────▼──────┐                       │
                          │   │  Services    │                       │
                          │   │              │                       │
                          │   │  DocumentService ──▶ Processing pipeline
                          │   │  ChatService ──────▶ RAG pipeline   │
                          │   │  UserService       │                │
                          │   └──────┬──────┘      │                │
                          │          │              │                │
                          │   ┌──────▼──────┐  ┌───▼──────────┐    │
                          │   │ Spring Data  │  │  Spring AI    │    │
                          │   │ JPA          │  │              │    │
                          │   └──────┬──────┘  │  ChatModel    │    │
                          │          │         │  EmbeddingModel│    │
                          │   ┌──────▼──────┐  │  VectorStore  │    │
                          │   │ PostgreSQL   │  │  DocumentReader│   │
                          │   │ + pgvector   │◀─┘              │    │
                          │   └─────────────┘  └──────────────┘    │
                          │                                          │
                          │   ┌─────────────┐                       │
                          │   │ File Storage │  Local disk (dev)     │
                          │   │              │  S3 (prod, optional)  │
                          │   └─────────────┘                       │
                          └──────────────────────────────────────────┘
```

### Key Architectural Decisions

1. **Async document processing** — Upload returns immediately; processing happens in a background thread (Spring's `@Async` or a simple `TaskExecutor`). Status pushed to client via SSE. Keeps the upload responsive and allows for retry logic on failures.

2. **Document-scoped vector search** — Embeddings are stored globally in pgvector but queries are always filtered to `WHERE document_id = ?`. Each document is its own isolated knowledge base. This is a deliberate design choice: the AI should only answer from the specific document, not cross-contaminate between a user's phone bill and their insurance policy.

3. **Conversation history in prompt** — Last N messages (configurable, default 10) are included in the chat prompt for continuity. Token budget: ~500 for system prompt + ~2000 for retrieved chunks + ~1500 for history + remainder for response. This needs to be managed carefully — a utility method that trims history from the oldest messages when approaching the limit.

4. **File storage abstraction** — `StorageService` interface with `LocalStorageService` (dev) and `S3StorageService` (prod) implementations. Spring profile-driven. For portfolio purposes, local storage is fine, but having the abstraction shows production thinking.

---

## Implementation Phases

### Phase 1 — Foundation (Week 1-2)
- Spring Boot project setup with Spring Security OAuth2 (Google)
- PostgreSQL + Flyway migrations for users, documents, document_summaries
- File upload endpoint (PDF + text only, no images yet)
- Text extraction from PDF (Apache PDFBox or Spring AI DocumentReader)
- Basic summary generation (single LLM call, no RAG yet)
- React app scaffold: login, dashboard, upload, basic document view
- Deploy: Docker Compose for local dev (app + Postgres + pgvector)

**Milestone**: Upload a text PDF, get a plain-English summary.

### Phase 2 — RAG & Chat (Week 3-4)
- Chunking pipeline with embeddings (pgvector)
- Chat endpoint with RAG retrieval
- SSE streaming for chat responses
- Conversation persistence (messages table)
- React chat UI with streaming display
- Suggested questions by document type
- Source chunk attribution on responses

**Milestone**: Upload a document, read the summary, ask follow-up questions grounded in the document.

### Phase 3 — Vision & Polish (Week 5-6)
- Image upload support (PNG, JPG, HEIC)
- Vision LLM integration for image/scanned PDF text extraction
- Scanned PDF detection (check for text layer)
- Jargoyle Decoder UI component
- Key facts structured display
- "Show source" highlighting in document view
- Processing progress SSE with named stages
- Error handling and edge cases (empty documents, unsupported formats, very long documents)
- Mobile responsive pass

**Milestone**: Photograph a paper bill with your phone, upload it, get a full explanation.

### Phase 4 — Portfolio Readiness (Week 7)
- README with architecture diagram, screenshots, and setup instructions
- Test coverage: unit tests for services, integration tests for API, repository tests with Testcontainers
- Code quality: consistent patterns, Javadoc on public APIs, clean package structure
- Demo data seeder (optional: pre-loaded example documents for reviewers)
- Docker Compose for one-command local setup
- Record a 2-minute demo video walkthrough

**Milestone**: Someone can clone the repo (`jargoyle`), run `docker compose up`, and have a working app.

---

## Testing Strategy

- **Unit tests**: Service layer logic, chunking strategy, prompt construction, token budget management
- **Integration tests**: Full API tests with `@SpringBootTest` + Testcontainers (PostgreSQL + pgvector). Test the upload → process → chat flow end-to-end.
- **Repository tests**: `@DataJpaTest` with Testcontainers for custom queries
- **LLM interaction tests**: Tricky to unit test. Use Spring AI's test support where available. For the RAG pipeline, test the retrieval and prompt assembly separately from the actual LLM call. Consider a mock `ChatModel` that returns canned responses for deterministic testing.
- **Frontend**: React Testing Library for component tests, especially the chat interface and file upload flow.

---

## Talking Points for Interviews

Things this project lets you discuss credibly:

- **RAG pipeline design**: Chunking strategies, embedding models, retrieval quality, prompt engineering for grounded answers
- **Vision AI for document processing**: Why vision LLMs beat traditional OCR for complex layouts, cost/quality tradeoffs
- **Async processing patterns**: Background job execution, SSE for progress updates, error recovery
- **Data modelling**: The document → chunks → conversations → messages hierarchy, JSONB for semi-structured data alongside relational data
- **Security**: OAuth2/OIDC, document-scoped access control (users can only see their own documents), input validation on file uploads
- **Production considerations you'd add**: Rate limiting, file size/type validation, cost controls on LLM calls, monitoring/observability, queue-based processing (SQS/RabbitMQ) instead of `@Async` at scale
- **Spring AI specifically**: How the framework abstracts LLM providers, the VectorStore abstraction, document readers and text splitters

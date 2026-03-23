-- Supports the findByDocumentIdOrderByCreatedAtDescIdDesc query,
-- which sorts conversations by creation time rather than last message time.
create index idx_conversations_document_id_created_at
    on conversations(document_id, created_at desc, id desc);

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

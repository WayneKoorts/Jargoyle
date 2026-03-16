create table conversations (
    id uuid primary key,
    document_id uuid not null references documents(id) on delete cascade,
    title varchar(255),
    created_at timestamp with time zone not null default now(),
    last_message_at timestamp with time zone not null default now()
);

create index idx_conversations_document_id on conversations(document_id);
create index idx_conversations_document_id_last_message_at on conversations(document_id, last_message_at desc);

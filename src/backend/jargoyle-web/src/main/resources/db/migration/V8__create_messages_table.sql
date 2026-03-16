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

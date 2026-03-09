create table document_summaries (
    id uuid primary key,
    document_id uuid not null unique references documents(id) on delete cascade,
    plain_summary text not null,
    key_facts jsonb not null default '{}',
    flagged_terms jsonb not null default '[]',
    generated_at timestamp with time zone not null default now()
);

create table documents (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    title varchar(255),
    document_type varchar(50),
    original_filename varchar(255),
    input_type varchar(20) not null,
    storage_key varchar(500),
    extracted_text text,
    status varchar(30) not null default 'UPLOADING',
    error_message text,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create index idx_documents_user_id on documents(user_id);
create index idx_documents_user_id_created_at on documents(user_id, created_at desc);

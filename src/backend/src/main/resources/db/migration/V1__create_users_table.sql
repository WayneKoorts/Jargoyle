create table users (
    id UUID primary key,
    email varchar(255) not null unique,
    display_name varchar(255) not null,
    oauth_provider varchar(50) not null,
    oauth_subject varchar(255) not null,
    created_at timestamp with time zone not null default now(),
    last_login_at timestamp with time zone,
    
    unique(oauth_provider, oauth_subject)
);

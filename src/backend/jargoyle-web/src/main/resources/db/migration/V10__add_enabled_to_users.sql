alter table users add column enabled boolean not null default false;

update users
set enabled = true
where role = 'ADMIN';

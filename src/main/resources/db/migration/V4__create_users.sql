create table users (
    id serial not null,
    username varchar(50) not null,
    password_hash varchar(50) not null,
    role varchar(20) not null default 'USER',
    created_at timestamptz not null default now(),

    constraint users_pkey primary key (id),
    constraint users_username_key unique (username),
    constraint users_role_check check (role in ('USER', 'ADMIN'))
);
create table topics(
    id serial not null,
    name varchar(255) not null,
    created_at timestamptz not null default now(),

    constraint topics_pkey primary key (id),
    constraint topics_name_key unique (name)
)
create table cards(
    id serial not null,
    topic_id integer not null,
    name varchar(255) not null,
    example_sentence text not null,
    translation varchar(255) not null,
    image_filename varchar(500) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint cards_pkey primary key (id),
    constraint cards_topic_id_fkey foreign key (topic_id) references topics (id) on delete cascade
);

create index cards_topic_id_idx on cards (topic_id);
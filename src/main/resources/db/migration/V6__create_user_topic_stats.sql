create table user_topic_stats (
    id serial not null,
    user_id integer not null,
    topic_id integer not null,
    total_reviews integer not null default 0,
    correct_reviews integer not null default 0,
    current_streak integer not null default 0,
    best_streak integer not null default 0,
    last_reviewed_at timestamptz,
    updated_at timestamptz not null default now(),

    constraint user_topic_stats_pkey primary key (id),
    constraint user_topic_stats_user_topic_key unique (user_id, topic_id),
    constraint user_topic_stats_user_fkey foreign key (user_id) references users (id) on delete cascade,
    constraint user_topic_stats_topic_fkey foreign key (topic_id) references topics (id) on delete cascade
);
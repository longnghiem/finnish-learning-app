create table review_schedules (
    id serial not null,
    user_id integer not null,
    card_id integer not null,
    repetition integer not null default 0,
    ease_factor numeric(4, 2) not null default 2.50,
    interval_days integer not null default 0,
    next_review_date date not null default current_date,
    last_reviewed_at timestamptz,

    constraint review_schedules_pkey primary key (id),
    constraint review_schedules_user_card_key unique (user_id, card_id),
    constraint review_schedules_user_fkey foreign key (user_id) references users (id) on delete cascade,
    constraint review_schedules_card_fkey foreign key (card_id) references cards (id) on delete cascade
);

create index review_schedules_user_next_review_idx on review_schedules (user_id, next_review_date);
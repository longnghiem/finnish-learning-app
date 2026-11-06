-- Recruiter chatbot ("Ask") tables.
-- ask_sessions and ask_turns hold conversation content and are purged after 30 days.
-- ask_daily_stats holds no personal data, is kept indefinitely, and backs the budget cap.

create table ask_sessions(
    id uuid not null,
    ip_hash varchar(64) not null,             -- HMAC-SHA256 of the client IP, lowercase hex
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,

    constraint ask_sessions_pkey primary key (id)
);
create index ask_sessions_ip_hash_created_idx on ask_sessions (ip_hash, created_at);

create table ask_turns(
    id bigserial not null,
    session_id uuid not null,
    mode varchar(16) not null,                -- CHAT | FIT_CHECK
    status varchar(16) not null,              -- PENDING | ANSWERED | BLOCKED | CAPPED | ERROR
    user_text text not null,                  -- the chat message or the job description
    answer_text text,                         -- chat answer
    fit_report jsonb,                         -- fit-check report
    tool_trace jsonb not null default '[]',   -- [{tool, args, isError}, ...]
    guardrail_outcome varchar(16),            -- PASSED | BLOCKED | ERROR; null until checked
    guardrail_scores jsonb,                   -- {category: score}
    input_tokens int not null default 0,
    output_tokens int not null default 0,
    cache_read_tokens int not null default 0,
    cache_write_tokens int not null default 0,
    guardrail_text_units int not null default 0,
    cost_usd numeric(12, 8) not null default 0,
    latency_ms int,
    created_at timestamptz not null default now(),

    constraint ask_turns_pkey primary key (id),
    constraint ask_turns_session_fkey foreign key (session_id) references ask_sessions (id) on delete cascade,
    constraint ask_turns_mode_check check (mode in ('CHAT', 'FIT_CHECK')),
    constraint ask_turns_status_check check (status in ('PENDING', 'ANSWERED', 'BLOCKED', 'CAPPED', 'ERROR')),
    constraint ask_turns_guardrail_outcome_check check (guardrail_outcome in ('PASSED', 'BLOCKED', 'ERROR'))
);
create index ask_turns_session_created_idx on ask_turns (session_id, created_at);
create index ask_turns_created_idx on ask_turns (created_at);

create table ask_daily_stats(
    day date not null,                        -- Europe/Helsinki calendar day
    answers int not null default 0,
    fit_checks int not null default 0,
    blocked int not null default 0,
    spent_usd numeric(12, 8) not null default 0,
    reserved_usd numeric(12, 8) not null default 0,

    constraint ask_daily_stats_pkey primary key (day),
    constraint ask_daily_stats_amounts_check check (spent_usd >= 0 and reserved_usd >= 0)
);
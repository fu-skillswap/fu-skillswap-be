-- rollout: EXPAND
-- Persistent forum abuse buckets and complete action audit targets.

create table forum_rate_limit_buckets (
    id uuid not null,
    bucket_key varchar(255) not null,
    window_started_at timestamp(6) not null,
    window_expires_at timestamp(6) not null,
    request_count bigint not null,
    primary key (id),
    constraint uq_forum_rate_limit_bucket_key_window unique (bucket_key, window_started_at)
);

create index idx_forum_rate_limit_buckets_expires
    on forum_rate_limit_buckets (window_expires_at);

alter table forum_action_logs add column target_type varchar(30);
alter table forum_action_logs add column target_id uuid;
alter table forum_action_logs add column metadata jsonb;

update forum_action_logs
set target_type = 'LEGACY',
    metadata = '{}'::jsonb
where target_type is null
   or metadata is null;

alter table forum_action_logs alter column target_type set not null;
alter table forum_action_logs alter column metadata set not null;

create index idx_forum_action_logs_target
    on forum_action_logs (target_type, target_id, created_at desc);

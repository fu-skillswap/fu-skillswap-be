create table if not exists internal_telemetry_events (
    id uuid primary key,
    event_type varchar(80) not null,
    user_id uuid,
    subject_type varchar(80),
    subject_id uuid,
    metadata_json text,
    created_at timestamp not null
);

create index if not exists idx_internal_telemetry_event_type_created
    on internal_telemetry_events (event_type, created_at desc);

create index if not exists idx_internal_telemetry_user_created
    on internal_telemetry_events (user_id, created_at desc);

create index if not exists idx_internal_telemetry_subject
    on internal_telemetry_events (subject_type, subject_id);

create index if not exists idx_payment_orders_status_updated
    on payment_orders (status, updated_at desc);

create index if not exists idx_notifications_recipient_read_created
    on notifications (recipient_user_id, read_at, created_at desc);

create index if not exists idx_forum_posts_author_created
    on forum_posts (author_user_id, created_at desc);

create index if not exists idx_forum_comments_author_created
    on forum_comments (author_user_id, created_at desc);

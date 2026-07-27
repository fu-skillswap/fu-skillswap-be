alter table mentor_service_resource_upload_intents
    add column cleanup_lease_until timestamp,
    add column next_cleanup_at timestamp,
    add column cleanup_attempt_count integer not null default 0,
    add column last_cleanup_error text,
    add column storage_deleted_at timestamp;
create index idx_mentor_service_resource_cleanup
    on mentor_service_resource_upload_intents(status, expires_at, next_cleanup_at)
    where storage_deleted_at is null;
create index idx_mentor_service_resource_reader
    on mentor_service_resources(service_id, visibility, created_at)
    where deleted_at is null;

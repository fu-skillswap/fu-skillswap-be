-- Scheduling management uses optimistic versions. Existing rows begin at version zero.
alter table mentor_services
    add column if not exists version integer not null default 0;

alter table mentor_availability_slots
    add column if not exists version integer not null default 0;

alter table mentor_booking_policies
    add column if not exists version integer not null default 0;

-- A mentee can retry after a rejected/cancelled request, but cannot create duplicate
-- active requests for the same offered segment.
create unique index if not exists uq_bookings_pending_request_segment
    on bookings (mentee_user_id, slot_id, service_id, selected_start_time)
    where status = 'PENDING';

create index if not exists idx_bookings_service_status_selected_start
    on bookings (service_id, status, selected_start_time);

alter table idempotency_keys
    add column if not exists request_fingerprint varchar(64),
    add column if not exists response_status integer,
    add column if not exists response_body text,
    add column if not exists completed_at timestamp,
    add column if not exists expires_at timestamp;

-- Old keys were only conflict markers. Treat them as expired rather than pretending
-- they can replay a response that was never recorded.
update idempotency_keys
set request_fingerprint = coalesce(request_fingerprint, repeat('0', 64)),
    expires_at = coalesce(expires_at, created_at)
where request_fingerprint is null or expires_at is null;

alter table idempotency_keys
    alter column request_fingerprint set not null,
    alter column expires_at set not null;

-- The availability-slot/service relation already has a primary key on (slot_id, service_id).
-- Timestamp columns intentionally remain unchanged in this migration: their JPA mapping is
-- still LocalDateTime. A later dedicated UTC migration must convert the whole scheduling
-- aggregate and calendar integrations atomically.

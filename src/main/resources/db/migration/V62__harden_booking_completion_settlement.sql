-- Booking completion, dispute and settlement hardening. Legacy status values remain readable.

alter table payment_orders add column if not exists settlement_status varchar(20);
alter table payment_orders add column if not exists released_at timestamp;
alter table payment_orders add column if not exists refunded_at timestamp;
alter table payment_orders add column if not exists refunded_scoin integer;
alter table payment_orders add column if not exists refund_reason varchar(120);

alter table bookings add column if not exists issue_responded_at timestamp;
alter table bookings add column if not exists issue_submitted_by_user_id uuid;
alter table bookings add column if not exists issue_responded_by_user_id uuid;
alter table bookings add column if not exists issue_response_note text;
alter table bookings add column if not exists mentor_completion_overdue_at timestamp;
alter table bookings add column if not exists post_session_prompted_at timestamp;
alter table bookings add column if not exists mentor_completion_reminder_30m_at timestamp;
alter table bookings add column if not exists mentor_completion_reminder_1h_at timestamp;
alter table bookings add column if not exists mentee_completion_prompted_at timestamp;
alter table bookings add column if not exists auto_close_warning_sent_at timestamp;
alter table bookings add column if not exists issue_escalation_sent_at timestamp;

alter table mentor_profiles add column if not exists mentor_no_show_count integer not null default 0;
alter table mentor_profiles add column if not exists mentor_completion_overdue_count integer not null default 0;

alter table credit_ledger_entries add column if not exists operation_key varchar(160);
create unique index if not exists uk_credit_ledger_entries_operation_key
    on credit_ledger_entries(operation_key) where operation_key is not null;

create table if not exists booking_events (
    id uuid primary key,
    booking_id uuid not null,
    event_type varchar(60) not null,
    event_version integer not null default 1,
    actor_user_id uuid,
    actor_type varchar(20) not null,
    old_status varchar(50),
    new_status varchar(50),
    metadata_json text,
    metadata_schema_version integer not null default 1,
    created_at timestamp not null default now()
);

create index if not exists idx_bookings_status_selected_end_time
    on bookings(status, selected_end_time);
create index if not exists idx_bookings_status_completed_at
    on bookings(status, completed_at);
create index if not exists idx_bookings_under_review_issue_submitted
    on bookings(status, issue_submitted_at);
create index if not exists idx_booking_events_booking_created
    on booking_events(booking_id, created_at desc);

-- Reconstruct only a summary for legacy paid orders. Existing ledger evidence wins.
update payment_orders p
set settlement_status = 'RELEASED'
where p.settlement_status is null
  and exists (
      select 1 from settlement_entries s
      where s.source_type = 'BOOKING' and s.source_id = p.booking_id and s.entry_type = 'RELEASE'
  );

update payment_orders p
set settlement_status = 'REFUNDED'
where p.settlement_status is null
  and exists (
      select 1 from credit_ledger_entries c
      where c.source_type = 'BOOKING' and c.source_id = p.booking_id and c.entry_type = 'REFUND'
  );

update payment_orders
set settlement_status = 'HELD'
where settlement_status is null and status = 'PAID';

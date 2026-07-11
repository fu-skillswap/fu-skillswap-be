alter table if exists domain_event_outbox
    add column if not exists trace_id varchar(64);

create index if not exists idx_domain_event_outbox_trace_created
    on domain_event_outbox (trace_id, created_at desc);

-- rollout: EXPAND
-- Add admin_sla_warning_sent_at column to bookings to track SLA breach warnings
alter table bookings add column if not exists admin_sla_warning_sent_at timestamp;

create index if not exists idx_bookings_under_review_admin_sla
    on bookings(status, issue_submitted_at, admin_sla_warning_sent_at)
    where status = 'UNDER_REVIEW';

-- rollout: EXPAND
-- Phase 1 group-session supply. Learner bookings are intentionally not linked until Phase 2.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE mentor_services
    ADD COLUMN IF NOT EXISTS delivery_mode varchar(32) NOT NULL DEFAULT 'ONE_TO_ONE';

ALTER TABLE mentor_services
    DROP CONSTRAINT IF EXISTS chk_mentor_services_delivery_mode;
ALTER TABLE mentor_services
    ADD CONSTRAINT chk_mentor_services_delivery_mode
        CHECK (delivery_mode IN ('ONE_TO_ONE', 'GROUP_SESSION'));

CREATE TABLE IF NOT EXISTS group_sessions (
    id uuid PRIMARY KEY,
    service_id uuid NOT NULL REFERENCES mentor_services(id),
    mentor_user_id uuid NOT NULL REFERENCES mentor_profiles(user_id),
    source_slot_id uuid NOT NULL REFERENCES mentor_availability_slots(id),
    scheduled_start_at timestamp(6) NOT NULL,
    scheduled_end_at timestamp(6) NOT NULL,
    max_participants integer NOT NULL,
    reserved_seat_count integer NOT NULL DEFAULT 0,
    status varchar(32) NOT NULL DEFAULT 'DRAFT',
    registration_status varchar(32) NOT NULL DEFAULT 'OPEN',
    registration_closes_at timestamp(6) NOT NULL,
    session_note varchar(1000),
    service_title_snapshot varchar(200),
    service_description_snapshot text,
    service_expected_outcome_snapshot text,
    service_duration_snapshot integer,
    service_is_free_snapshot boolean,
    service_price_scoin_snapshot integer,
    version integer NOT NULL DEFAULT 0,
    created_at timestamp(6) NOT NULL DEFAULT now(),
    updated_at timestamp(6) NOT NULL DEFAULT now(),
    published_at timestamp(6),
    cancelled_at timestamp(6),
    CONSTRAINT chk_group_sessions_interval CHECK (scheduled_end_at > scheduled_start_at),
    CONSTRAINT chk_group_sessions_capacity CHECK (
        max_participants BETWEEN 2 AND 20
        AND reserved_seat_count >= 0
        AND reserved_seat_count <= max_participants
    ),
    CONSTRAINT chk_group_sessions_status CHECK (status IN ('DRAFT', 'OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_group_sessions_registration_status CHECK (registration_status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_group_sessions_registration_deadline CHECK (registration_closes_at < scheduled_start_at),
    CONSTRAINT chk_group_sessions_snapshot_duration CHECK (service_duration_snapshot IS NULL OR service_duration_snapshot > 0),
    CONSTRAINT chk_group_sessions_snapshot_price CHECK (
        service_price_scoin_snapshot IS NULL OR service_price_scoin_snapshot >= 0
    ),
    CONSTRAINT chk_group_sessions_minute_precision CHECK (
        date_trunc('minute', scheduled_start_at) = scheduled_start_at
        AND date_trunc('minute', scheduled_end_at) = scheduled_end_at
        AND date_trunc('minute', registration_closes_at) = registration_closes_at
    )
);

CREATE INDEX IF NOT EXISTS idx_group_sessions_service_mentor_start
    ON group_sessions (service_id, mentor_user_id, scheduled_start_at);
CREATE INDEX IF NOT EXISTS idx_group_sessions_slot_start
    ON group_sessions (source_slot_id, scheduled_start_at);
CREATE INDEX IF NOT EXISTS idx_group_sessions_lifecycle
    ON group_sessions (status, registration_status, registration_closes_at, scheduled_start_at, scheduled_end_at);

ALTER TABLE group_sessions
    DROP CONSTRAINT IF EXISTS ex_group_sessions_active_mentor_interval;
ALTER TABLE group_sessions
    ADD CONSTRAINT ex_group_sessions_active_mentor_interval
    EXCLUDE USING gist (
        mentor_user_id WITH =,
        tsrange(scheduled_start_at, scheduled_end_at, '[)') WITH &&
    ) WHERE (status IN ('OPEN', 'IN_PROGRESS'));

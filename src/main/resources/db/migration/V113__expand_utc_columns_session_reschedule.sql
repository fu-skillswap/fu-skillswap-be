-- rollout: EXPAND
-- Slice 6: Expand schema UTC for Sessions, Booking Reschedule Requests, and Booking Events

-- 1. Add shadow TIMESTAMPTZ columns to sessions
ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS scheduled_start_time_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS scheduled_end_time_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS actual_start_time_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS actual_end_time_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS calendar_last_synced_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMPTZ;

-- 2. Add shadow TIMESTAMPTZ columns to booking_reschedule_requests
ALTER TABLE booking_reschedule_requests
    ADD COLUMN IF NOT EXISTS previous_selected_start_time_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS previous_selected_end_time_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS proposed_selected_start_time_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS proposed_selected_end_time_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS requested_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS responded_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS expired_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMPTZ;

-- 3. Add shadow TIMESTAMPTZ columns to booking_events
ALTER TABLE booking_events
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ;

-- 4. Backfill from legacy HCM timezone (Asia/Ho_Chi_Minh)
UPDATE sessions
SET
    scheduled_start_time_utc = CASE WHEN scheduled_start_time IS NOT NULL THEN scheduled_start_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    scheduled_end_time_utc = CASE WHEN scheduled_end_time IS NOT NULL THEN scheduled_end_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    actual_start_time_utc = CASE WHEN actual_start_time IS NOT NULL THEN actual_start_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    actual_end_time_utc = CASE WHEN actual_end_time IS NOT NULL THEN actual_end_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    calendar_last_synced_at_utc = CASE WHEN calendar_last_synced_at IS NOT NULL THEN calendar_last_synced_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    updated_at_utc = CASE WHEN updated_at IS NOT NULL THEN updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;

UPDATE booking_reschedule_requests
SET
    previous_selected_start_time_utc = CASE WHEN previous_selected_start_time IS NOT NULL THEN previous_selected_start_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    previous_selected_end_time_utc = CASE WHEN previous_selected_end_time IS NOT NULL THEN previous_selected_end_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    proposed_selected_start_time_utc = CASE WHEN proposed_selected_start_time IS NOT NULL THEN proposed_selected_start_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    proposed_selected_end_time_utc = CASE WHEN proposed_selected_end_time IS NOT NULL THEN proposed_selected_end_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    requested_at_utc = CASE WHEN requested_at IS NOT NULL THEN requested_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    responded_at_utc = CASE WHEN responded_at IS NOT NULL THEN responded_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    expired_at_utc = CASE WHEN expired_at IS NOT NULL THEN expired_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    updated_at_utc = CASE WHEN updated_at IS NOT NULL THEN updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;

UPDATE booking_events
SET
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;

-- 5. PostgreSQL Triggers for dual-write / bidirectional sync

-- 5.1 sessions sync trigger
CREATE OR REPLACE FUNCTION trg_sync_sessions_utc() RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR NEW.scheduled_start_time IS DISTINCT FROM OLD.scheduled_start_time) AND (NEW.scheduled_start_time_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.scheduled_start_time_utc = OLD.scheduled_start_time_utc)) THEN
        NEW.scheduled_start_time_utc := CASE WHEN NEW.scheduled_start_time IS NOT NULL THEN NEW.scheduled_start_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.scheduled_start_time_utc IS DISTINCT FROM OLD.scheduled_start_time_utc) AND NEW.scheduled_start_time = OLD.scheduled_start_time THEN
        NEW.scheduled_start_time := CASE WHEN NEW.scheduled_start_time_utc IS NOT NULL THEN (NEW.scheduled_start_time_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.scheduled_end_time IS DISTINCT FROM OLD.scheduled_end_time) AND (NEW.scheduled_end_time_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.scheduled_end_time_utc = OLD.scheduled_end_time_utc)) THEN
        NEW.scheduled_end_time_utc := CASE WHEN NEW.scheduled_end_time IS NOT NULL THEN NEW.scheduled_end_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.scheduled_end_time_utc IS DISTINCT FROM OLD.scheduled_end_time_utc) AND NEW.scheduled_end_time = OLD.scheduled_end_time THEN
        NEW.scheduled_end_time := CASE WHEN NEW.scheduled_end_time_utc IS NOT NULL THEN (NEW.scheduled_end_time_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.actual_start_time IS DISTINCT FROM OLD.actual_start_time) AND (NEW.actual_start_time_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.actual_start_time_utc = OLD.actual_start_time_utc)) THEN
        NEW.actual_start_time_utc := CASE WHEN NEW.actual_start_time IS NOT NULL THEN NEW.actual_start_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.actual_start_time_utc IS DISTINCT FROM OLD.actual_start_time_utc) AND NEW.actual_start_time = OLD.actual_start_time THEN
        NEW.actual_start_time := CASE WHEN NEW.actual_start_time_utc IS NOT NULL THEN (NEW.actual_start_time_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.actual_end_time IS DISTINCT FROM OLD.actual_end_time) AND (NEW.actual_end_time_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.actual_end_time_utc = OLD.actual_end_time_utc)) THEN
        NEW.actual_end_time_utc := CASE WHEN NEW.actual_end_time IS NOT NULL THEN NEW.actual_end_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.actual_end_time_utc IS DISTINCT FROM OLD.actual_end_time_utc) AND NEW.actual_end_time = OLD.actual_end_time THEN
        NEW.actual_end_time := CASE WHEN NEW.actual_end_time_utc IS NOT NULL THEN (NEW.actual_end_time_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.calendar_last_synced_at IS DISTINCT FROM OLD.calendar_last_synced_at) AND (NEW.calendar_last_synced_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.calendar_last_synced_at_utc = OLD.calendar_last_synced_at_utc)) THEN
        NEW.calendar_last_synced_at_utc := CASE WHEN NEW.calendar_last_synced_at IS NOT NULL THEN NEW.calendar_last_synced_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.calendar_last_synced_at_utc IS DISTINCT FROM OLD.calendar_last_synced_at_utc) AND NEW.calendar_last_synced_at = OLD.calendar_last_synced_at THEN
        NEW.calendar_last_synced_at := CASE WHEN NEW.calendar_last_synced_at_utc IS NOT NULL THEN (NEW.calendar_last_synced_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.created_at IS DISTINCT FROM OLD.created_at) AND (NEW.created_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.created_at_utc = OLD.created_at_utc)) THEN
        NEW.created_at_utc := CASE WHEN NEW.created_at IS NOT NULL THEN NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.created_at_utc IS DISTINCT FROM OLD.created_at_utc) AND NEW.created_at = OLD.created_at THEN
        NEW.created_at := CASE WHEN NEW.created_at_utc IS NOT NULL THEN (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.updated_at IS DISTINCT FROM OLD.updated_at) AND (NEW.updated_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.updated_at_utc = OLD.updated_at_utc)) THEN
        NEW.updated_at_utc := CASE WHEN NEW.updated_at IS NOT NULL THEN NEW.updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.updated_at_utc IS DISTINCT FROM OLD.updated_at_utc) AND NEW.updated_at = OLD.updated_at THEN
        NEW.updated_at := CASE WHEN NEW.updated_at_utc IS NOT NULL THEN (NEW.updated_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sessions_utc_sync ON sessions;
CREATE TRIGGER trg_sessions_utc_sync
    BEFORE INSERT OR UPDATE ON sessions
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_sessions_utc();

-- 5.2 booking_reschedule_requests sync trigger
CREATE OR REPLACE FUNCTION trg_sync_booking_reschedule_requests_utc() RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR NEW.previous_selected_start_time IS DISTINCT FROM OLD.previous_selected_start_time) AND (NEW.previous_selected_start_time_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.previous_selected_start_time_utc = OLD.previous_selected_start_time_utc)) THEN
        NEW.previous_selected_start_time_utc := CASE WHEN NEW.previous_selected_start_time IS NOT NULL THEN NEW.previous_selected_start_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.previous_selected_start_time_utc IS DISTINCT FROM OLD.previous_selected_start_time_utc) AND NEW.previous_selected_start_time = OLD.previous_selected_start_time THEN
        NEW.previous_selected_start_time := CASE WHEN NEW.previous_selected_start_time_utc IS NOT NULL THEN (NEW.previous_selected_start_time_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.previous_selected_end_time IS DISTINCT FROM OLD.previous_selected_end_time) AND (NEW.previous_selected_end_time_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.previous_selected_end_time_utc = OLD.previous_selected_end_time_utc)) THEN
        NEW.previous_selected_end_time_utc := CASE WHEN NEW.previous_selected_end_time IS NOT NULL THEN NEW.previous_selected_end_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.previous_selected_end_time_utc IS DISTINCT FROM OLD.previous_selected_end_time_utc) AND NEW.previous_selected_end_time = OLD.previous_selected_end_time THEN
        NEW.previous_selected_end_time := CASE WHEN NEW.previous_selected_end_time_utc IS NOT NULL THEN (NEW.previous_selected_end_time_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.proposed_selected_start_time IS DISTINCT FROM OLD.proposed_selected_start_time) AND (NEW.proposed_selected_start_time_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.proposed_selected_start_time_utc = OLD.proposed_selected_start_time_utc)) THEN
        NEW.proposed_selected_start_time_utc := CASE WHEN NEW.proposed_selected_start_time IS NOT NULL THEN NEW.proposed_selected_start_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.proposed_selected_start_time_utc IS DISTINCT FROM OLD.proposed_selected_start_time_utc) AND NEW.proposed_selected_start_time = OLD.proposed_selected_start_time THEN
        NEW.proposed_selected_start_time := CASE WHEN NEW.proposed_selected_start_time_utc IS NOT NULL THEN (NEW.proposed_selected_start_time_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.proposed_selected_end_time IS DISTINCT FROM OLD.proposed_selected_end_time) AND (NEW.proposed_selected_end_time_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.proposed_selected_end_time_utc = OLD.proposed_selected_end_time_utc)) THEN
        NEW.proposed_selected_end_time_utc := CASE WHEN NEW.proposed_selected_end_time IS NOT NULL THEN NEW.proposed_selected_end_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.proposed_selected_end_time_utc IS DISTINCT FROM OLD.proposed_selected_end_time_utc) AND NEW.proposed_selected_end_time = OLD.proposed_selected_end_time THEN
        NEW.proposed_selected_end_time := CASE WHEN NEW.proposed_selected_end_time_utc IS NOT NULL THEN (NEW.proposed_selected_end_time_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.requested_at IS DISTINCT FROM OLD.requested_at) AND (NEW.requested_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.requested_at_utc = OLD.requested_at_utc)) THEN
        NEW.requested_at_utc := CASE WHEN NEW.requested_at IS NOT NULL THEN NEW.requested_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.requested_at_utc IS DISTINCT FROM OLD.requested_at_utc) AND NEW.requested_at = OLD.requested_at THEN
        NEW.requested_at := CASE WHEN NEW.requested_at_utc IS NOT NULL THEN (NEW.requested_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.responded_at IS DISTINCT FROM OLD.responded_at) AND (NEW.responded_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.responded_at_utc = OLD.responded_at_utc)) THEN
        NEW.responded_at_utc := CASE WHEN NEW.responded_at IS NOT NULL THEN NEW.responded_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.responded_at_utc IS DISTINCT FROM OLD.responded_at_utc) AND NEW.responded_at = OLD.responded_at THEN
        NEW.responded_at := CASE WHEN NEW.responded_at_utc IS NOT NULL THEN (NEW.responded_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.expired_at IS DISTINCT FROM OLD.expired_at) AND (NEW.expired_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.expired_at_utc = OLD.expired_at_utc)) THEN
        NEW.expired_at_utc := CASE WHEN NEW.expired_at IS NOT NULL THEN NEW.expired_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.expired_at_utc IS DISTINCT FROM OLD.expired_at_utc) AND NEW.expired_at = OLD.expired_at THEN
        NEW.expired_at := CASE WHEN NEW.expired_at_utc IS NOT NULL THEN (NEW.expired_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.created_at IS DISTINCT FROM OLD.created_at) AND (NEW.created_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.created_at_utc = OLD.created_at_utc)) THEN
        NEW.created_at_utc := CASE WHEN NEW.created_at IS NOT NULL THEN NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.created_at_utc IS DISTINCT FROM OLD.created_at_utc) AND NEW.created_at = OLD.created_at THEN
        NEW.created_at := CASE WHEN NEW.created_at_utc IS NOT NULL THEN (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.updated_at IS DISTINCT FROM OLD.updated_at) AND (NEW.updated_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.updated_at_utc = OLD.updated_at_utc)) THEN
        NEW.updated_at_utc := CASE WHEN NEW.updated_at IS NOT NULL THEN NEW.updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.updated_at_utc IS DISTINCT FROM OLD.updated_at_utc) AND NEW.updated_at = OLD.updated_at THEN
        NEW.updated_at := CASE WHEN NEW.updated_at_utc IS NOT NULL THEN (NEW.updated_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_booking_reschedule_requests_utc_sync ON booking_reschedule_requests;
CREATE TRIGGER trg_booking_reschedule_requests_utc_sync
    BEFORE INSERT OR UPDATE ON booking_reschedule_requests
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_booking_reschedule_requests_utc();

-- 5.3 booking_events sync trigger
CREATE OR REPLACE FUNCTION trg_sync_booking_events_utc() RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR NEW.created_at IS DISTINCT FROM OLD.created_at) AND (NEW.created_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.created_at_utc = OLD.created_at_utc)) THEN
        NEW.created_at_utc := CASE WHEN NEW.created_at IS NOT NULL THEN NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.created_at_utc IS DISTINCT FROM OLD.created_at_utc) AND NEW.created_at = OLD.created_at THEN
        NEW.created_at := CASE WHEN NEW.created_at_utc IS NOT NULL THEN (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_booking_events_utc_sync ON booking_events;
CREATE TRIGGER trg_booking_events_utc_sync
    BEFORE INSERT OR UPDATE ON booking_events
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_booking_events_utc();

-- 6. Indexes for UTC columns
CREATE INDEX IF NOT EXISTS idx_sessions_scheduled_start_utc ON sessions(scheduled_start_time_utc);
CREATE INDEX IF NOT EXISTS idx_sessions_scheduled_end_utc ON sessions(scheduled_end_time_utc);
CREATE INDEX IF NOT EXISTS idx_sessions_created_at_utc ON sessions(created_at_utc);
CREATE INDEX IF NOT EXISTS idx_booking_reschedule_requested_at_utc ON booking_reschedule_requests(requested_at_utc);
CREATE INDEX IF NOT EXISTS idx_booking_reschedule_expired_at_utc ON booking_reschedule_requests(expired_at_utc);
CREATE INDEX IF NOT EXISTS idx_booking_events_created_at_utc ON booking_events(created_at_utc);

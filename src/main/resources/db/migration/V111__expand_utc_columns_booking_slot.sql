-- rollout: EXPAND
-- Slice 2: Expand schema UTC for Booking and Mentor Availability Slots

-- 1. Add shadow TIMESTAMPTZ columns to bookings
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS selected_start_time_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS selected_end_time_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS accepted_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS pending_expire_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejected_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS finalized_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS auto_closed_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS issue_submitted_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS issue_responded_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS issue_resolved_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS mentor_completion_overdue_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS post_session_prompted_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS mentor_completion_reminder_30m_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS mentor_completion_reminder_1h_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS mentee_completion_prompted_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS auto_close_warning_sent_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS issue_escalation_sent_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS admin_sla_warning_sent_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS calendar_availability_checked_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMPTZ;

-- 2. Add shadow TIMESTAMPTZ columns to mentor_availability_slots
ALTER TABLE mentor_availability_slots
    ADD COLUMN IF NOT EXISTS start_time_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS end_time_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMPTZ;

-- 3. Backfill from legacy HCM timezone (Asia/Ho_Chi_Minh)
UPDATE bookings
SET
    selected_start_time_utc = CASE WHEN selected_start_time IS NOT NULL THEN selected_start_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    selected_end_time_utc = CASE WHEN selected_end_time IS NOT NULL THEN selected_end_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    accepted_at_utc = CASE WHEN accepted_at IS NOT NULL THEN accepted_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    pending_expire_at_utc = CASE WHEN pending_expire_at IS NOT NULL THEN pending_expire_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    cancelled_at_utc = CASE WHEN cancelled_at IS NOT NULL THEN cancelled_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    completed_at_utc = CASE WHEN completed_at IS NOT NULL THEN completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    rejected_at_utc = CASE WHEN rejected_at IS NOT NULL THEN rejected_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    finalized_at_utc = CASE WHEN finalized_at IS NOT NULL THEN finalized_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    auto_closed_at_utc = CASE WHEN auto_closed_at IS NOT NULL THEN auto_closed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    issue_submitted_at_utc = CASE WHEN issue_submitted_at IS NOT NULL THEN issue_submitted_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    issue_responded_at_utc = CASE WHEN issue_responded_at IS NOT NULL THEN issue_responded_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    issue_resolved_at_utc = CASE WHEN issue_resolved_at IS NOT NULL THEN issue_resolved_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    mentor_completion_overdue_at_utc = CASE WHEN mentor_completion_overdue_at IS NOT NULL THEN mentor_completion_overdue_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    post_session_prompted_at_utc = CASE WHEN post_session_prompted_at IS NOT NULL THEN post_session_prompted_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    mentor_completion_reminder_30m_at_utc = CASE WHEN mentor_completion_reminder_30m_at IS NOT NULL THEN mentor_completion_reminder_30m_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    mentor_completion_reminder_1h_at_utc = CASE WHEN mentor_completion_reminder_1h_at IS NOT NULL THEN mentor_completion_reminder_1h_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    mentee_completion_prompted_at_utc = CASE WHEN mentee_completion_prompted_at IS NOT NULL THEN mentee_completion_prompted_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    auto_close_warning_sent_at_utc = CASE WHEN auto_close_warning_sent_at IS NOT NULL THEN auto_close_warning_sent_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    issue_escalation_sent_at_utc = CASE WHEN issue_escalation_sent_at IS NOT NULL THEN issue_escalation_sent_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    admin_sla_warning_sent_at_utc = CASE WHEN admin_sla_warning_sent_at IS NOT NULL THEN admin_sla_warning_sent_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    calendar_availability_checked_at_utc = CASE WHEN calendar_availability_checked_at IS NOT NULL THEN calendar_availability_checked_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    updated_at_utc = CASE WHEN updated_at IS NOT NULL THEN updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;

UPDATE mentor_availability_slots
SET
    start_time_utc = CASE WHEN start_time IS NOT NULL THEN start_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    end_time_utc = CASE WHEN end_time IS NOT NULL THEN end_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    updated_at_utc = CASE WHEN updated_at IS NOT NULL THEN updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;

-- 4. PostgreSQL Triggers for dual-write / bidirectional sync
CREATE OR REPLACE FUNCTION trg_sync_bookings_utc() RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR NEW.selected_start_time IS DISTINCT FROM OLD.selected_start_time) AND (NEW.selected_start_time_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.selected_start_time_utc = OLD.selected_start_time_utc)) THEN
        NEW.selected_start_time_utc := CASE WHEN NEW.selected_start_time IS NOT NULL THEN NEW.selected_start_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.selected_start_time_utc IS DISTINCT FROM OLD.selected_start_time_utc) AND NEW.selected_start_time = OLD.selected_start_time THEN
        NEW.selected_start_time := CASE WHEN NEW.selected_start_time_utc IS NOT NULL THEN (NEW.selected_start_time_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.selected_end_time IS DISTINCT FROM OLD.selected_end_time) AND (NEW.selected_end_time_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.selected_end_time_utc = OLD.selected_end_time_utc)) THEN
        NEW.selected_end_time_utc := CASE WHEN NEW.selected_end_time IS NOT NULL THEN NEW.selected_end_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.selected_end_time_utc IS DISTINCT FROM OLD.selected_end_time_utc) AND NEW.selected_end_time = OLD.selected_end_time THEN
        NEW.selected_end_time := CASE WHEN NEW.selected_end_time_utc IS NOT NULL THEN (NEW.selected_end_time_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.accepted_at IS DISTINCT FROM OLD.accepted_at) AND (NEW.accepted_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.accepted_at_utc = OLD.accepted_at_utc)) THEN
        NEW.accepted_at_utc := CASE WHEN NEW.accepted_at IS NOT NULL THEN NEW.accepted_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.accepted_at_utc IS DISTINCT FROM OLD.accepted_at_utc) AND NEW.accepted_at = OLD.accepted_at THEN
        NEW.accepted_at := CASE WHEN NEW.accepted_at_utc IS NOT NULL THEN (NEW.accepted_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.pending_expire_at IS DISTINCT FROM OLD.pending_expire_at) AND (NEW.pending_expire_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.pending_expire_at_utc = OLD.pending_expire_at_utc)) THEN
        NEW.pending_expire_at_utc := CASE WHEN NEW.pending_expire_at IS NOT NULL THEN NEW.pending_expire_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.pending_expire_at_utc IS DISTINCT FROM OLD.pending_expire_at_utc) AND NEW.pending_expire_at = OLD.pending_expire_at THEN
        NEW.pending_expire_at := CASE WHEN NEW.pending_expire_at_utc IS NOT NULL THEN (NEW.pending_expire_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.cancelled_at IS DISTINCT FROM OLD.cancelled_at) AND (NEW.cancelled_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.cancelled_at_utc = OLD.cancelled_at_utc)) THEN
        NEW.cancelled_at_utc := CASE WHEN NEW.cancelled_at IS NOT NULL THEN NEW.cancelled_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.cancelled_at_utc IS DISTINCT FROM OLD.cancelled_at_utc) AND NEW.cancelled_at = OLD.cancelled_at THEN
        NEW.cancelled_at := CASE WHEN NEW.cancelled_at_utc IS NOT NULL THEN (NEW.cancelled_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.completed_at IS DISTINCT FROM OLD.completed_at) AND (NEW.completed_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.completed_at_utc = OLD.completed_at_utc)) THEN
        NEW.completed_at_utc := CASE WHEN NEW.completed_at IS NOT NULL THEN NEW.completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.completed_at_utc IS DISTINCT FROM OLD.completed_at_utc) AND NEW.completed_at = OLD.completed_at THEN
        NEW.completed_at := CASE WHEN NEW.completed_at_utc IS NOT NULL THEN (NEW.completed_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.rejected_at IS DISTINCT FROM OLD.rejected_at) AND (NEW.rejected_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.rejected_at_utc = OLD.rejected_at_utc)) THEN
        NEW.rejected_at_utc := CASE WHEN NEW.rejected_at IS NOT NULL THEN NEW.rejected_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.rejected_at_utc IS DISTINCT FROM OLD.rejected_at_utc) AND NEW.rejected_at = OLD.rejected_at THEN
        NEW.rejected_at := CASE WHEN NEW.rejected_at_utc IS NOT NULL THEN (NEW.rejected_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.finalized_at IS DISTINCT FROM OLD.finalized_at) AND (NEW.finalized_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.finalized_at_utc = OLD.finalized_at_utc)) THEN
        NEW.finalized_at_utc := CASE WHEN NEW.finalized_at IS NOT NULL THEN NEW.finalized_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.finalized_at_utc IS DISTINCT FROM OLD.finalized_at_utc) AND NEW.finalized_at = OLD.finalized_at THEN
        NEW.finalized_at := CASE WHEN NEW.finalized_at_utc IS NOT NULL THEN (NEW.finalized_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.auto_closed_at IS DISTINCT FROM OLD.auto_closed_at) AND (NEW.auto_closed_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.auto_closed_at_utc = OLD.auto_closed_at_utc)) THEN
        NEW.auto_closed_at_utc := CASE WHEN NEW.auto_closed_at IS NOT NULL THEN NEW.auto_closed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.auto_closed_at_utc IS DISTINCT FROM OLD.auto_closed_at_utc) AND NEW.auto_closed_at = OLD.auto_closed_at THEN
        NEW.auto_closed_at := CASE WHEN NEW.auto_closed_at_utc IS NOT NULL THEN (NEW.auto_closed_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.issue_submitted_at IS DISTINCT FROM OLD.issue_submitted_at) AND (NEW.issue_submitted_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.issue_submitted_at_utc = OLD.issue_submitted_at_utc)) THEN
        NEW.issue_submitted_at_utc := CASE WHEN NEW.issue_submitted_at IS NOT NULL THEN NEW.issue_submitted_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.issue_submitted_at_utc IS DISTINCT FROM OLD.issue_submitted_at_utc) AND NEW.issue_submitted_at = OLD.issue_submitted_at THEN
        NEW.issue_submitted_at := CASE WHEN NEW.issue_submitted_at_utc IS NOT NULL THEN (NEW.issue_submitted_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.issue_responded_at IS DISTINCT FROM OLD.issue_responded_at) AND (NEW.issue_responded_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.issue_responded_at_utc = OLD.issue_responded_at_utc)) THEN
        NEW.issue_responded_at_utc := CASE WHEN NEW.issue_responded_at IS NOT NULL THEN NEW.issue_responded_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.issue_responded_at_utc IS DISTINCT FROM OLD.issue_responded_at_utc) AND NEW.issue_responded_at = OLD.issue_responded_at THEN
        NEW.issue_responded_at := CASE WHEN NEW.issue_responded_at_utc IS NOT NULL THEN (NEW.issue_responded_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.issue_resolved_at IS DISTINCT FROM OLD.issue_resolved_at) AND (NEW.issue_resolved_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.issue_resolved_at_utc = OLD.issue_resolved_at_utc)) THEN
        NEW.issue_resolved_at_utc := CASE WHEN NEW.issue_resolved_at IS NOT NULL THEN NEW.issue_resolved_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.issue_resolved_at_utc IS DISTINCT FROM OLD.issue_resolved_at_utc) AND NEW.issue_resolved_at = OLD.issue_resolved_at THEN
        NEW.issue_resolved_at := CASE WHEN NEW.issue_resolved_at_utc IS NOT NULL THEN (NEW.issue_resolved_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.mentor_completion_overdue_at IS DISTINCT FROM OLD.mentor_completion_overdue_at) AND (NEW.mentor_completion_overdue_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.mentor_completion_overdue_at_utc = OLD.mentor_completion_overdue_at_utc)) THEN
        NEW.mentor_completion_overdue_at_utc := CASE WHEN NEW.mentor_completion_overdue_at IS NOT NULL THEN NEW.mentor_completion_overdue_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.mentor_completion_overdue_at_utc IS DISTINCT FROM OLD.mentor_completion_overdue_at_utc) AND NEW.mentor_completion_overdue_at = OLD.mentor_completion_overdue_at THEN
        NEW.mentor_completion_overdue_at := CASE WHEN NEW.mentor_completion_overdue_at_utc IS NOT NULL THEN (NEW.mentor_completion_overdue_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.post_session_prompted_at IS DISTINCT FROM OLD.post_session_prompted_at) AND (NEW.post_session_prompted_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.post_session_prompted_at_utc = OLD.post_session_prompted_at_utc)) THEN
        NEW.post_session_prompted_at_utc := CASE WHEN NEW.post_session_prompted_at IS NOT NULL THEN NEW.post_session_prompted_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.post_session_prompted_at_utc IS DISTINCT FROM OLD.post_session_prompted_at_utc) AND NEW.post_session_prompted_at = OLD.post_session_prompted_at THEN
        NEW.post_session_prompted_at := CASE WHEN NEW.post_session_prompted_at_utc IS NOT NULL THEN (NEW.post_session_prompted_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.mentor_completion_reminder_30m_at IS DISTINCT FROM OLD.mentor_completion_reminder_30m_at) AND (NEW.mentor_completion_reminder_30m_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.mentor_completion_reminder_30m_at_utc = OLD.mentor_completion_reminder_30m_at_utc)) THEN
        NEW.mentor_completion_reminder_30m_at_utc := CASE WHEN NEW.mentor_completion_reminder_30m_at IS NOT NULL THEN NEW.mentor_completion_reminder_30m_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.mentor_completion_reminder_30m_at_utc IS DISTINCT FROM OLD.mentor_completion_reminder_30m_at_utc) AND NEW.mentor_completion_reminder_30m_at = OLD.mentor_completion_reminder_30m_at THEN
        NEW.mentor_completion_reminder_30m_at := CASE WHEN NEW.mentor_completion_reminder_30m_at_utc IS NOT NULL THEN (NEW.mentor_completion_reminder_30m_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.mentor_completion_reminder_1h_at IS DISTINCT FROM OLD.mentor_completion_reminder_1h_at) AND (NEW.mentor_completion_reminder_1h_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.mentor_completion_reminder_1h_at_utc = OLD.mentor_completion_reminder_1h_at_utc)) THEN
        NEW.mentor_completion_reminder_1h_at_utc := CASE WHEN NEW.mentor_completion_reminder_1h_at IS NOT NULL THEN NEW.mentor_completion_reminder_1h_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.mentor_completion_reminder_1h_at_utc IS DISTINCT FROM OLD.mentor_completion_reminder_1h_at_utc) AND NEW.mentor_completion_reminder_1h_at = OLD.mentor_completion_reminder_1h_at THEN
        NEW.mentor_completion_reminder_1h_at := CASE WHEN NEW.mentor_completion_reminder_1h_at_utc IS NOT NULL THEN (NEW.mentor_completion_reminder_1h_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.mentee_completion_prompted_at IS DISTINCT FROM OLD.mentee_completion_prompted_at) AND (NEW.mentee_completion_prompted_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.mentee_completion_prompted_at_utc = OLD.mentee_completion_prompted_at_utc)) THEN
        NEW.mentee_completion_prompted_at_utc := CASE WHEN NEW.mentee_completion_prompted_at IS NOT NULL THEN NEW.mentee_completion_prompted_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.mentee_completion_prompted_at_utc IS DISTINCT FROM OLD.mentee_completion_prompted_at_utc) AND NEW.mentee_completion_prompted_at = OLD.mentee_completion_prompted_at THEN
        NEW.mentee_completion_prompted_at := CASE WHEN NEW.mentee_completion_prompted_at_utc IS NOT NULL THEN (NEW.mentee_completion_prompted_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.auto_close_warning_sent_at IS DISTINCT FROM OLD.auto_close_warning_sent_at) AND (NEW.auto_close_warning_sent_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.auto_close_warning_sent_at_utc = OLD.auto_close_warning_sent_at_utc)) THEN
        NEW.auto_close_warning_sent_at_utc := CASE WHEN NEW.auto_close_warning_sent_at IS NOT NULL THEN NEW.auto_close_warning_sent_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.auto_close_warning_sent_at_utc IS DISTINCT FROM OLD.auto_close_warning_sent_at_utc) AND NEW.auto_close_warning_sent_at = OLD.auto_close_warning_sent_at THEN
        NEW.auto_close_warning_sent_at := CASE WHEN NEW.auto_close_warning_sent_at_utc IS NOT NULL THEN (NEW.auto_close_warning_sent_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.issue_escalation_sent_at IS DISTINCT FROM OLD.issue_escalation_sent_at) AND (NEW.issue_escalation_sent_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.issue_escalation_sent_at_utc = OLD.issue_escalation_sent_at_utc)) THEN
        NEW.issue_escalation_sent_at_utc := CASE WHEN NEW.issue_escalation_sent_at IS NOT NULL THEN NEW.issue_escalation_sent_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.issue_escalation_sent_at_utc IS DISTINCT FROM OLD.issue_escalation_sent_at_utc) AND NEW.issue_escalation_sent_at = OLD.issue_escalation_sent_at THEN
        NEW.issue_escalation_sent_at := CASE WHEN NEW.issue_escalation_sent_at_utc IS NOT NULL THEN (NEW.issue_escalation_sent_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.admin_sla_warning_sent_at IS DISTINCT FROM OLD.admin_sla_warning_sent_at) AND (NEW.admin_sla_warning_sent_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.admin_sla_warning_sent_at_utc = OLD.admin_sla_warning_sent_at_utc)) THEN
        NEW.admin_sla_warning_sent_at_utc := CASE WHEN NEW.admin_sla_warning_sent_at IS NOT NULL THEN NEW.admin_sla_warning_sent_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.admin_sla_warning_sent_at_utc IS DISTINCT FROM OLD.admin_sla_warning_sent_at_utc) AND NEW.admin_sla_warning_sent_at = OLD.admin_sla_warning_sent_at THEN
        NEW.admin_sla_warning_sent_at := CASE WHEN NEW.admin_sla_warning_sent_at_utc IS NOT NULL THEN (NEW.admin_sla_warning_sent_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.calendar_availability_checked_at IS DISTINCT FROM OLD.calendar_availability_checked_at) AND (NEW.calendar_availability_checked_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.calendar_availability_checked_at_utc = OLD.calendar_availability_checked_at_utc)) THEN
        NEW.calendar_availability_checked_at_utc := CASE WHEN NEW.calendar_availability_checked_at IS NOT NULL THEN NEW.calendar_availability_checked_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.calendar_availability_checked_at_utc IS DISTINCT FROM OLD.calendar_availability_checked_at_utc) AND NEW.calendar_availability_checked_at = OLD.calendar_availability_checked_at THEN
        NEW.calendar_availability_checked_at := CASE WHEN NEW.calendar_availability_checked_at_utc IS NOT NULL THEN (NEW.calendar_availability_checked_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
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

DROP TRIGGER IF EXISTS trg_bookings_sync_utc ON bookings;
CREATE TRIGGER trg_bookings_sync_utc
    BEFORE INSERT OR UPDATE ON bookings
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_bookings_utc();

-- Trigger for mentor_availability_slots
CREATE OR REPLACE FUNCTION trg_sync_mentor_availability_slots_utc() RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR NEW.start_time IS DISTINCT FROM OLD.start_time) AND (NEW.start_time_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.start_time_utc = OLD.start_time_utc)) THEN
        NEW.start_time_utc := CASE WHEN NEW.start_time IS NOT NULL THEN NEW.start_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.start_time_utc IS DISTINCT FROM OLD.start_time_utc) AND NEW.start_time = OLD.start_time THEN
        NEW.start_time := CASE WHEN NEW.start_time_utc IS NOT NULL THEN (NEW.start_time_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.end_time IS DISTINCT FROM OLD.end_time) AND (NEW.end_time_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.end_time_utc = OLD.end_time_utc)) THEN
        NEW.end_time_utc := CASE WHEN NEW.end_time IS NOT NULL THEN NEW.end_time AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.end_time_utc IS DISTINCT FROM OLD.end_time_utc) AND NEW.end_time = OLD.end_time THEN
        NEW.end_time := CASE WHEN NEW.end_time_utc IS NOT NULL THEN (NEW.end_time_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
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

DROP TRIGGER IF EXISTS trg_mentor_availability_slots_sync_utc ON mentor_availability_slots;
CREATE TRIGGER trg_mentor_availability_slots_sync_utc
    BEFORE INSERT OR UPDATE ON mentor_availability_slots
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_mentor_availability_slots_utc();

-- 5. Indexes on UTC shadow columns
CREATE INDEX IF NOT EXISTS idx_bookings_start_time_utc
    ON bookings (selected_start_time_utc);

CREATE INDEX IF NOT EXISTS idx_bookings_end_time_utc
    ON bookings (selected_end_time_utc);

CREATE INDEX IF NOT EXISTS idx_bookings_mentor_status_start_utc
    ON bookings (mentor_user_id, status, selected_start_time_utc);

CREATE INDEX IF NOT EXISTS idx_bookings_mentee_status_start_utc
    ON bookings (mentee_user_id, status, selected_start_time_utc);

CREATE INDEX IF NOT EXISTS idx_bookings_status_start_utc
    ON bookings (status, selected_start_time_utc);

CREATE INDEX IF NOT EXISTS idx_bookings_pending_expire_utc
    ON bookings (status, pending_expire_at_utc);

CREATE INDEX IF NOT EXISTS idx_bookings_lifecycle_overlap_utc
    ON bookings (mentor_user_id, status, selected_start_time_utc, selected_end_time_utc);

CREATE INDEX IF NOT EXISTS idx_availability_slots_start_utc
    ON mentor_availability_slots (start_time_utc);

CREATE INDEX IF NOT EXISTS idx_availability_slots_end_utc
    ON mentor_availability_slots (end_time_utc);

CREATE INDEX IF NOT EXISTS idx_availability_slots_mentor_start_utc
    ON mentor_availability_slots (mentor_user_id, is_active, is_booked, start_time_utc, end_time_utc);

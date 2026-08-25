-- rollout: EXPAND
-- Private, immutable evidence for booking disputes. Staging upload intents are
-- deliberately separate from attached evidence so a failed upload never changes a booking.
CREATE TABLE IF NOT EXISTS booking_issue_evidence_upload_intents (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    owner_user_id UUID NOT NULL REFERENCES users(id),
    staging_storage_key VARCHAR(600) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    expected_size_bytes BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at_utc TIMESTAMPTZ NOT NULL,
    confirmed_at_utc TIMESTAMPTZ,
    created_at_utc TIMESTAMPTZ NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_booking_issue_evidence_upload_intent_status
        CHECK (status IN ('PENDING_UPLOAD', 'CONFIRMED', 'EXPIRED', 'REJECTED')),
    CONSTRAINT chk_booking_issue_evidence_upload_intent_size
        CHECK (expected_size_bytes > 0)
);

CREATE INDEX IF NOT EXISTS idx_booking_issue_evidence_intent_owner_status_expiry
    ON booking_issue_evidence_upload_intents(owner_user_id, status, expires_at_utc);
CREATE INDEX IF NOT EXISTS idx_booking_issue_evidence_intent_booking
    ON booking_issue_evidence_upload_intents(booking_id, created_at_utc DESC);

CREATE TABLE IF NOT EXISTS booking_issue_evidences (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    upload_intent_id UUID NOT NULL UNIQUE REFERENCES booking_issue_evidence_upload_intents(id),
    submitted_by_user_id UUID NOT NULL REFERENCES users(id),
    submission_side VARCHAR(20),
    storage_key VARCHAR(600) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    state VARCHAR(32) NOT NULL,
    confirmed_at_utc TIMESTAMPTZ NOT NULL,
    attached_at_utc TIMESTAMPTZ,
    hidden_at_utc TIMESTAMPTZ,
    hidden_by_user_id UUID REFERENCES users(id),
    hidden_reason VARCHAR(1000),
    deleted_at_utc TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_booking_issue_evidence_side
        CHECK (submission_side IS NULL OR submission_side IN ('REPORTER', 'RESPONDER')),
    CONSTRAINT chk_booking_issue_evidence_state
        CHECK (state IN ('PENDING_ATTACH', 'ACTIVE', 'HIDDEN', 'DELETED')),
    CONSTRAINT chk_booking_issue_evidence_size CHECK (size_bytes > 0)
);

CREATE INDEX IF NOT EXISTS idx_booking_issue_evidences_booking_attached
    ON booking_issue_evidences(booking_id, attached_at_utc ASC);
CREATE INDEX IF NOT EXISTS idx_booking_issue_evidences_state_attached
    ON booking_issue_evidences(state, attached_at_utc ASC);

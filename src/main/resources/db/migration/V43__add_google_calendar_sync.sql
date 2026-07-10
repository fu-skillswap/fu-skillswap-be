ALTER TABLE oauth_accounts
    ADD COLUMN IF NOT EXISTS provider_access_token TEXT,
    ADD COLUMN IF NOT EXISTS provider_refresh_token TEXT,
    ADD COLUMN IF NOT EXISTS provider_token_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS provider_granted_scopes TEXT,
    ADD COLUMN IF NOT EXISTS provider_key_version INTEGER;

CREATE TABLE IF NOT EXISTS google_calendar_connections (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    google_subject VARCHAR(255) NOT NULL,
    google_email VARCHAR(255) NOT NULL,
    calendar_id VARCHAR(255) NOT NULL,
    access_token_ciphertext TEXT NOT NULL,
    refresh_token_ciphertext TEXT,
    token_expires_at TIMESTAMP,
    granted_scopes TEXT,
    key_version INTEGER NOT NULL,
    connection_status VARCHAR(50) NOT NULL,
    last_sync_status VARCHAR(50),
    last_sync_at TIMESTAMP,
    last_sync_error_code VARCHAR(100),
    last_sync_error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_google_calendar_connections_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_google_calendar_connections_user UNIQUE (user_id)
);

CREATE INDEX IF NOT EXISTS idx_google_calendar_connections_status
    ON google_calendar_connections (connection_status);

CREATE TABLE IF NOT EXISTS google_calendar_sync_jobs (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    session_id UUID,
    mentor_user_id UUID NOT NULL,
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    run_after TIMESTAMP NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    last_error_code VARCHAR(100),
    last_error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT fk_google_calendar_sync_jobs_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_google_calendar_sync_jobs_session FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE SET NULL,
    CONSTRAINT fk_google_calendar_sync_jobs_mentor FOREIGN KEY (mentor_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_google_calendar_sync_jobs_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_google_calendar_sync_jobs_poll
    ON google_calendar_sync_jobs (status, run_after);

CREATE TABLE IF NOT EXISTS google_calendar_event_links (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    session_id UUID NOT NULL,
    mentor_user_id UUID NOT NULL,
    google_event_id VARCHAR(255) NOT NULL,
    google_meet_url TEXT,
    etag VARCHAR(255),
    event_status VARCHAR(50) NOT NULL,
    last_error_code VARCHAR(100),
    last_error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_google_calendar_event_links_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_google_calendar_event_links_session FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_google_calendar_event_links_mentor FOREIGN KEY (mentor_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_google_calendar_event_links_booking UNIQUE (booking_id),
    CONSTRAINT uk_google_calendar_event_links_session UNIQUE (session_id)
);

CREATE INDEX IF NOT EXISTS idx_google_calendar_event_links_status
    ON google_calendar_event_links (event_status);

ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS google_calendar_managed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS google_meet_auto_generated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS calendar_sync_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS calendar_sync_error_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS calendar_sync_error_message TEXT,
    ADD COLUMN IF NOT EXISTS calendar_last_synced_at TIMESTAMP;

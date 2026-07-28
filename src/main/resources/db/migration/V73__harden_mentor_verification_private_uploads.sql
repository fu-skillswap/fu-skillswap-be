-- rollout: EXPAND
CREATE TABLE mentor_verification_upload_intents (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    expected_content_type VARCHAR(100) NOT NULL,
    expected_size_bytes BIGINT NOT NULL CHECK (expected_size_bytes > 0 AND expected_size_bytes <= 15728640),
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    confirmed_stored_file_id UUID UNIQUE REFERENCES files(id),
    confirmed_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_mentor_verification_upload_intents_owner_expiry
    ON mentor_verification_upload_intents(owner_user_id, status, expires_at);

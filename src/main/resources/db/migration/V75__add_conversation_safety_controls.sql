-- rollout: EXPAND
-- Participant blocks preserve text history while removing the ability to continue an unsafe exchange.
CREATE TABLE IF NOT EXISTS conversation_user_blocks (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    blocker_user_id UUID NOT NULL REFERENCES users(id),
    blocked_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_conversation_user_block UNIQUE (conversation_id, blocker_user_id),
    CONSTRAINT chk_conversation_user_block_distinct_users CHECK (blocker_user_id <> blocked_user_id)
);

CREATE INDEX IF NOT EXISTS idx_conversation_user_blocks_conversation
    ON conversation_user_blocks (conversation_id);

-- Reports are deliberately separate from a participant block: reporting is a moderation workflow,
-- while a block is an immediate user-safety control.
CREATE TABLE IF NOT EXISTS chat_reports (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    reporter_user_id UUID NOT NULL REFERENCES users(id),
    reported_user_id UUID NOT NULL REFERENCES users(id),
    reason_type VARCHAR(40) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(40) NOT NULL,
    reviewed_by_user_id UUID REFERENCES users(id),
    review_note VARCHAR(1000),
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_chat_report_distinct_users CHECK (reporter_user_id <> reported_user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_reports_status_created_at
    ON chat_reports (status, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_reports_open_reporter_conversation
    ON chat_reports (conversation_id, reporter_user_id)
    WHERE status = 'OPEN';

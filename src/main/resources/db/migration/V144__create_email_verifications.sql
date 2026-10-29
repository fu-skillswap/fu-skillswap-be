-- rollout: EXPAND
CREATE TABLE email_verifications (
    id UUID NOT NULL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    otp_code VARCHAR(10) NOT NULL,
    type VARCHAR(50) NOT NULL,
    full_name VARCHAR(150),
    pending_password_hash VARCHAR(255),
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP(6) NOT NULL,
    verified_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_email_verifications_email ON email_verifications (email);
CREATE INDEX idx_email_verifications_expires_at ON email_verifications (expires_at);

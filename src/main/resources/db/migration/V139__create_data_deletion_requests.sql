CREATE TABLE data_deletion_requests (
    id UUID PRIMARY KEY,
    email VARCHAR(150) NOT NULL,
    reason TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX idx_data_deletion_requests_email ON data_deletion_requests (email);
CREATE INDEX idx_data_deletion_requests_status ON data_deletion_requests (status);

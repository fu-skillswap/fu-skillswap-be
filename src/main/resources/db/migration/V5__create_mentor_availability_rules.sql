CREATE TABLE IF NOT EXISTS mentor_availability_rules (
    id UUID PRIMARY KEY,
    mentor_user_id UUID NOT NULL,
    rule_type VARCHAR(20) NOT NULL,
    repeat_type VARCHAR(20) NOT NULL,
    days_of_week VARCHAR(80),
    effective_from DATE NOT NULL,
    effective_to DATE,
    start_time TIME,
    end_time TIME,
    timezone VARCHAR(80) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    note VARCHAR(200),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_availability_rules_mentor
        FOREIGN KEY (mentor_user_id) REFERENCES mentor_profiles(user_id),
    CONSTRAINT ck_availability_rules_type
        CHECK (rule_type IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_availability_rules_repeat
        CHECK (repeat_type IN ('NONE', 'DAILY', 'WEEKLY')),
    CONSTRAINT ck_availability_rules_date_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_availability_rules_time_range
        CHECK (
            (start_time IS NULL AND end_time IS NULL)
            OR (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
        )
);

CREATE INDEX IF NOT EXISTS idx_availability_rules_mentor_active
    ON mentor_availability_rules (mentor_user_id, is_active);

CREATE INDEX IF NOT EXISTS idx_availability_rules_date_range
    ON mentor_availability_rules (effective_from, effective_to);

CREATE INDEX IF NOT EXISTS idx_availability_rules_repeat_type
    ON mentor_availability_rules (repeat_type);

CREATE INDEX IF NOT EXISTS idx_availability_slots_mentor_time_active
    ON mentor_availability_slots (mentor_user_id, start_time, is_active);

CREATE INDEX IF NOT EXISTS idx_availability_slots_mentor_exact_active
    ON mentor_availability_slots (mentor_user_id, start_time, end_time, is_active);

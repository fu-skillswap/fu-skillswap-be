-- rollout: EXPAND
-- Bounded weekly templates materialize existing availability slots; booking continues to use slots.

ALTER TABLE mentor_availability_slots
    ALTER COLUMN rule_id DROP NOT NULL;

CREATE TABLE availability_templates (
    id uuid PRIMARY KEY,
    mentor_user_id uuid NOT NULL REFERENCES mentor_profiles(user_id),
    start_time time NOT NULL,
    end_time time NOT NULL,
    weekdays varchar(80) NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    timezone varchar(80) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    note varchar(200),
    configured_status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    config_version integer NOT NULL DEFAULT 1,
    created_at timestamp(6) NOT NULL DEFAULT now(),
    updated_at timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT chk_availability_template_interval CHECK (end_time > start_time),
    CONSTRAINT chk_availability_template_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_availability_template_status CHECK (configured_status IN ('ACTIVE', 'PAUSED', 'ARCHIVED'))
);

CREATE TABLE availability_template_services (
    template_id uuid NOT NULL REFERENCES availability_templates(id) ON DELETE CASCADE,
    service_id uuid NOT NULL REFERENCES mentor_services(id),
    PRIMARY KEY (template_id, service_id)
);

CREATE TABLE availability_mentor_mutation_locks (
    mentor_user_id uuid PRIMARY KEY REFERENCES mentor_profiles(user_id)
);

CREATE TABLE availability_template_exceptions (
    id uuid PRIMARY KEY,
    template_id uuid NOT NULL REFERENCES availability_templates(id) ON DELETE CASCADE,
    occurrence_date date NOT NULL,
    provenance varchar(32) NOT NULL,
    source_manual_slot_id uuid NULL REFERENCES mentor_availability_slots(id) ON DELETE SET NULL,
    created_at timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT uq_availability_template_exception_date UNIQUE (template_id, occurrence_date),
    CONSTRAINT chk_availability_template_exception_provenance CHECK (provenance IN ('MENTOR', 'MANUAL_REPLACEMENT'))
);

CREATE TABLE availability_template_reconciliation (
    template_id uuid PRIMARY KEY REFERENCES availability_templates(id) ON DELETE CASCADE,
    last_reconciled_at timestamp(6),
    last_attempt_at timestamp(6),
    next_reconcile_at timestamp(6) NOT NULL DEFAULT now(),
    claim_token uuid,
    claimed_until timestamp(6),
    last_error_code varchar(100),
    last_error_message varchar(500),
    consecutive_failures integer NOT NULL DEFAULT 0
);

ALTER TABLE mentor_availability_slots
    ADD COLUMN template_id uuid NULL REFERENCES availability_templates(id),
    ADD COLUMN template_occurrence_date date NULL;

CREATE UNIQUE INDEX uq_availability_slot_template_occurrence
    ON mentor_availability_slots (template_id, template_occurrence_date)
    WHERE template_id IS NOT NULL;
CREATE INDEX idx_availability_templates_mentor_status
    ON availability_templates (mentor_user_id, configured_status, effective_from);
CREATE INDEX idx_availability_templates_effective_dates
    ON availability_templates (effective_from, effective_to);
CREATE INDEX idx_availability_template_reconciliation_due
    ON availability_template_reconciliation (next_reconcile_at, claimed_until);
CREATE INDEX idx_availability_template_exceptions_template_date
    ON availability_template_exceptions (template_id, occurrence_date);
CREATE INDEX idx_availability_slots_template_occurrence
    ON mentor_availability_slots (template_id, template_occurrence_date);

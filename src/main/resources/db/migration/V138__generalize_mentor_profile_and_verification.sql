-- rollout: EXPAND
-- V138: Generalize mentor profile and verification requests for multi-institution catalog support

CREATE TABLE IF NOT EXISTS mentor_profiles (
    user_id UUID PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS mentor_verification_requests (
    id UUID PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS mentor_verification_documents (
    id UUID PRIMARY KEY,
    document_type VARCHAR(40)
);

-- Alter mentor_profiles table
ALTER TABLE mentor_profiles ADD COLUMN IF NOT EXISTS institution_id UUID;
ALTER TABLE mentor_profiles ADD COLUMN IF NOT EXISTS custom_institution_name VARCHAR(200);
ALTER TABLE mentor_profiles ADD COLUMN IF NOT EXISTS custom_institution_province_id UUID;
ALTER TABLE mentor_profiles ADD COLUMN IF NOT EXISTS company_or_organization VARCHAR(200);
ALTER TABLE mentor_profiles ADD COLUMN IF NOT EXISTS primary_field_group_id UUID;

CREATE INDEX IF NOT EXISTS idx_mentor_profiles_institution ON mentor_profiles(institution_id);
CREATE INDEX IF NOT EXISTS idx_mentor_profiles_custom_province ON mentor_profiles(custom_institution_province_id);
CREATE INDEX IF NOT EXISTS idx_mentor_profiles_primary_field_group ON mentor_profiles(primary_field_group_id);

ALTER TABLE mentor_profiles ADD CONSTRAINT fk_mentor_profiles_institution
    FOREIGN KEY (institution_id) REFERENCES educational_institutions(id);
ALTER TABLE mentor_profiles ADD CONSTRAINT fk_mentor_profiles_custom_province
    FOREIGN KEY (custom_institution_province_id) REFERENCES administrative_provinces(id);
ALTER TABLE mentor_profiles ADD CONSTRAINT fk_mentor_profiles_primary_field_group
    FOREIGN KEY (primary_field_group_id) REFERENCES education_field_groups(id);

-- Alter mentor_verification_requests table
ALTER TABLE mentor_verification_requests ADD COLUMN IF NOT EXISTS institution_id UUID;
ALTER TABLE mentor_verification_requests ADD COLUMN IF NOT EXISTS custom_institution_name VARCHAR(200);
ALTER TABLE mentor_verification_requests ADD COLUMN IF NOT EXISTS custom_institution_province_id UUID;
ALTER TABLE mentor_verification_requests ADD COLUMN IF NOT EXISTS company_or_organization VARCHAR(200);
ALTER TABLE mentor_verification_requests ADD COLUMN IF NOT EXISTS primary_field_group_id UUID;

CREATE INDEX IF NOT EXISTS idx_mentor_verification_institution ON mentor_verification_requests(institution_id);
CREATE INDEX IF NOT EXISTS idx_mentor_verification_custom_province ON mentor_verification_requests(custom_institution_province_id);
CREATE INDEX IF NOT EXISTS idx_mentor_verification_primary_field_group ON mentor_verification_requests(primary_field_group_id);

ALTER TABLE mentor_verification_requests ADD CONSTRAINT fk_mentor_verification_institution
    FOREIGN KEY (institution_id) REFERENCES educational_institutions(id);
ALTER TABLE mentor_verification_requests ADD CONSTRAINT fk_mentor_verification_custom_province
    FOREIGN KEY (custom_institution_province_id) REFERENCES administrative_provinces(id);
ALTER TABLE mentor_verification_requests ADD CONSTRAINT fk_mentor_verification_primary_field_group
    FOREIGN KEY (primary_field_group_id) REFERENCES education_field_groups(id);

-- Alter mentor_verification_documents check constraint to allow generalized affiliation proof
ALTER TABLE mentor_verification_documents DROP CONSTRAINT IF EXISTS mentor_verification_documents_document_type_check;
ALTER TABLE mentor_verification_documents ADD CONSTRAINT mentor_verification_documents_document_type_check
    CHECK (document_type IS NULL OR document_type IN ('FPTU_AFFILIATION_PROOF', 'AFFILIATION_PROOF', 'EXPERTISE_PROOF'));

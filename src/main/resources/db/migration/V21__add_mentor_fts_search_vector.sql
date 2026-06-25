-- Phase S1: Add PostgreSQL full-text search vector to mentor_profiles for fast keyword retrieval.
-- Replaces sequential LIKE %...% scans with GIN-indexed tsvector queries.
-- Uses 'simple' config (lowercase + split) — sufficient for Vietnamese text at ~1000 mentor scale.

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- Populate initial search_vector for existing mentors
UPDATE mentor_profiles
SET search_vector = to_tsvector('simple',
    coalesce(headline, '') || ' ' ||
    coalesce(expertise_description, '') || ' ' ||
    coalesce(supporting_subjects, '')
)
WHERE search_vector IS NULL;

-- GIN index for fast @@ queries
CREATE INDEX IF NOT EXISTS idx_mentor_profiles_search_vector
    ON mentor_profiles USING GIN (search_vector);

-- Trigger function: auto-update search_vector on relevant profile changes
CREATE OR REPLACE FUNCTION mentor_profiles_fts_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple',
        coalesce(NEW.headline, '') || ' ' ||
        coalesce(NEW.expertise_description, '') || ' ' ||
        coalesce(NEW.supporting_subjects, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Drop and recreate trigger to ensure idempotency
DROP TRIGGER IF EXISTS trg_mentor_profiles_fts ON mentor_profiles;

CREATE TRIGGER trg_mentor_profiles_fts
    BEFORE INSERT OR UPDATE OF headline, expertise_description, supporting_subjects
    ON mentor_profiles
    FOR EACH ROW
    EXECUTE FUNCTION mentor_profiles_fts_update();

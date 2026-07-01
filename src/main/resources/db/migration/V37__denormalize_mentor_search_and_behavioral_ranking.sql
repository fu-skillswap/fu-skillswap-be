ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS search_document TEXT,
    ADD COLUMN IF NOT EXISTS total_accepted_bookings INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_mentor_cancelled_bookings INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_active_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_mentor_profiles_last_active_at
    ON mentor_profiles (last_active_at);

WITH booking_stats AS (
    SELECT b.mentor_user_id,
           COUNT(*) FILTER (WHERE b.accepted_at IS NOT NULL) AS accepted_count,
           COUNT(*) FILTER (WHERE b.status = 'CANCELLED_BY_MENTOR') AS mentor_cancelled_count,
           MAX(GREATEST(
               COALESCE(b.accepted_at, TIMESTAMP '1970-01-01 00:00:00'),
               COALESCE(b.rejected_at, TIMESTAMP '1970-01-01 00:00:00'),
               COALESCE(b.cancelled_at, TIMESTAMP '1970-01-01 00:00:00'),
               COALESCE(b.completed_at, TIMESTAMP '1970-01-01 00:00:00'),
               COALESCE(b.finalized_at, TIMESTAMP '1970-01-01 00:00:00'),
               COALESCE(b.updated_at, TIMESTAMP '1970-01-01 00:00:00')
           )) AS last_booking_activity_at
    FROM bookings b
    GROUP BY b.mentor_user_id
)
UPDATE mentor_profiles mp
SET total_accepted_bookings = COALESCE(bs.accepted_count, 0),
    total_mentor_cancelled_bookings = COALESCE(bs.mentor_cancelled_count, 0),
    last_active_at = NULLIF(
        GREATEST(
            COALESCE(mp.updated_at, TIMESTAMP '1970-01-01 00:00:00'),
            COALESCE(mp.verified_at, TIMESTAMP '1970-01-01 00:00:00'),
            COALESCE(bs.last_booking_activity_at, TIMESTAMP '1970-01-01 00:00:00')
        ),
        TIMESTAMP '1970-01-01 00:00:00'
    )
FROM booking_stats bs
WHERE bs.mentor_user_id = mp.user_id;

UPDATE mentor_profiles mp
SET last_active_at = NULLIF(
    GREATEST(
        COALESCE(mp.last_active_at, TIMESTAMP '1970-01-01 00:00:00'),
        COALESCE(mp.updated_at, TIMESTAMP '1970-01-01 00:00:00'),
        COALESCE(mp.verified_at, TIMESTAMP '1970-01-01 00:00:00')
    ),
    TIMESTAMP '1970-01-01 00:00:00'
)
WHERE mp.last_active_at IS NULL;

CREATE OR REPLACE FUNCTION skillswap_normalize_search_text(input_text TEXT)
RETURNS TEXT AS $$
    SELECT trim(
        regexp_replace(
            regexp_replace(
                translate(
                    lower(COALESCE(input_text, '')),
                    'àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ',
                    'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy'
                ),
                '[^a-z0-9\\s]+',
                ' ',
                'g'
            ),
            '\\s+',
            ' ',
            'g'
        )
    );
$$ LANGUAGE sql IMMUTABLE;

CREATE OR REPLACE FUNCTION refresh_mentor_profile_search_index(target_mentor_user_id UUID)
RETURNS VOID AS $$
BEGIN
    UPDATE mentor_profiles mp
    SET search_document = search_payload.document,
        search_vector = to_tsvector('simple', skillswap_normalize_search_text(search_payload.document))
    FROM (
        SELECT mp0.user_id,
               concat_ws(
                   ' ',
                   u.full_name,
                   mp0.headline,
                   mp0.expertise_description,
                   mp0.supporting_subjects,
                   sp.bio,
                   c.name,
                   ap.name_vi,
                   sz.name_vi,
                   mentor_tags_data.keyword_text,
                   mentor_services_data.keyword_text,
                   mentor_service_tags_data.keyword_text
               ) AS document
        FROM mentor_profiles mp0
        JOIN users u ON u.id = mp0.user_id
        LEFT JOIN student_profiles sp ON sp.user_id = mp0.user_id
        LEFT JOIN campuses c ON c.id = sp.campus_id
        LEFT JOIN academic_programs ap ON ap.id = sp.program_id
        LEFT JOIN specializations sz ON sz.id = sp.specialization_id
        LEFT JOIN LATERAL (
            SELECT string_agg(DISTINCT concat_ws(' ', t.name_vi, t.name_en, t.code), ' ') AS keyword_text
            FROM mentor_tags mt
            JOIN tags t ON t.id = mt.tag_id
            WHERE mt.mentor_user_id = mp0.user_id
        ) mentor_tags_data ON TRUE
        LEFT JOIN LATERAL (
            SELECT string_agg(DISTINCT concat_ws(' ', ms.title, ms.description, ms.expected_outcome), ' ') AS keyword_text
            FROM mentor_services ms
            WHERE ms.mentor_user_id = mp0.user_id
              AND ms.is_active = TRUE
        ) mentor_services_data ON TRUE
        LEFT JOIN LATERAL (
            SELECT string_agg(DISTINCT concat_ws(' ', t.name_vi, t.name_en, t.code), ' ') AS keyword_text
            FROM mentor_services ms
            JOIN mentor_service_help_topics msht ON msht.service_id = ms.id
            JOIN tags t ON t.id = msht.tag_id
            WHERE ms.mentor_user_id = mp0.user_id
              AND ms.is_active = TRUE
        ) mentor_service_tags_data ON TRUE
        WHERE mp0.user_id = target_mentor_user_id
    ) search_payload
    WHERE mp.user_id = search_payload.user_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION refresh_mentor_profiles_by_tag(target_tag_id UUID)
RETURNS VOID AS $$
DECLARE
    mentor_id UUID;
BEGIN
    FOR mentor_id IN
        SELECT DISTINCT mt.mentor_user_id
        FROM mentor_tags mt
        WHERE mt.tag_id = target_tag_id
        UNION
        SELECT DISTINCT ms.mentor_user_id
        FROM mentor_services ms
        JOIN mentor_service_help_topics msht ON msht.service_id = ms.id
        WHERE msht.tag_id = target_tag_id
    LOOP
        PERFORM refresh_mentor_profile_search_index(mentor_id);
    END LOOP;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_refresh_mentor_profile_search_from_profile()
RETURNS TRIGGER AS $$
BEGIN
    IF pg_trigger_depth() > 1 THEN
        RETURN NEW;
    END IF;
    PERFORM refresh_mentor_profile_search_index(NEW.user_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_refresh_mentor_profile_search_from_user()
RETURNS TRIGGER AS $$
BEGIN
    IF pg_trigger_depth() > 1 THEN
        RETURN NEW;
    END IF;
    IF EXISTS (SELECT 1 FROM mentor_profiles mp WHERE mp.user_id = NEW.id) THEN
        PERFORM refresh_mentor_profile_search_index(NEW.id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_refresh_mentor_profile_search_from_student_profile()
RETURNS TRIGGER AS $$
BEGIN
    IF pg_trigger_depth() > 1 THEN
        RETURN NEW;
    END IF;
    IF EXISTS (SELECT 1 FROM mentor_profiles mp WHERE mp.user_id = NEW.user_id) THEN
        PERFORM refresh_mentor_profile_search_index(NEW.user_id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_refresh_mentor_profile_search_from_mentor_tag()
RETURNS TRIGGER AS $$
BEGIN
    IF pg_trigger_depth() > 1 THEN
        RETURN COALESCE(NEW, OLD);
    END IF;
    PERFORM refresh_mentor_profile_search_index(COALESCE(NEW.mentor_user_id, OLD.mentor_user_id));
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_refresh_mentor_profile_search_from_service()
RETURNS TRIGGER AS $$
BEGIN
    IF pg_trigger_depth() > 1 THEN
        RETURN COALESCE(NEW, OLD);
    END IF;
    PERFORM refresh_mentor_profile_search_index(COALESCE(NEW.mentor_user_id, OLD.mentor_user_id));
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_refresh_mentor_profile_search_from_service_tag()
RETURNS TRIGGER AS $$
DECLARE
    target_mentor_user_id UUID;
BEGIN
    IF pg_trigger_depth() > 1 THEN
        RETURN COALESCE(NEW, OLD);
    END IF;

    SELECT ms.mentor_user_id
    INTO target_mentor_user_id
    FROM mentor_services ms
    WHERE ms.id = COALESCE(NEW.service_id, OLD.service_id);

    IF target_mentor_user_id IS NOT NULL THEN
        PERFORM refresh_mentor_profile_search_index(target_mentor_user_id);
    END IF;

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_refresh_mentor_profile_search_from_tag()
RETURNS TRIGGER AS $$
BEGIN
    IF pg_trigger_depth() > 1 THEN
        RETURN NEW;
    END IF;
    PERFORM refresh_mentor_profiles_by_tag(NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_mentor_profiles_search_refresh ON mentor_profiles;
CREATE TRIGGER trg_mentor_profiles_search_refresh
    AFTER INSERT OR UPDATE OF headline, expertise_description, supporting_subjects
    ON mentor_profiles
    FOR EACH ROW
    EXECUTE FUNCTION trg_refresh_mentor_profile_search_from_profile();

DROP TRIGGER IF EXISTS trg_users_search_refresh ON users;
CREATE TRIGGER trg_users_search_refresh
    AFTER UPDATE OF full_name
    ON users
    FOR EACH ROW
    EXECUTE FUNCTION trg_refresh_mentor_profile_search_from_user();

DROP TRIGGER IF EXISTS trg_student_profiles_search_refresh ON student_profiles;
CREATE TRIGGER trg_student_profiles_search_refresh
    AFTER INSERT OR UPDATE OF bio, campus_id, program_id, specialization_id
    ON student_profiles
    FOR EACH ROW
    EXECUTE FUNCTION trg_refresh_mentor_profile_search_from_student_profile();

DROP TRIGGER IF EXISTS trg_mentor_tags_search_refresh ON mentor_tags;
CREATE TRIGGER trg_mentor_tags_search_refresh
    AFTER INSERT OR UPDATE OR DELETE
    ON mentor_tags
    FOR EACH ROW
    EXECUTE FUNCTION trg_refresh_mentor_profile_search_from_mentor_tag();

DROP TRIGGER IF EXISTS trg_mentor_services_search_refresh ON mentor_services;
CREATE TRIGGER trg_mentor_services_search_refresh
    AFTER INSERT OR UPDATE OR DELETE
    ON mentor_services
    FOR EACH ROW
    EXECUTE FUNCTION trg_refresh_mentor_profile_search_from_service();

DROP TRIGGER IF EXISTS trg_mentor_service_help_topics_search_refresh ON mentor_service_help_topics;
CREATE TRIGGER trg_mentor_service_help_topics_search_refresh
    AFTER INSERT OR DELETE
    ON mentor_service_help_topics
    FOR EACH ROW
    EXECUTE FUNCTION trg_refresh_mentor_profile_search_from_service_tag();

DROP TRIGGER IF EXISTS trg_tags_search_refresh ON tags;
CREATE TRIGGER trg_tags_search_refresh
    AFTER UPDATE OF name_vi, name_en, code
    ON tags
    FOR EACH ROW
    EXECUTE FUNCTION trg_refresh_mentor_profile_search_from_tag();

DO $$
DECLARE
    mentor_record RECORD;
BEGIN
    FOR mentor_record IN SELECT user_id FROM mentor_profiles LOOP
        PERFORM refresh_mentor_profile_search_index(mentor_record.user_id);
    END LOOP;
END;
$$;

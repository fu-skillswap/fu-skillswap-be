-- rollout: CONTRACT

-- The mentor search document/vector has no remaining query consumer. Remove
-- every writer before dropping the data it updates.
DROP TRIGGER IF EXISTS trg_mentor_profiles_fts ON mentor_profiles;
DROP TRIGGER IF EXISTS trg_mentor_profiles_search_refresh ON mentor_profiles;
DROP TRIGGER IF EXISTS trg_users_search_refresh ON users;
DROP TRIGGER IF EXISTS trg_student_profiles_search_refresh ON student_profiles;
DROP TRIGGER IF EXISTS trg_mentor_tags_search_refresh ON mentor_tags;
DROP TRIGGER IF EXISTS trg_mentor_services_search_refresh ON mentor_services;
DROP TRIGGER IF EXISTS trg_mentor_service_help_topics_search_refresh ON mentor_service_help_topics;
DROP TRIGGER IF EXISTS trg_tags_search_refresh ON tags;
DROP TRIGGER IF EXISTS trg_mentor_subject_results_search_refresh ON mentor_subject_results;
DROP TRIGGER IF EXISTS trg_mentor_featured_projects_search_refresh ON mentor_featured_projects;
DROP TRIGGER IF EXISTS trg_mentor_achievements_search_refresh ON mentor_achievements;

DROP INDEX IF EXISTS idx_mentor_profiles_search_vector;
ALTER TABLE mentor_profiles DROP COLUMN IF EXISTS search_document;
ALTER TABLE mentor_profiles DROP COLUMN IF EXISTS search_vector;

DROP FUNCTION IF EXISTS trg_refresh_mentor_profile_search_from_profile();
DROP FUNCTION IF EXISTS trg_refresh_mentor_profile_search_from_user();
DROP FUNCTION IF EXISTS trg_refresh_mentor_profile_search_from_student_profile();
DROP FUNCTION IF EXISTS trg_refresh_mentor_profile_search_from_mentor_tag();
DROP FUNCTION IF EXISTS trg_refresh_mentor_profile_search_from_service();
DROP FUNCTION IF EXISTS trg_refresh_mentor_profile_search_from_service_tag();
DROP FUNCTION IF EXISTS trg_refresh_mentor_profile_search_from_tag();
DROP FUNCTION IF EXISTS trg_refresh_mentor_profile_search_from_subject_result();
DROP FUNCTION IF EXISTS refresh_mentor_profiles_by_tag(UUID);
DROP FUNCTION IF EXISTS refresh_mentor_profile_search_index(UUID);
DROP FUNCTION IF EXISTS mentor_profiles_fts_update();
DROP FUNCTION IF EXISTS skillswap_normalize_search_text(TEXT);

-- Legacy targeting collections contain identifiers only and have no active
-- campaign consumers. Role and help-topic audiences remain intact.
DROP TABLE IF EXISTS campaign_audience_campus_ids;
DROP TABLE IF EXISTS campaign_audience_program_ids;
DROP TABLE IF EXISTS campaign_audience_specialization_ids;

ALTER TABLE forum_posts DROP CONSTRAINT IF EXISTS fk_forum_posts_author_program;
DROP INDEX IF EXISTS idx_forum_posts_status_program_activity_id;
ALTER TABLE forum_posts DROP COLUMN IF EXISTS author_program_id;

ALTER TABLE student_profiles DROP CONSTRAINT IF EXISTS fk_student_profiles_campus;
ALTER TABLE student_profiles DROP CONSTRAINT IF EXISTS fk_student_profiles_program;
ALTER TABLE student_profiles DROP CONSTRAINT IF EXISTS fk_student_profiles_spec;
DROP INDEX IF EXISTS idx_student_profiles_campus_id;
DROP INDEX IF EXISTS idx_student_profiles_program_id;
DROP INDEX IF EXISTS idx_student_profiles_spec_id;
ALTER TABLE student_profiles DROP COLUMN IF EXISTS campus_id;
ALTER TABLE student_profiles DROP COLUMN IF EXISTS program_id;
ALTER TABLE student_profiles DROP COLUMN IF EXISTS specialization_id;
ALTER TABLE student_profiles DROP COLUMN IF EXISTS semester;
ALTER TABLE student_profiles DROP COLUMN IF EXISTS intake_year;
ALTER TABLE student_profiles DROP COLUMN IF EXISTS is_alumni;
ALTER TABLE student_profiles DROP COLUMN IF EXISTS student_code;

DROP INDEX IF EXISTS idx_spec_tags_tag_id;
DROP TABLE IF EXISTS specialization_tags;
DROP INDEX IF EXISTS idx_specializations_program_active;
ALTER TABLE specializations DROP CONSTRAINT IF EXISTS fk_specializations_program;
DROP TABLE IF EXISTS specializations;
DROP TABLE IF EXISTS academic_programs;
DROP TABLE IF EXISTS campuses;

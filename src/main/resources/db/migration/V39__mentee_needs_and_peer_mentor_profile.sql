ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS foundation_support_level INTEGER,
    ADD COLUMN IF NOT EXISTS output_review_support_level INTEGER,
    ADD COLUMN IF NOT EXISTS direction_support_level INTEGER;

CREATE TABLE IF NOT EXISTS mentoring_questionnaire_versions (
    id UUID PRIMARY KEY,
    version_number INTEGER NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS mentoring_questionnaire_questions (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL,
    question_code VARCHAR(80) NOT NULL,
    question_type VARCHAR(40) NOT NULL,
    question_text TEXT NOT NULL,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_mqq_version FOREIGN KEY (version_id) REFERENCES mentoring_questionnaire_versions(id) ON DELETE CASCADE,
    CONSTRAINT uk_mqq_version_code UNIQUE (version_id, question_code),
    CONSTRAINT uk_mqq_version_order UNIQUE (version_id, display_order)
);

CREATE TABLE IF NOT EXISTS mentoring_questionnaire_options (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL,
    option_code VARCHAR(100) NOT NULL,
    option_label TEXT NOT NULL,
    display_order INTEGER NOT NULL,
    score_value INTEGER,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_mqo_question FOREIGN KEY (question_id) REFERENCES mentoring_questionnaire_questions(id) ON DELETE CASCADE,
    CONSTRAINT uk_mqo_question_code UNIQUE (question_id, option_code),
    CONSTRAINT uk_mqo_question_order UNIQUE (question_id, display_order)
);

CREATE TABLE IF NOT EXISTS mentoring_questionnaire_activation_history (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL,
    activated_by UUID,
    activated_at TIMESTAMP NOT NULL,
    deactivated_at TIMESTAMP,
    CONSTRAINT fk_mqa_version FOREIGN KEY (version_id) REFERENCES mentoring_questionnaire_versions(id),
    CONSTRAINT fk_mqa_activated_by FOREIGN KEY (activated_by) REFERENCES users(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mqa_single_active
    ON mentoring_questionnaire_activation_history ((deactivated_at IS NULL))
    WHERE deactivated_at IS NULL;

CREATE TABLE IF NOT EXISTS mentoring_questionnaire_answers (
    id UUID PRIMARY KEY,
    activation_id UUID NOT NULL,
    version_id UUID NOT NULL,
    user_id UUID NOT NULL,
    question_code VARCHAR(80) NOT NULL,
    option_code VARCHAR(100) NOT NULL,
    score_value INTEGER,
    answered_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_mqans_activation FOREIGN KEY (activation_id) REFERENCES mentoring_questionnaire_activation_history(id),
    CONSTRAINT fk_mqans_version FOREIGN KEY (version_id) REFERENCES mentoring_questionnaire_versions(id),
    CONSTRAINT fk_mqans_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_mqans_activation_user_question UNIQUE (activation_id, user_id, question_code)
);

CREATE INDEX IF NOT EXISTS idx_mqans_user_answered_at
    ON mentoring_questionnaire_answers (user_id, answered_at DESC);

CREATE TABLE IF NOT EXISTS mentor_subject_results (
    id UUID PRIMARY KEY,
    mentor_user_id UUID NOT NULL,
    subject_code VARCHAR(80) NOT NULL,
    subject_name VARCHAR(200),
    score_value NUMERIC(4,2) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_msr_mentor FOREIGN KEY (mentor_user_id) REFERENCES mentor_profiles(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_msr_mentor
    ON mentor_subject_results (mentor_user_id);

CREATE TABLE IF NOT EXISTS mentor_featured_projects (
    id UUID PRIMARY KEY,
    mentor_user_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    picture_file_id UUID,
    content TEXT,
    project_description TEXT,
    live_demo_url TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_mfp_mentor FOREIGN KEY (mentor_user_id) REFERENCES mentor_profiles(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_mfp_picture FOREIGN KEY (picture_file_id) REFERENCES files(id)
);

CREATE INDEX IF NOT EXISTS idx_mfp_mentor
    ON mentor_featured_projects (mentor_user_id);

CREATE TABLE IF NOT EXISTS mentor_achievements (
    id UUID PRIMARY KEY,
    mentor_user_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    award_description TEXT,
    achieved_at DATE,
    product_header VARCHAR(200),
    product_description TEXT,
    demo_url TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ma_mentor FOREIGN KEY (mentor_user_id) REFERENCES mentor_profiles(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ma_mentor
    ON mentor_achievements (mentor_user_id);

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
                   subject_results_data.keyword_text,
                   mentor_projects_data.keyword_text,
                   mentor_achievements_data.keyword_text,
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
            SELECT string_agg(DISTINCT concat_ws(' ', msr.subject_code, msr.subject_name, msr.score_value::TEXT), ' ') AS keyword_text
            FROM mentor_subject_results msr
            WHERE msr.mentor_user_id = mp0.user_id
        ) subject_results_data ON TRUE
        LEFT JOIN LATERAL (
            SELECT string_agg(DISTINCT concat_ws(' ', mfp.title, mfp.content, mfp.project_description, mfp.live_demo_url), ' ') AS keyword_text
            FROM mentor_featured_projects mfp
            WHERE mfp.mentor_user_id = mp0.user_id
        ) mentor_projects_data ON TRUE
        LEFT JOIN LATERAL (
            SELECT string_agg(DISTINCT concat_ws(' ', ma.title, ma.award_description, ma.product_header, ma.product_description, ma.demo_url), ' ') AS keyword_text
            FROM mentor_achievements ma
            WHERE ma.mentor_user_id = mp0.user_id
        ) mentor_achievements_data ON TRUE
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

CREATE OR REPLACE FUNCTION trg_refresh_mentor_profile_search_from_subject_result()
RETURNS TRIGGER AS $$
BEGIN
    IF pg_trigger_depth() > 1 THEN
        RETURN COALESCE(NEW, OLD);
    END IF;
    PERFORM refresh_mentor_profile_search_index(COALESCE(NEW.mentor_user_id, OLD.mentor_user_id));
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_mentor_subject_results_search_refresh ON mentor_subject_results;
CREATE TRIGGER trg_mentor_subject_results_search_refresh
    AFTER INSERT OR UPDATE OR DELETE
    ON mentor_subject_results
    FOR EACH ROW
    EXECUTE FUNCTION trg_refresh_mentor_profile_search_from_subject_result();

DROP TRIGGER IF EXISTS trg_mentor_featured_projects_search_refresh ON mentor_featured_projects;
CREATE TRIGGER trg_mentor_featured_projects_search_refresh
    AFTER INSERT OR UPDATE OR DELETE
    ON mentor_featured_projects
    FOR EACH ROW
    EXECUTE FUNCTION trg_refresh_mentor_profile_search_from_subject_result();

DROP TRIGGER IF EXISTS trg_mentor_achievements_search_refresh ON mentor_achievements;
CREATE TRIGGER trg_mentor_achievements_search_refresh
    AFTER INSERT OR UPDATE OR DELETE
    ON mentor_achievements
    FOR EACH ROW
    EXECUTE FUNCTION trg_refresh_mentor_profile_search_from_subject_result();

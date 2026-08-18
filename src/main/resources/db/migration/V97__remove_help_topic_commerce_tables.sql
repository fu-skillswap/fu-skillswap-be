-- rollout: CONTRACT

-- HelpTopic is no longer a commerce targeting dimension. The corresponding
-- collections have no owning JPA entities or application writers anymore.
DROP TABLE IF EXISTS coupon_applicable_help_topic_ids;
DROP TABLE IF EXISTS campaign_audience_help_topic_ids;

-- Rebuild the legacy search function before removing the unused service-tag join.
DROP FUNCTION IF EXISTS refresh_mentor_profiles_by_tag(UUID);
DROP FUNCTION IF EXISTS refresh_mentor_profile_search_index(UUID);
DROP TRIGGER IF EXISTS trg_mentor_service_help_topics_search_refresh ON mentor_service_help_topics;
DROP TABLE IF EXISTS mentor_service_help_topics;

-- Clean up obsolete tag associations and tag rows before applying constraints
DELETE FROM mentor_tags
WHERE tag_type NOT IN ('EXPERTISE');

DELETE FROM user_learning_goals
WHERE tag_id IN (
    SELECT id FROM tags
    WHERE type NOT IN (
        'MAJOR', 'SPECIALIZATION', 'TECH_SKILL', 'BUSINESS_SKILL',
        'LANGUAGE', 'CAREER', 'SOFT_SKILL', 'TOOL', 'INDUSTRY'
    )
);

DELETE FROM specialization_tags
WHERE tag_id IN (
    SELECT id FROM tags
    WHERE type NOT IN (
        'MAJOR', 'SPECIALIZATION', 'TECH_SKILL', 'BUSINESS_SKILL',
        'LANGUAGE', 'CAREER', 'SOFT_SKILL', 'TOOL', 'INDUSTRY'
    )
);

DELETE FROM mentor_tags
WHERE tag_id IN (
    SELECT id FROM tags
    WHERE type NOT IN (
        'MAJOR', 'SPECIALIZATION', 'TECH_SKILL', 'BUSINESS_SKILL',
        'LANGUAGE', 'CAREER', 'SOFT_SKILL', 'TOOL', 'INDUSTRY'
    )
);

UPDATE tags
SET parent_tag_id = NULL
WHERE parent_tag_id IN (
    SELECT id FROM tags
    WHERE type NOT IN (
        'MAJOR', 'SPECIALIZATION', 'TECH_SKILL', 'BUSINESS_SKILL',
        'LANGUAGE', 'CAREER', 'SOFT_SKILL', 'TOOL', 'INDUSTRY'
    )
);

DELETE FROM tags
WHERE type NOT IN (
    'MAJOR', 'SPECIALIZATION', 'TECH_SKILL', 'BUSINESS_SKILL',
    'LANGUAGE', 'CAREER', 'SOFT_SKILL', 'TOOL', 'INDUSTRY'
);

ALTER TABLE mentor_tags DROP CONSTRAINT IF EXISTS mentor_tags_tag_type_check;
ALTER TABLE mentor_tags
    ADD CONSTRAINT mentor_tags_tag_type_check CHECK (tag_type IN ('EXPERTISE'));

ALTER TABLE tags DROP CONSTRAINT IF EXISTS tags_type_check;
ALTER TABLE tags
    ADD CONSTRAINT tags_type_check CHECK (type IN (
        'MAJOR', 'SPECIALIZATION', 'TECH_SKILL', 'BUSINESS_SKILL',
        'LANGUAGE', 'CAREER', 'SOFT_SKILL', 'TOOL', 'INDUSTRY'
    ));

CREATE FUNCTION refresh_mentor_profile_search_index(target_mentor_user_id UUID)
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
                   mentor_services_data.keyword_text
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
        WHERE mp0.user_id = target_mentor_user_id
    ) search_payload
    WHERE mp.user_id = search_payload.user_id;
END;
$$ LANGUAGE plpgsql;

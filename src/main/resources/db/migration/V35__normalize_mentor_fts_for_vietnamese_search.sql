-- Normalize mentor discovery FTS to non-accented lowercase text.
-- This keeps PostgreSQL FTS aligned with the backend keyword normalization logic.

UPDATE mentor_profiles
SET search_vector = to_tsvector(
    'simple',
    translate(
        lower(
            coalesce(headline, '') || ' ' ||
            coalesce(expertise_description, '') || ' ' ||
            coalesce(supporting_subjects, '')
        ),
        'àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ',
        'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy'
    )
);

CREATE OR REPLACE FUNCTION mentor_profiles_fts_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector(
        'simple',
        translate(
            lower(
                coalesce(NEW.headline, '') || ' ' ||
                coalesce(NEW.expertise_description, '') || ' ' ||
                coalesce(NEW.supporting_subjects, '')
            ),
            'àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ',
            'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy'
        )
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- rollout: EXPAND
-- Keep all legacy academic columns and student_code intact.
ALTER TABLE student_profiles ADD COLUMN id UUID;
UPDATE student_profiles SET id = user_id WHERE id IS NULL;
ALTER TABLE student_profiles ALTER COLUMN id SET NOT NULL;
CREATE UNIQUE INDEX uq_student_profiles_id ON student_profiles(id);
ALTER TABLE student_profiles ADD COLUMN profile_type VARCHAR(30);
ALTER TABLE student_profiles ADD COLUMN institution_id UUID;
ALTER TABLE student_profiles ADD COLUMN custom_institution_name VARCHAR(200);
ALTER TABLE student_profiles ADD COLUMN custom_institution_province_id UUID;
ALTER TABLE student_profiles ADD COLUMN field_group_id UUID;
ALTER TABLE student_profiles ADD COLUMN major_name VARCHAR(200);
ALTER TABLE student_profiles ADD COLUMN enrollment_year INTEGER;
ALTER TABLE student_profiles ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE student_profiles ADD COLUMN onboarding_completed_at TIMESTAMP;
ALTER TABLE student_profiles ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
-- A campus reference alone is not evidence that the record belongs to a particular institution in the
-- expanded catalog. Preserve every legacy value and require a fresh profile selection instead.
UPDATE student_profiles SET onboarding_completed = FALSE WHERE onboarding_completed IS NULL OR onboarding_completed = TRUE;
ALTER TABLE student_profiles ALTER COLUMN student_code DROP NOT NULL;
ALTER TABLE student_profiles ADD CONSTRAINT ck_student_profiles_profile_type
    CHECK (profile_type IS NULL OR profile_type IN ('SCHOOL_STUDENT','UNIVERSITY_STUDENT','ALUMNI'));
ALTER TABLE student_profiles ADD CONSTRAINT ck_student_profiles_type_fields
    CHECK (profile_type IS NULL OR
      (profile_type='SCHOOL_STUDENT' AND institution_id IS NULL AND field_group_id IS NULL AND major_name IS NULL AND enrollment_year IS NULL AND graduation_year IS NULL) OR
      (profile_type='UNIVERSITY_STUDENT' AND graduation_year IS NULL) OR profile_type='ALUMNI');
ALTER TABLE student_profiles ADD CONSTRAINT ck_student_profiles_onboarding_shape
    CHECK (NOT onboarding_completed OR (profile_type IS NOT NULL AND (
      (profile_type='SCHOOL_STUDENT' AND custom_institution_name IS NOT NULL AND custom_institution_province_id IS NOT NULL
        AND institution_id IS NULL AND field_group_id IS NULL AND major_name IS NULL AND enrollment_year IS NULL AND graduation_year IS NULL)
      OR (profile_type='UNIVERSITY_STUDENT' AND field_group_id IS NOT NULL AND major_name IS NOT NULL AND graduation_year IS NULL
        AND ((institution_id IS NOT NULL AND custom_institution_name IS NULL AND custom_institution_province_id IS NULL)
          OR (institution_id IS NULL AND custom_institution_name IS NOT NULL AND custom_institution_province_id IS NOT NULL)))
      OR (profile_type='ALUMNI' AND field_group_id IS NOT NULL AND major_name IS NOT NULL AND graduation_year IS NOT NULL
        AND ((institution_id IS NOT NULL AND custom_institution_name IS NULL AND custom_institution_province_id IS NULL)
          OR (institution_id IS NULL AND custom_institution_name IS NOT NULL AND custom_institution_province_id IS NOT NULL)))
    )));
CREATE INDEX idx_student_profiles_institution ON student_profiles(institution_id);
CREATE INDEX idx_student_profiles_field_group ON student_profiles(field_group_id);

CREATE TABLE administrative_provinces (
 id UUID PRIMARY KEY, code VARCHAR(2) NOT NULL, name VARCHAR(120) NOT NULL, normalized_name VARCHAR(120) NOT NULL,
 unit_type VARCHAR(30) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, sort_order INTEGER NOT NULL,
 effective_from DATE NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uq_administrative_provinces_code UNIQUE(code)
);
CREATE INDEX idx_administrative_provinces_name ON administrative_provinces(normalized_name);

CREATE TABLE educational_institutions (
 id UUID PRIMARY KEY, slug VARCHAR(120) NOT NULL, name VARCHAR(200) NOT NULL, short_name VARCHAR(80), province_id UUID NOT NULL,
 institution_type VARCHAR(40) NOT NULL, normalized_name VARCHAR(200) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
 sort_order INTEGER NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uq_educational_institutions_slug UNIQUE(slug),
 CONSTRAINT uq_educational_institutions_name_province UNIQUE(normalized_name,province_id),
 CONSTRAINT fk_educational_institutions_province FOREIGN KEY(province_id) REFERENCES administrative_provinces(id)
);
CREATE INDEX idx_educational_institutions_province ON educational_institutions(province_id,active,sort_order);

CREATE TABLE education_field_groups (
 id UUID PRIMARY KEY, official_code VARCHAR(5) NOT NULL, name VARCHAR(150) NOT NULL, normalized_name VARCHAR(150) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, sort_order INTEGER NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uq_education_field_groups_official_code UNIQUE(official_code)
);
CREATE INDEX idx_education_field_groups_name ON education_field_groups(normalized_name);
CREATE TABLE education_field_group_aliases (
 field_group_id UUID NOT NULL, alias VARCHAR(150) NOT NULL,
 CONSTRAINT fk_education_field_alias_group FOREIGN KEY(field_group_id) REFERENCES education_field_groups(id) ON DELETE CASCADE
);
CREATE INDEX idx_student_profiles_custom_province ON student_profiles(custom_institution_province_id);
ALTER TABLE student_profiles ADD CONSTRAINT fk_student_profiles_institution FOREIGN KEY(institution_id) REFERENCES educational_institutions(id);
ALTER TABLE student_profiles ADD CONSTRAINT fk_student_profiles_custom_province FOREIGN KEY(custom_institution_province_id) REFERENCES administrative_provinces(id);
ALTER TABLE student_profiles ADD CONSTRAINT fk_student_profiles_field_group FOREIGN KEY(field_group_id) REFERENCES education_field_groups(id);

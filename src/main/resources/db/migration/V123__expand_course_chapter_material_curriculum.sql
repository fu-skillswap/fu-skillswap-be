-- rollout: EXPAND
-- Move the course curriculum from Chapter -> Lecture -> Resource to Chapter -> Material.
CREATE TABLE course_materials (
    id UUID PRIMARY KEY,
    chapter_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    material_type VARCHAR(16) NOT NULL,
    sort_order INT NOT NULL,
    is_previewable BOOLEAN NOT NULL DEFAULT FALSE,
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    storage_provider_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    bunny_video_id VARCHAR(64),
    bunny_library_id VARCHAR(64),
    thumbnail_url VARCHAR(500),
    duration_seconds INT,
    document_object_key VARCHAR(255),
    file_size_bytes BIGINT,
    upload_expires_at TIMESTAMPTZ,
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_course_materials_chapter FOREIGN KEY (chapter_id) REFERENCES course_chapters(id),
    CONSTRAINT uk_course_materials_chapter_sort UNIQUE (chapter_id, sort_order),
    CONSTRAINT chk_course_materials_type CHECK (material_type IN ('VIDEO', 'PDF'))
);

CREATE INDEX idx_course_materials_chapter_active
    ON course_materials(chapter_id, sort_order)
    WHERE deleted_at IS NULL;

CREATE TABLE course_material_progresses (
    id UUID PRIMARY KEY,
    student_user_id UUID NOT NULL,
    material_id UUID NOT NULL,
    watched_seconds INT NOT NULL DEFAULT 0,
    completion_percentage INT NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    last_accessed_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_course_material_progress_material FOREIGN KEY (material_id) REFERENCES course_materials(id),
    CONSTRAINT uk_course_material_progress_student UNIQUE (student_user_id, material_id)
);

CREATE INDEX idx_course_material_progress_student_course
    ON course_material_progresses(student_user_id, material_id);

ALTER TABLE courses ADD COLUMN IF NOT EXISTS total_materials INT NOT NULL DEFAULT 0;
ALTER TABLE course_progresses ADD COLUMN IF NOT EXISTS completed_materials INT NOT NULL DEFAULT 0;
ALTER TABLE course_progresses ADD COLUMN IF NOT EXISTS total_materials INT NOT NULL DEFAULT 0;
ALTER TABLE course_progresses ADD COLUMN IF NOT EXISTS last_studied_material_id UUID;
ALTER TABLE course_progresses ADD CONSTRAINT fk_course_progress_last_material
    FOREIGN KEY (last_studied_material_id) REFERENCES course_materials(id);

INSERT INTO course_materials (
    id, chapter_id, title, material_type, sort_order, is_previewable, is_published,
    storage_provider_type, status, bunny_video_id, bunny_library_id, thumbnail_url,
    duration_seconds, document_object_key, file_size_bytes, uploaded_by, uploaded_at,
    deleted_at, version
)
SELECT
    resource.id,
    lecture.chapter_id,
    resource.title,
    CASE WHEN resource.resource_type = 'VIDEO' THEN 'VIDEO' ELSE 'PDF' END,
    ROW_NUMBER() OVER (PARTITION BY lecture.chapter_id ORDER BY lecture.sort_order, resource.uploaded_at, resource.id),
    lecture.is_previewable,
    lecture.is_published,
    resource.storage_provider_type,
    resource.status,
    resource.bunny_video_id,
    resource.bunny_library_id,
    resource.thumbnail_url,
    resource.duration_seconds,
    resource.document_object_key,
    resource.file_size_bytes,
    resource.uploaded_by,
    resource.uploaded_at,
    resource.deleted_at,
    resource.version
FROM lecture_resources resource
JOIN course_lectures lecture ON lecture.id = resource.lecture_id;

INSERT INTO course_material_progresses (
    id, student_user_id, material_id, watched_seconds, completion_percentage, is_completed,
    completed_at, last_accessed_at, version, created_at, updated_at
)
SELECT
    progress.id,
    progress.student_user_id,
    selected_material.id,
    progress.watched_seconds,
    progress.completion_percentage,
    progress.is_completed,
    progress.completed_at,
    progress.last_accessed_at,
    progress.version,
    progress.created_at,
    progress.updated_at
FROM lecture_progresses progress
JOIN LATERAL (
    SELECT material.id
    FROM lecture_resources resource
    JOIN course_materials material ON material.id = resource.id
    WHERE resource.lecture_id = progress.lecture_id
    ORDER BY CASE WHEN material.material_type = 'VIDEO' THEN 0 ELSE 1 END, material.sort_order, material.id
    LIMIT 1
) selected_material ON TRUE;

UPDATE courses course_row
SET total_materials = (
    SELECT COUNT(*) FROM course_materials material
    JOIN course_chapters chapter ON chapter.id = material.chapter_id
    WHERE chapter.course_id = course_row.id AND material.deleted_at IS NULL AND material.is_published = TRUE
);

UPDATE course_progresses course_progress
SET total_materials = (
        SELECT COUNT(*) FROM course_materials material
        JOIN course_chapters chapter ON chapter.id = material.chapter_id
        WHERE chapter.course_id = course_progress.course_id
          AND material.deleted_at IS NULL AND material.is_published = TRUE AND chapter.is_published = TRUE
    ),
    completed_materials = (
        SELECT COUNT(*) FROM course_material_progresses material_progress
        JOIN course_materials material ON material.id = material_progress.material_id
        JOIN course_chapters chapter ON chapter.id = material.chapter_id
        WHERE material_progress.student_user_id = course_progress.student_user_id
          AND chapter.course_id = course_progress.course_id
          AND material_progress.is_completed = TRUE
          AND material.deleted_at IS NULL AND material.is_published = TRUE AND chapter.is_published = TRUE
    ),
    last_studied_material_id = (
        SELECT material_progress.material_id
        FROM course_material_progresses material_progress
        JOIN course_materials material ON material.id = material_progress.material_id
        JOIN course_chapters chapter ON chapter.id = material.chapter_id
        WHERE material_progress.student_user_id = course_progress.student_user_id
          AND chapter.course_id = course_progress.course_id
        ORDER BY material_progress.last_accessed_at DESC, material_progress.material_id DESC
        LIMIT 1
    );

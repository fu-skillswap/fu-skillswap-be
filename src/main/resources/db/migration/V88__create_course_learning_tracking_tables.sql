-- rollout: EXPAND
CREATE TABLE course_chapters (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    sort_order INT NOT NULL,
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_course_chapters_course FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT uk_course_chapters_sort UNIQUE (course_id, sort_order)
);

CREATE TABLE course_lectures (
    id UUID PRIMARY KEY,
    chapter_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    sort_order INT NOT NULL,
    duration_seconds INT NOT NULL DEFAULT 0,
    is_previewable BOOLEAN NOT NULL DEFAULT FALSE,
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_course_lectures_chapter FOREIGN KEY (chapter_id) REFERENCES course_chapters(id),
    CONSTRAINT uk_course_lectures_sort UNIQUE (chapter_id, sort_order)
);

CREATE TABLE course_progresses (
    id UUID PRIMARY KEY,
    student_user_id UUID NOT NULL,
    course_id UUID NOT NULL,
    completed_lectures INT NOT NULL DEFAULT 0,
    total_lectures INT NOT NULL DEFAULT 0,
    overall_percentage INT NOT NULL DEFAULT 0,
    last_studied_lecture_id UUID,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_course_progress_course FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_course_progress_last_lecture FOREIGN KEY (last_studied_lecture_id) REFERENCES course_lectures(id),
    CONSTRAINT uk_course_progress_student UNIQUE (student_user_id, course_id)
);

CREATE TABLE lecture_progresses (
    id UUID PRIMARY KEY,
    student_user_id UUID NOT NULL,
    lecture_id UUID NOT NULL,
    watched_seconds INT NOT NULL DEFAULT 0,
    completion_percentage INT NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP WITH TIME ZONE,
    last_accessed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_lecture_progress_lecture FOREIGN KEY (lecture_id) REFERENCES course_lectures(id),
    CONSTRAINT uk_lecture_progress_student UNIQUE (student_user_id, lecture_id)
);

CREATE TABLE lecture_resources (
    id UUID PRIMARY KEY,
    lecture_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    storage_provider_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    bunny_video_id VARCHAR(64),
    bunny_library_id VARCHAR(64),
    thumbnail_url VARCHAR(500),
    preview_url VARCHAR(500),
    duration_seconds INT,
    document_object_key VARCHAR(255),
    external_url VARCHAR(500),
    file_size_bytes BIGINT,
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_lecture_resources_lecture FOREIGN KEY (lecture_id) REFERENCES course_lectures(id)
);

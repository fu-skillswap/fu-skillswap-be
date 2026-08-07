-- rollout: EXPAND
CREATE TABLE courses (
    id UUID PRIMARY KEY,
    mentor_profile_id UUID NOT NULL,
    subject_code VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    max_students INT NOT NULL,
    total_sessions INT NOT NULL,
    price_scoin INT NOT NULL,
    reserved_count INT NOT NULL DEFAULT 0,
    confirmed_count INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    start_date DATE,
    end_date DATE,
    published_at TIMESTAMP WITH TIME ZONE,
    registration_open_at TIMESTAMP WITH TIME ZONE,
    registration_close_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_courses_mentor FOREIGN KEY (mentor_profile_id) REFERENCES mentor_profiles(user_id)
);

CREATE TABLE course_sessions (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    session_number INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    scheduled_start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    meeting_link VARCHAR(500),
    status VARCHAR(32) NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_course_sessions_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT uk_course_sessions_number UNIQUE (course_id, session_number),
    CONSTRAINT chk_course_sessions_time CHECK (scheduled_end_at > scheduled_start_at)
);

CREATE TABLE course_enrollments (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    student_user_id UUID NOT NULL,
    payment_order_id UUID,
    paid_amount_scoin INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    seat_reserved_until TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    enrolled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_course_enrollments_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_course_enrollments_student FOREIGN KEY (student_user_id) REFERENCES users(id),
    CONSTRAINT uk_course_enrollments_student UNIQUE (course_id, student_user_id)
);

CREATE TABLE course_enrollment_settlements (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL,
    course_session_id UUID NOT NULL,
    allocated_scoin INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'HELD',
    eligible_at TIMESTAMP WITH TIME ZONE,
    released_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_settlements_enrollment FOREIGN KEY (enrollment_id) REFERENCES course_enrollments(id) ON DELETE CASCADE,
    CONSTRAINT fk_settlements_session FOREIGN KEY (course_session_id) REFERENCES course_sessions(id) ON DELETE CASCADE
);

CREATE TABLE course_materials (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    course_session_id UUID,
    title VARCHAR(200) NOT NULL,
    material_type VARCHAR(32) NOT NULL,
    storage_provider_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    access_scope VARCHAR(32) NOT NULL DEFAULT 'COURSE_LEVEL',
    bunny_video_id VARCHAR(64),
    bunny_library_id VARCHAR(64),
    thumbnail_url VARCHAR(500),
    preview_url VARCHAR(500),
    duration_seconds INT,
    document_object_key VARCHAR(255),
    external_url VARCHAR(500),
    file_size_bytes BIGINT,
    available_from TIMESTAMP WITH TIME ZONE,
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_course_materials_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_course_materials_session FOREIGN KEY (course_session_id) REFERENCES course_sessions(id) ON DELETE SET NULL,
    CONSTRAINT fk_course_materials_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(id)
);

CREATE INDEX idx_course_materials_course_session ON course_materials(course_id, course_session_id);
CREATE INDEX idx_course_materials_bunny_video ON course_materials(bunny_library_id, bunny_video_id);

CREATE TABLE bunny_webhook_events (
    id UUID PRIMARY KEY,
    external_event_id VARCHAR(128),
    event_type VARCHAR(64) NOT NULL,
    video_id VARCHAR(64) NOT NULL,
    library_id VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_bunny_webhook_events_external_id UNIQUE (external_event_id)
);

CREATE INDEX idx_bunny_webhook_events_video ON bunny_webhook_events(library_id, video_id);

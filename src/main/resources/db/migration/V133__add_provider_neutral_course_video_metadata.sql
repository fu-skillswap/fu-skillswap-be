-- rollout: EXPAND
-- Foundation for the R2-backed course-video flow. The current Bunny columns
-- remain untouched so existing uploads and playback stay compatible.
ALTER TABLE course_materials
    ADD COLUMN IF NOT EXISTS video_object_key VARCHAR(500),
    ADD COLUMN IF NOT EXISTS video_content_type VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_course_materials_video_object_key
    ON course_materials(video_object_key)
    WHERE video_object_key IS NOT NULL;

-- Migration V145: Add YouTube video support to course_materials
ALTER TABLE course_materials
    ADD COLUMN IF NOT EXISTS youtube_video_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS youtube_url VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_course_materials_youtube_id
    ON course_materials(youtube_video_id)
    WHERE youtube_video_id IS NOT NULL;

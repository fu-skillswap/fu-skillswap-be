-- Add picture_file_id column to mentor_achievements table
ALTER TABLE mentor_achievements
    ADD COLUMN IF NOT EXISTS picture_file_id UUID,
    ADD CONSTRAINT fk_ma_picture FOREIGN KEY (picture_file_id) REFERENCES files(id) ON DELETE SET NULL;

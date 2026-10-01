-- rollout: EXPAND
-- Migration: Create system_app_versions table for Version Guard & Maintenance control
CREATE TABLE IF NOT EXISTS system_app_versions (
    id BIGSERIAL PRIMARY KEY,
    platform VARCHAR(32) NOT NULL UNIQUE,
    min_version_code INT NOT NULL DEFAULT 1,
    latest_version_code INT NOT NULL DEFAULT 1,
    is_maintenance BOOLEAN NOT NULL DEFAULT FALSE,
    update_url VARCHAR(512),
    maintenance_message VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed initial records for Android and iOS
INSERT INTO system_app_versions (platform, min_version_code, latest_version_code, is_maintenance, update_url, maintenance_message)
VALUES 
    ('android', 1, 1, FALSE, 'https://play.google.com/store/apps/details?id=com.fptu.exe.skillswap', NULL),
    ('ios', 1, 1, FALSE, 'https://apps.apple.com/app/id000000000', NULL)
ON CONFLICT (platform) DO NOTHING;

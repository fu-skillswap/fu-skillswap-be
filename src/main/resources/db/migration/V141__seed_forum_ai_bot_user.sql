-- rollout: EXPAND
-- Seed fixed AI Bot user for forum auto-answering and mentor recommendation
INSERT INTO users (
    id,
    email,
    full_name,
    avatar_url,
    gender,
    status,
    created_at,
    updated_at
)
VALUES (
    '019f0000-0000-7000-8000-0000000000a1',
    'ai-bot@skillswap.asia',
    'SkillSwap AI',
    'https://cdn.skillswap.asia/static/ai-bot-avatar.png',
    'OTHER',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    avatar_url = EXCLUDED.avatar_url,
    status = 'ACTIVE';

INSERT INTO user_roles (user_id, role)
VALUES ('019f0000-0000-7000-8000-0000000000a1', 'MENTEE')
ON CONFLICT (user_id, role) DO NOTHING;

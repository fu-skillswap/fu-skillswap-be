-- rollout: EXPAND
-- Migration V142: Seed 70.000 VND welcome coupon for first 60 mentees
INSERT INTO coupons (
    id,
    code,
    title,
    description,
    discount_type,
    discount_value,
    max_discount_scoin,
    min_order_value_scoin,
    quota_total,
    quota_per_user,
    status,
    start_at,
    end_at,
    created_at,
    updated_at
) VALUES (
    '01952340-0000-7000-8000-000000000070',
    'WELCOME70K',
    'Ưu đãi chào mừng thành viên mới (Giới hạn 60 suất)',
    'Giảm 70.000 VNĐ cho 60 tài khoản mentee đầu tiên khi đặt lịch dịch vụ.',
    'FIXED',
    70000,
    NULL,
    0,
    60,
    1,
    'ACTIVE',
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (code) DO NOTHING;

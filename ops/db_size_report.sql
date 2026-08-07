-- ST-01: Báo cáo Kích thước Database (PostgreSQL)
-- Chạy script này để đánh giá dung lượng sử dụng của các bảng trên VPS 80GB.

-- 1. Tổng kích thước toàn bộ Database
SELECT
    current_database() AS db_name,
    pg_size_pretty(pg_database_size(current_database())) AS total_size;

-- 2. Kích thước Top 20 bảng lớn nhất (bao gồm cả TOAST và Index)
SELECT
    relname AS table_name,
    pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
    pg_size_pretty(pg_relation_size(relid)) AS data_size,
    pg_size_pretty(pg_indexes_size(relid)) AS index_size,
    pg_size_pretty(pg_total_relation_size(relid) - pg_relation_size(relid) - pg_indexes_size(relid)) AS toast_size
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC
LIMIT 20;

-- 3. Số lượng bản ghi ước tính của các bảng (rất nhanh, dùng metadata)
SELECT
    relname AS table_name,
    n_live_tup AS estimated_rows
FROM pg_stat_user_tables
ORDER BY n_live_tup DESC
LIMIT 20;

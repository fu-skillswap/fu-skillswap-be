-- Read-only PostgreSQL storage and bloat report.
SELECT current_database() AS database_name,
       pg_size_pretty(pg_database_size(current_database())) AS database_size,
       pg_database_size(current_database()) AS database_size_bytes;

SELECT n.nspname AS schema_name,
       c.relname AS table_name,
       pg_total_relation_size(c.oid) AS total_bytes,
       pg_table_size(c.oid) AS table_and_toast_bytes,
       pg_indexes_size(c.oid) AS index_bytes,
       c.reltuples::bigint AS estimated_rows,
       COALESCE(s.n_dead_tup, 0) AS dead_tuples,
       s.last_autovacuum,
       s.last_autoanalyze,
       c.reloptions
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
LEFT JOIN pg_stat_all_tables s ON s.relid = c.oid
WHERE c.relkind IN ('r', 'm')
  AND n.nspname NOT IN ('pg_catalog', 'information_schema')
ORDER BY pg_total_relation_size(c.oid) DESC;

SELECT schemaname,
       relname AS table_name,
       n_live_tup,
       n_dead_tup,
       CASE WHEN n_live_tup = 0 THEN 0 ELSE round(100.0 * n_dead_tup / n_live_tup, 2) END AS dead_tuple_percent,
       last_autovacuum,
       last_autoanalyze,
       vacuum_count,
       autovacuum_count,
       analyze_count,
       autoanalyze_count
FROM pg_stat_all_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
ORDER BY n_dead_tup DESC;

SELECT schemaname, relname AS table_name, reloptions
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE c.relkind = 'r'
  AND n.nspname NOT IN ('pg_catalog', 'information_schema')
  AND (relname IN ('messages', 'notifications', 'audit_logs', 'internal_telemetry_events'));

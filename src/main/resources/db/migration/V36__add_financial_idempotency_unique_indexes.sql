-- Harden financial idempotency at the database layer.
-- Note: credit_ledger_entries includes origin_type in the unique key because
-- one payment order can legitimately reserve multiple credit origins in parallel.

WITH ranked_credit_entries AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY account_id, source_type, source_id, entry_type, origin_type
               ORDER BY created_at ASC, id ASC
           ) AS rn
    FROM credit_ledger_entries
    WHERE source_id IS NOT NULL
)
DELETE FROM credit_ledger_entries entry
USING ranked_credit_entries ranked
WHERE entry.id = ranked.id
  AND ranked.rn > 1;

WITH ranked_settlement_entries AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY account_id, source_type, source_id, entry_type
               ORDER BY created_at ASC, id ASC
           ) AS rn
    FROM settlement_entries
    WHERE source_id IS NOT NULL
)
DELETE FROM settlement_entries entry
USING ranked_settlement_entries ranked
WHERE entry.id = ranked.id
  AND ranked.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_credit_ledger_entries_source_entry_origin
    ON credit_ledger_entries(account_id, source_type, source_id, entry_type, origin_type)
    WHERE source_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_settlement_entries_source_entry
    ON settlement_entries(account_id, source_type, source_id, entry_type)
    WHERE source_id IS NOT NULL;

ALTER TABLE credit_ledger_accounts ADD COLUMN balance INT NOT NULL DEFAULT 0;
ALTER TABLE settlement_accounts ADD COLUMN balance NUMERIC(19,4) NOT NULL DEFAULT 0;

-- Tính toán lại số dư hiện tại từ các entries
UPDATE credit_ledger_accounts a
SET balance = COALESCE((SELECT SUM(balance_effect_scoin) FROM credit_ledger_entries e WHERE e.account_id = a.id), 0);

UPDATE settlement_accounts a
SET balance = COALESCE((SELECT SUM(balance_effect_scoin) FROM settlement_entries e WHERE e.account_id = a.id), 0);

-- Gài chốt chặn SQL để bảo vệ an toàn tài chính tuyệt đối
ALTER TABLE credit_ledger_accounts ADD CONSTRAINT chk_credit_ledger_balance_non_negative CHECK (balance >= 0);
ALTER TABLE settlement_accounts ADD CONSTRAINT chk_settlement_balance_non_negative CHECK (balance >= 0);

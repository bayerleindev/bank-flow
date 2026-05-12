ALTER TABLE account_balances
ADD COLUMN IF NOT EXISTS digital_account_id UUID;

UPDATE account_balances
SET digital_account_id = '00000000-0000-0000-0000-000000000000'
WHERE digital_account_id IS NULL;

ALTER TABLE account_balances
ALTER COLUMN digital_account_id SET NOT NULL;

ALTER TABLE account_balance_entries
ADD COLUMN IF NOT EXISTS digital_account_id UUID;

ALTER TABLE account_balance_entries
ADD COLUMN IF NOT EXISTS ledger_account_id BIGINT;

UPDATE account_balance_entries
SET digital_account_id = '00000000-0000-0000-0000-000000000000'
WHERE digital_account_id IS NULL;

UPDATE account_balance_entries
SET ledger_account_id = account_id
WHERE ledger_account_id IS NULL;

ALTER TABLE account_balance_entries
ALTER COLUMN digital_account_id SET NOT NULL;

ALTER TABLE account_balance_entries
ALTER COLUMN ledger_account_id SET NOT NULL;

ALTER TABLE account_holds
ADD COLUMN IF NOT EXISTS digital_account_id UUID;

UPDATE account_holds
SET digital_account_id = '00000000-0000-0000-0000-000000000000'
WHERE digital_account_id IS NULL;

ALTER TABLE account_holds
ALTER COLUMN digital_account_id SET NOT NULL;

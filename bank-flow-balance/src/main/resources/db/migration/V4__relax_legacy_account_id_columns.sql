ALTER TABLE account_balances DROP CONSTRAINT IF EXISTS account_balances_pkey;

UPDATE account_balances
SET digital_account_id = (
    substr(md5('account_balances:' || account_id::text), 1, 8) || '-' ||
    substr(md5('account_balances:' || account_id::text), 9, 4) || '-' ||
    substr(md5('account_balances:' || account_id::text), 13, 4) || '-' ||
    substr(md5('account_balances:' || account_id::text), 17, 4) || '-' ||
    substr(md5('account_balances:' || account_id::text), 21, 12)
)::uuid
WHERE digital_account_id = '00000000-0000-0000-0000-000000000000'
  AND account_id IS NOT NULL;

UPDATE account_balance_entries
SET digital_account_id = (
    substr(md5('account_balances:' || account_id::text), 1, 8) || '-' ||
    substr(md5('account_balances:' || account_id::text), 9, 4) || '-' ||
    substr(md5('account_balances:' || account_id::text), 13, 4) || '-' ||
    substr(md5('account_balances:' || account_id::text), 17, 4) || '-' ||
    substr(md5('account_balances:' || account_id::text), 21, 12)
)::uuid
WHERE digital_account_id = '00000000-0000-0000-0000-000000000000'
  AND account_id IS NOT NULL;

UPDATE account_holds
SET digital_account_id = (
    substr(md5('account_balances:' || account_id::text), 1, 8) || '-' ||
    substr(md5('account_balances:' || account_id::text), 9, 4) || '-' ||
    substr(md5('account_balances:' || account_id::text), 13, 4) || '-' ||
    substr(md5('account_balances:' || account_id::text), 17, 4) || '-' ||
    substr(md5('account_balances:' || account_id::text), 21, 12)
)::uuid
WHERE digital_account_id = '00000000-0000-0000-0000-000000000000'
  AND account_id IS NOT NULL;

ALTER TABLE account_balances ALTER COLUMN account_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_account_balances_digital_account_id
ON account_balances (digital_account_id);

ALTER TABLE account_balance_entries ALTER COLUMN account_id DROP NOT NULL;

ALTER TABLE account_holds ALTER COLUMN account_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_account_holds_digital_account_status
ON account_holds (digital_account_id, status);

CREATE INDEX IF NOT EXISTS idx_account_balance_entries_digital_account_occurred
ON account_balance_entries (digital_account_id, occurred_at DESC);

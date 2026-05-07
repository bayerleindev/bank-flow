ALTER TABLE transfers ADD COLUMN IF NOT EXISTS source_digital_account_id UUID;
ALTER TABLE transfers ADD COLUMN IF NOT EXISTS destination_digital_account_id UUID;

UPDATE transfers
SET source_digital_account_id = COALESCE(source_owner_id, '00000000-0000-0000-0000-000000000000')
WHERE source_digital_account_id IS NULL;

UPDATE transfers
SET destination_digital_account_id = COALESCE(destination_owner_id, '00000000-0000-0000-0000-000000000000')
WHERE destination_digital_account_id IS NULL;

ALTER TABLE transfers ALTER COLUMN source_digital_account_id SET NOT NULL;
ALTER TABLE transfers ALTER COLUMN destination_digital_account_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_transfers_source_digital_account_created
ON transfers (source_digital_account_id, created_at DESC);

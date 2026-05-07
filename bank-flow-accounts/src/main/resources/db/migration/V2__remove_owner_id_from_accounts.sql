ALTER TABLE accounts DROP CONSTRAINT IF EXISTS ux_accounts_owner_id;
ALTER TABLE accounts DROP COLUMN IF EXISTS owner_id;

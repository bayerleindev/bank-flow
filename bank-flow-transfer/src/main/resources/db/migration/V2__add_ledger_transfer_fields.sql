ALTER TABLE transfers ADD COLUMN source_owner_id UUID;
ALTER TABLE transfers ADD COLUMN source_account VARCHAR(128);
ALTER TABLE transfers ADD COLUMN destination_owner_id UUID;
ALTER TABLE transfers ADD COLUMN destination_account VARCHAR(128);

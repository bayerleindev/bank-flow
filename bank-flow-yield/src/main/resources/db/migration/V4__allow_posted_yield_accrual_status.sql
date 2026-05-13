ALTER TABLE account_yield_accruals
DROP CONSTRAINT IF EXISTS chk_account_yield_status;

ALTER TABLE account_yield_accruals
ADD CONSTRAINT chk_account_yield_status
CHECK (status IN ('PENDING', 'POSTING_REQUESTED', 'POSTED'));

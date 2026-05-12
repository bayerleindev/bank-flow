ALTER TABLE transfers
DROP CONSTRAINT IF EXISTS chk_transfers_status;

ALTER TABLE transfers
ADD CONSTRAINT chk_transfers_status CHECK (status IN (
    'RECEIVED',
    'HOLD_CREATED',
    'PSP_PENDING',
    'PSP_CONFIRMED',
    'POSTING_REQUESTED',
    'COMPLETED',
    'FAILED',
    'EXPIRED',
    'REVERSED'
));

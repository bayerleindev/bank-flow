CREATE TABLE transfers (
    transfer_id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    source_account_id BIGINT NOT NULL,
    destination_account_id BIGINT NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(512) NOT NULL,
    hold_id VARCHAR(36),
    psp_payment_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(128),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    CONSTRAINT ux_transfers_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_transfers_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT chk_transfers_currency_length CHECK (char_length(currency) = 3),
    CONSTRAINT chk_transfers_status CHECK (status IN (
        'RECEIVED',
        'HOLD_CREATED',
        'PSP_PENDING',
        'PSP_CONFIRMED',
        'POSTING_REQUESTED',
        'COMPLETED',
        'FAILED'
    ))
);

CREATE UNIQUE INDEX ux_transfers_psp_payment_id
ON transfers (psp_payment_id);

CREATE INDEX idx_transfers_source_account_created
ON transfers (source_account_id, created_at DESC);

CREATE INDEX idx_transfers_status_updated
ON transfers (status, updated_at);

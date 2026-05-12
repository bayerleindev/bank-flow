ALTER TABLE account_balances
ADD COLUMN held_minor BIGINT NOT NULL DEFAULT 0;

ALTER TABLE account_balances
ADD CONSTRAINT chk_account_balances_held_non_negative CHECK (held_minor >= 0);

CREATE TABLE account_holds (
    hold_id VARCHAR(36) PRIMARY KEY,
    transfer_id VARCHAR(128) NOT NULL,
    account_id BIGINT NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(128) NOT NULL,
    expires_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    CONSTRAINT chk_account_holds_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT chk_account_holds_currency_length CHECK (char_length(currency) = 3),
    CONSTRAINT chk_account_holds_status CHECK (status IN ('HELD', 'CAPTURED', 'RELEASED', 'EXPIRED'))
);

CREATE UNIQUE INDEX ux_account_holds_transfer_id
ON account_holds (transfer_id);

CREATE INDEX idx_account_holds_account_status
ON account_holds (account_id, status);

CREATE INDEX idx_account_holds_expires_at
ON account_holds (expires_at);

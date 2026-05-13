CREATE TABLE account_yield_accruals (
    accrual_id UUID PRIMARY KEY,
    reference_date DATE NOT NULL,
    digital_account_id UUID NOT NULL,
    base_balance_minor BIGINT NOT NULL,
    yield_amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    cdi_daily_rate_percent NUMERIC(18, 8) NOT NULL,
    yield_cdi_percentage NUMERIC(9, 4) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at BIGINT NOT NULL,
    CONSTRAINT ux_account_yield_accruals_account_date UNIQUE (digital_account_id, reference_date),
    CONSTRAINT chk_account_yield_base_balance_positive CHECK (base_balance_minor > 0),
    CONSTRAINT chk_account_yield_amount_positive CHECK (yield_amount_minor > 0),
    CONSTRAINT chk_account_yield_currency_length CHECK (char_length(currency) = 3),
    CONSTRAINT chk_account_yield_status CHECK (status IN ('PENDING', 'POSTING_REQUESTED'))
);

CREATE INDEX idx_account_yield_accruals_reference_date
ON account_yield_accruals (reference_date);

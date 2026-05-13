CREATE TABLE daily_cdi_yield_rates (
    reference_date DATE PRIMARY KEY,
    source VARCHAR(64) NOT NULL,
    source_url TEXT NOT NULL,
    raw_value VARCHAR(64) NOT NULL,
    cdi_daily_rate_percent NUMERIC(18, 8) NOT NULL,
    cdi_daily_factor NUMERIC(22, 12) NOT NULL,
    yield_cdi_percentage NUMERIC(9, 4) NOT NULL,
    effective_daily_factor NUMERIC(22, 12) NOT NULL,
    fetched_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    CONSTRAINT chk_daily_cdi_rate_non_negative CHECK (cdi_daily_rate_percent >= 0),
    CONSTRAINT chk_yield_cdi_percentage_positive CHECK (yield_cdi_percentage > 0),
    CONSTRAINT chk_cdi_daily_factor_positive CHECK (cdi_daily_factor > 0),
    CONSTRAINT chk_effective_daily_factor_positive CHECK (effective_daily_factor > 0)
);

CREATE INDEX idx_daily_cdi_yield_rates_created_at
ON daily_cdi_yield_rates (created_at DESC);

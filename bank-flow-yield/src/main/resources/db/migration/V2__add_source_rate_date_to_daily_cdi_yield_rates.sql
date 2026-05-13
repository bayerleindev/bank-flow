ALTER TABLE daily_cdi_yield_rates
ADD COLUMN IF NOT EXISTS source_rate_date DATE;

UPDATE daily_cdi_yield_rates
SET source_rate_date = reference_date
WHERE source_rate_date IS NULL;

ALTER TABLE daily_cdi_yield_rates
ALTER COLUMN source_rate_date SET NOT NULL;

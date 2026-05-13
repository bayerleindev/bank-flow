package br.com.bankflow.ledger.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record YieldAccrualEvent(
		@JsonProperty("accrual_id") UUID accrualId,
		@JsonProperty("reference_date") LocalDate referenceDate,
		@JsonProperty("digital_account_id") UUID digitalAccountId,
		@JsonProperty("base_balance_minor") long baseBalanceMinor,
		@JsonProperty("yield_amount_minor") long yieldAmountMinor,
		String currency,
		@JsonProperty("cdi_daily_rate_percent") BigDecimal cdiDailyRatePercent,
		@JsonProperty("yield_cdi_percentage") BigDecimal yieldCdiPercentage
) {
	public void validate() {
		if (accrualId == null) {
			throw new IllegalArgumentException("accrual_id is required");
		}
		if (referenceDate == null) {
			throw new IllegalArgumentException("reference_date is required");
		}
		if (digitalAccountId == null) {
			throw new IllegalArgumentException("digital_account_id is required");
		}
		if (baseBalanceMinor <= 0) {
			throw new IllegalArgumentException("base_balance_minor must be positive");
		}
		if (yieldAmountMinor <= 0) {
			throw new IllegalArgumentException("yield_amount_minor must be positive");
		}
		if (!"BRL".equals(currency)) {
			throw new IllegalArgumentException("currency must be BRL");
		}
		if (cdiDailyRatePercent == null) {
			throw new IllegalArgumentException("cdi_daily_rate_percent is required");
		}
		if (yieldCdiPercentage == null) {
			throw new IllegalArgumentException("yield_cdi_percentage is required");
		}
	}
}

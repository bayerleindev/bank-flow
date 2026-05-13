package br.com.bankflow.yielding.domain;

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
	public static YieldAccrualEvent from(AccountYieldAccrual accrual) {
		return new YieldAccrualEvent(
				accrual.accrualId(),
				accrual.referenceDate(),
				accrual.digitalAccountId(),
				accrual.baseBalanceMinor(),
				accrual.yieldAmountMinor(),
				accrual.currency(),
				accrual.cdiDailyRatePercent(),
				accrual.yieldCdiPercentage()
		);
	}
}

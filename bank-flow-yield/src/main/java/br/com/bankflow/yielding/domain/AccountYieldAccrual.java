package br.com.bankflow.yielding.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AccountYieldAccrual(
		UUID accrualId,
		LocalDate referenceDate,
		UUID digitalAccountId,
		long baseBalanceMinor,
		long yieldAmountMinor,
		String currency,
		BigDecimal cdiDailyRatePercent,
		BigDecimal yieldCdiPercentage,
		String status,
		long createdAt
) {
}

package br.com.bankflow.yielding.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CdiRate(
		LocalDate referenceDate,
		LocalDate sourceRateDate,
		String source,
		String sourceUrl,
		String rawValue,
		BigDecimal cdiDailyRatePercent,
		BigDecimal cdiDailyFactor,
		BigDecimal yieldCdiPercentage,
		BigDecimal effectiveDailyFactor,
		long fetchedAt,
		long createdAt
) {
}

package br.com.bankflow.yielding.repositories;

import br.com.bankflow.yielding.domain.AccountYieldAccrual;
import br.com.bankflow.yielding.domain.CdiRate;
import br.com.bankflow.yielding.domain.EligibleBalance;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcYieldRepository implements YieldRepository {
	private final JdbcTemplate jdbcTemplate;

	public JdbcYieldRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public CdiRate saveRateIfAbsent(CdiRate rate) {
		try {
			jdbcTemplate.update("""
					INSERT INTO daily_cdi_yield_rates (
						reference_date, source_rate_date, source, source_url, raw_value, cdi_daily_rate_percent, cdi_daily_factor,
						yield_cdi_percentage, effective_daily_factor, fetched_at, created_at
					) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""",
					Date.valueOf(rate.referenceDate()),
					Date.valueOf(rate.sourceRateDate()),
					rate.source(),
					rate.sourceUrl(),
					rate.rawValue(),
					rate.cdiDailyRatePercent(),
					rate.cdiDailyFactor(),
					rate.yieldCdiPercentage(),
					rate.effectiveDailyFactor(),
					rate.fetchedAt(),
					rate.createdAt()
			);
			return rate;
		} catch (DuplicateKeyException ignored) {
			return findRate(rate.referenceDate())
					.orElseThrow(() -> new IllegalStateException("existing CDI rate not found reference_date=%s".formatted(rate.referenceDate())));
		}
	}

	@Override
	public List<EligibleBalance> findEligibleBalances() {
		return jdbcTemplate.query("""
				SELECT digital_account_id, currency, posted_minor
				FROM public.account_balances
				WHERE currency = 'BRL'
				  AND posted_minor > 0
				""",
				(rs, rowNum) -> new EligibleBalance(
						(UUID) rs.getObject("digital_account_id"),
						rs.getString("currency"),
						rs.getLong("posted_minor")
				)
		);
	}

	@Override
	public Optional<AccountYieldAccrual> createAccrualIfAbsent(AccountYieldAccrual accrual) {
		try {
			jdbcTemplate.update("""
					INSERT INTO account_yield_accruals (
						accrual_id, reference_date, digital_account_id, base_balance_minor, yield_amount_minor,
						currency, cdi_daily_rate_percent, yield_cdi_percentage, status, created_at
					) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""",
					accrual.accrualId(),
					Date.valueOf(accrual.referenceDate()),
					accrual.digitalAccountId(),
					accrual.baseBalanceMinor(),
					accrual.yieldAmountMinor(),
					accrual.currency(),
					accrual.cdiDailyRatePercent(),
					accrual.yieldCdiPercentage(),
					accrual.status(),
					accrual.createdAt()
			);
			return Optional.of(accrual);
		} catch (DuplicateKeyException ignored) {
			return Optional.empty();
		}
	}

	@Override
	public void markPostingRequested(UUID accrualId) {
		jdbcTemplate.update("""
				UPDATE account_yield_accruals
				SET status = 'POSTING_REQUESTED'
				WHERE accrual_id = ?
				""", accrualId);
	}

	@Override
	public boolean markPosted(UUID accrualId) {
		int updated = jdbcTemplate.update("""
				UPDATE account_yield_accruals
				SET status = 'POSTED'
				WHERE accrual_id = ?
				  AND status <> 'POSTED'
				""", accrualId);
		return updated > 0;
	}

	@Override
	public boolean hasAccrualsFor(LocalDate referenceDate) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM account_yield_accruals
				WHERE reference_date = ?
				""", Long.class, Date.valueOf(referenceDate));
		return count != null && count > 0;
	}

	private Optional<CdiRate> findRate(LocalDate referenceDate) {
		List<CdiRate> rates = jdbcTemplate.query("""
				SELECT reference_date, source, source_url, raw_value, cdi_daily_rate_percent, cdi_daily_factor,
				       source_rate_date, yield_cdi_percentage, effective_daily_factor, fetched_at, created_at
				FROM daily_cdi_yield_rates
				WHERE reference_date = ?
				""",
				(rs, rowNum) -> new CdiRate(
						rs.getDate("reference_date").toLocalDate(),
						rs.getDate("source_rate_date").toLocalDate(),
						rs.getString("source"),
						rs.getString("source_url"),
						rs.getString("raw_value"),
						rs.getBigDecimal("cdi_daily_rate_percent"),
						rs.getBigDecimal("cdi_daily_factor"),
						rs.getBigDecimal("yield_cdi_percentage"),
						rs.getBigDecimal("effective_daily_factor"),
						rs.getLong("fetched_at"),
						rs.getLong("created_at")
				),
				Date.valueOf(referenceDate)
		);
		return rates.stream().findFirst();
	}
}

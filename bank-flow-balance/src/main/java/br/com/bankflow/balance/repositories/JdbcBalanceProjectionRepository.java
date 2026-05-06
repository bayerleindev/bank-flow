package br.com.bankflow.balance.repositories;

import br.com.bankflow.balance.domain.LedgerPostingCreatedEvent;
import br.com.bankflow.balance.domain.LedgerPostingCreatedLine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcBalanceProjectionRepository implements BalanceProjectionRepository {
	private final JdbcTemplate jdbcTemplate;

	public JdbcBalanceProjectionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean markProcessedIfAbsent(LedgerPostingCreatedEvent event, long processedAt) {
		int updated = jdbcTemplate.update("""
				INSERT INTO processed_ledger_entries (entry_id, external_id, processed_at)
				VALUES (?, ?, ?)
				ON CONFLICT (entry_id) DO NOTHING
				""", event.entryId(), event.externalId(), processedAt);
		return updated == 1;
	}

	@Override
	public void saveEntryLine(LedgerPostingCreatedEvent event, LedgerPostingCreatedLine line) {
		jdbcTemplate.update("""
				INSERT INTO account_balance_entries (
					line_id,
					entry_id,
					account_id,
					external_id,
					entry_type,
					direction,
					amount_minor,
					signed_amount_minor,
					currency,
					description,
					occurred_at,
					created_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				line.lineId(),
				event.entryId(),
				line.accountId(),
				event.externalId(),
				event.entryType(),
				line.direction(),
				line.amountMinor(),
				line.signedAmountMinor(),
				line.currency(),
				event.description(),
				event.occurredAt(),
				line.createdAt()
		);
	}

	@Override
	public void applyPostedBalance(LedgerPostingCreatedLine line, long updatedAt) {
		int updated = jdbcTemplate.update("""
				INSERT INTO account_balances (account_id, currency, posted_minor, updated_at)
				VALUES (?, ?, ?, ?)
				ON CONFLICT (account_id)
				DO UPDATE SET
					posted_minor = account_balances.posted_minor + EXCLUDED.posted_minor,
					updated_at = EXCLUDED.updated_at
				WHERE account_balances.currency = EXCLUDED.currency
				""",
				line.accountId(),
				line.currency(),
				line.signedAmountMinor(),
				updatedAt
		);
		if (updated != 1) {
			throw new IllegalStateException("account balance currency mismatch account_id=%d".formatted(line.accountId()));
		}
	}
}

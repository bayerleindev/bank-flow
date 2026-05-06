package br.com.bankflow.balance.repositories;

import br.com.bankflow.balance.domain.AccountBalance;
import br.com.bankflow.balance.domain.AccountStatementLine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcBalanceQueryRepository implements BalanceQueryRepository {
	private final JdbcTemplate jdbcTemplate;

	public JdbcBalanceQueryRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<AccountBalance> findBalance(long accountId) {
		List<AccountBalance> balances = jdbcTemplate.query("""
				SELECT account_id, currency, posted_minor, updated_at
				FROM account_balances
				WHERE account_id = ?
				""",
				(rs, rowNum) -> new AccountBalance(
						rs.getLong("account_id"),
						rs.getString("currency"),
						rs.getLong("posted_minor"),
						rs.getLong("updated_at")
				),
				accountId
		);
		return balances.stream().findFirst();
	}

	@Override
	public List<AccountStatementLine> findStatementLines(long accountId, int limit, StatementCursor cursor) {
		if (cursor == null) {
			return jdbcTemplate.query("""
					SELECT line_id, entry_id, account_id, external_id, entry_type, direction, amount_minor,
					       signed_amount_minor, currency, description, occurred_at, created_at
					FROM account_balance_entries
					WHERE account_id = ?
					ORDER BY occurred_at DESC, line_id DESC
					LIMIT ?
					""",
					this::mapStatementLine,
					accountId,
					limit
			);
		}
		return jdbcTemplate.query("""
				SELECT line_id, entry_id, account_id, external_id, entry_type, direction, amount_minor,
				       signed_amount_minor, currency, description, occurred_at, created_at
				FROM account_balance_entries
				WHERE account_id = ?
				  AND (occurred_at < ? OR (occurred_at = ? AND line_id < ?))
				ORDER BY occurred_at DESC, line_id DESC
				LIMIT ?
				""",
				this::mapStatementLine,
				accountId,
				cursor.occurredAt(),
				cursor.occurredAt(),
				cursor.lineId(),
				limit
		);
	}

	private AccountStatementLine mapStatementLine(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new AccountStatementLine(
				rs.getLong("line_id"),
				rs.getLong("entry_id"),
				rs.getLong("account_id"),
				rs.getString("external_id"),
				rs.getString("entry_type"),
				rs.getString("direction"),
				rs.getLong("amount_minor"),
				rs.getLong("signed_amount_minor"),
				rs.getString("currency"),
				rs.getString("description"),
				rs.getLong("occurred_at"),
				rs.getLong("created_at")
		);
	}
}

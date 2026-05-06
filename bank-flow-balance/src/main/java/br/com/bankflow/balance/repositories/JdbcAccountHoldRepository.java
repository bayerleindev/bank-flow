package br.com.bankflow.balance.repositories;

import br.com.bankflow.balance.domain.AccountHold;
import br.com.bankflow.balance.domain.AccountHoldStatus;
import br.com.bankflow.balance.domain.CreateAccountHoldCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAccountHoldRepository implements AccountHoldRepository {
	private final JdbcTemplate jdbcTemplate;

	public JdbcAccountHoldRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<AccountHold> findByTransferId(String transferId) {
		List<AccountHold> holds = jdbcTemplate.query("""
				SELECT hold_id, transfer_id, account_id, amount_minor, currency, status, reason,
				       expires_at, created_at, updated_at
				FROM account_holds
				WHERE transfer_id = ?
				""", this::mapHold, transferId);
		return holds.stream().findFirst();
	}

	@Override
	public Optional<AccountHold> findByHoldId(String holdId) {
		List<AccountHold> holds = jdbcTemplate.query("""
				SELECT hold_id, transfer_id, account_id, amount_minor, currency, status, reason,
				       expires_at, created_at, updated_at
				FROM account_holds
				WHERE hold_id = ?
				""", this::mapHold, holdId);
		return holds.stream().findFirst();
	}

	@Override
	public AccountHold createHeld(String holdId, CreateAccountHoldCommand command, long now) {
		jdbcTemplate.update("""
				INSERT INTO account_holds (
					hold_id, transfer_id, account_id, amount_minor, currency, status,
					reason, expires_at, created_at, updated_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				holdId,
				command.transferId(),
				command.accountId(),
				command.amountMinor(),
				command.currency(),
				AccountHoldStatus.HELD.name(),
				command.reason(),
				command.expiresAt(),
				now,
				now
		);
		return findByHoldId(holdId).orElseThrow();
	}

	@Override
	public boolean reserveBalance(long accountId, String currency, long amountMinor, long updatedAt) {
		int updated = jdbcTemplate.update("""
				UPDATE account_balances
				SET held_minor = held_minor + ?,
				    updated_at = ?
				WHERE account_id = ?
				  AND currency = ?
				  AND posted_minor - held_minor >= ?
				""",
				amountMinor,
				updatedAt,
				accountId,
				currency,
				amountMinor
		);
		return updated == 1;
	}

	@Override
	public boolean captureHeld(String holdId, long updatedAt) {
		return closeHeld(holdId, AccountHoldStatus.CAPTURED, updatedAt);
	}

	@Override
	public boolean releaseHeld(String holdId, long updatedAt) {
		return closeHeld(holdId, AccountHoldStatus.RELEASED, updatedAt);
	}

	private boolean closeHeld(String holdId, AccountHoldStatus targetStatus, long updatedAt) {
		int updatedBalance = jdbcTemplate.update("""
				UPDATE account_balances balance
				SET held_minor = balance.held_minor - hold.amount_minor,
				    updated_at = ?
				FROM account_holds hold
				WHERE hold.hold_id = ?
				  AND hold.status = 'HELD'
				  AND balance.account_id = hold.account_id
				  AND balance.currency = hold.currency
				""", updatedAt, holdId);
		if (updatedBalance != 1) {
			return false;
		}
		int updatedHold = jdbcTemplate.update("""
				UPDATE account_holds
				SET status = ?,
				    updated_at = ?
				WHERE hold_id = ?
				  AND status = 'HELD'
				""", targetStatus.name(), updatedAt, holdId);
		return updatedHold == 1;
	}

	private AccountHold mapHold(ResultSet rs, int rowNum) throws SQLException {
		return new AccountHold(
				rs.getString("hold_id"),
				rs.getString("transfer_id"),
				rs.getLong("account_id"),
				rs.getLong("amount_minor"),
				rs.getString("currency"),
				AccountHoldStatus.valueOf(rs.getString("status")),
				rs.getString("reason"),
				rs.getLong("expires_at"),
				rs.getLong("created_at"),
				rs.getLong("updated_at")
		);
	}
}

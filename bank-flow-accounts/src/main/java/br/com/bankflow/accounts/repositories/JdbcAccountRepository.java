package br.com.bankflow.accounts.repositories;

import br.com.bankflow.accounts.clients.baas.BaasAccountResponse;
import br.com.bankflow.accounts.domain.Account;
import br.com.bankflow.accounts.domain.AccountStatus;
import br.com.bankflow.accounts.domain.CreateAccountCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAccountRepository implements AccountRepository {
	private final JdbcTemplate jdbcTemplate;

	public JdbcAccountRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<Account> findByIdempotencyKey(String idempotencyKey) {
		return findBy("idempotency_key", idempotencyKey);
	}

	@Override
	public Optional<Account> findByDocumentNumber(String documentNumber) {
		return findBy("document_number", normalizeDocument(documentNumber));
	}

	@Override
	public Optional<Account> findByAccountId(UUID accountId) {
		List<Account> accounts = jdbcTemplate.query(selectSql() + " WHERE account_id = ?", this::mapAccount, accountId);
		return accounts.stream().findFirst();
	}

	@Override
	public Account create(UUID accountId, CreateAccountCommand command, long now) {
		jdbcTemplate.update("""
				INSERT INTO accounts (
					account_id, idempotency_key, full_name, document_number, email,
					mother_name, social_name, phone_number, birth_date, address, is_politically_exposed,
					currency, status, created_at, updated_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				accountId,
				command.idempotencyKey(),
				command.fullName(),
				normalizeDocument(command.documentNumber()),
				command.email().trim().toLowerCase(),
				command.motherName(),
				command.socialName(),
				command.phoneNumber(),
				Date.valueOf(command.birthDate()),
				command.address(),
				command.politicallyExposed(),
				"BRL",
				AccountStatus.RECEIVED.name(),
				now,
				now
		);
		return findByAccountId(accountId).orElseThrow();
	}

	@Override
	public Account updateFromBaas(UUID accountId, BaasAccountResponse response, AccountStatus status, String failureReason, long now) {
		jdbcTemplate.update("""
				UPDATE accounts
				SET baas_account_id = ?,
				    branch = ?,
				    account = ?,
				    currency = ?,
				    status = ?,
				    failure_reason = ?,
				    updated_at = ?
				WHERE account_id = ?
				""",
				response.baasAccountId(),
				response.branch(),
				response.account(),
				response.currency() == null ? "BRL" : response.currency(),
				status.name(),
				failureReason,
				now,
				accountId
		);
		return findByAccountId(accountId).orElseThrow();
	}

	private Optional<Account> findBy(String column, Object value) {
		List<Account> accounts = jdbcTemplate.query(selectSql() + " WHERE " + column + " = ?", this::mapAccount, value);
		return accounts.stream().findFirst();
	}

	private String selectSql() {
		return """
				SELECT account_id, idempotency_key, full_name, document_number, email,
				       mother_name, social_name, phone_number, birth_date, address, is_politically_exposed,
				       baas_account_id, branch, account, currency, status, failure_reason, created_at, updated_at
				FROM accounts
				""";
	}

	private Account mapAccount(ResultSet rs, int rowNum) throws SQLException {
		return new Account(
				(UUID) rs.getObject("account_id"),
				rs.getString("idempotency_key"),
				rs.getString("full_name"),
				rs.getString("document_number"),
				rs.getString("email"),
				rs.getString("mother_name"),
				rs.getString("social_name"),
				rs.getString("phone_number"),
				rs.getDate("birth_date").toLocalDate(),
				rs.getString("address"),
				rs.getBoolean("is_politically_exposed"),
				rs.getString("baas_account_id"),
				rs.getString("branch"),
				rs.getString("account"),
				rs.getString("currency"),
				AccountStatus.valueOf(rs.getString("status")),
				rs.getString("failure_reason"),
				rs.getLong("created_at"),
				rs.getLong("updated_at")
		);
	}

	private String normalizeDocument(String documentNumber) {
		return documentNumber == null ? null : documentNumber.replaceAll("\\D", "");
	}
}

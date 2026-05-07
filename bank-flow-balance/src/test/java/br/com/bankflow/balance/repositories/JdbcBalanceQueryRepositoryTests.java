package br.com.bankflow.balance.repositories;

import br.com.bankflow.balance.domain.AccountBalance;
import br.com.bankflow.balance.domain.AccountStatementLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Sql(statements = {
		"DELETE FROM account_holds",
		"DELETE FROM account_balance_entries",
		"DELETE FROM account_balances",
		"INSERT INTO account_balances (account_id, digital_account_id, currency, posted_minor, updated_at) VALUES (1001, '11111111-1111-1111-1111-111111111111', 'BRL', 12500, 1777777777000)",
			"INSERT INTO account_balance_entries (line_id, entry_id, account_id, digital_account_id, ledger_account_id, external_id, entry_type, direction, amount_minor, signed_amount_minor, currency, description, occurred_at, created_at) VALUES (1, 9, 1001, '11111111-1111-1111-1111-111111111111', 1001, 'transfer-0', 'TRANSFER', 'CREDIT', 5000, 5000, 'BRL', 'Transfer transfer-0', 100, 110)",
			"INSERT INTO account_balance_entries (line_id, entry_id, account_id, digital_account_id, ledger_account_id, external_id, entry_type, direction, amount_minor, signed_amount_minor, currency, description, occurred_at, created_at) VALUES (2, 10, 1001, '11111111-1111-1111-1111-111111111111', 1001, 'transfer-1', 'TRANSFER', 'CREDIT', 7500, 7500, 'BRL', 'Transfer transfer-1', 200, 210)",
			"INSERT INTO account_balance_entries (line_id, entry_id, account_id, digital_account_id, ledger_account_id, external_id, entry_type, direction, amount_minor, signed_amount_minor, currency, description, occurred_at, created_at) VALUES (3, 11, 1001, '11111111-1111-1111-1111-111111111111', 1001, 'transfer-2', 'TRANSFER', 'CREDIT', 1000, 1000, 'BRL', 'Transfer transfer-2', 200, 211)"
	})
class JdbcBalanceQueryRepositoryTests {
	private static final UUID DIGITAL_ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Autowired
	private JdbcBalanceQueryRepository repository;

	@Autowired
	@SuppressWarnings("unused")
	private JdbcTemplate jdbcTemplate;

	@Test
	void findsBalance() {
		Optional<AccountBalance> balance = repository.findBalance(DIGITAL_ACCOUNT_ID);

		assertTrue(balance.isPresent());
		assertEquals(DIGITAL_ACCOUNT_ID, balance.get().digitalAccountId());
		assertEquals(12_500L, balance.get().postedMinor());
	}

	@Test
	void findsStatementLinesOrderedByOccurrenceDescending() {
		List<AccountStatementLine> lines = repository.findStatementLines(DIGITAL_ACCOUNT_ID, 10, null);

		assertEquals(3, lines.size());
		assertEquals(3L, lines.getFirst().lineId());
		assertEquals(2L, lines.get(1).lineId());
		assertEquals(1L, lines.getLast().lineId());
	}

	@Test
	void filtersStatementByBeforeCursor() {
		List<AccountStatementLine> lines = repository.findStatementLines(
				DIGITAL_ACCOUNT_ID,
				10,
				new BalanceQueryRepository.StatementCursor(200L, 3L)
		);

		assertEquals(2, lines.size());
		assertEquals(2L, lines.getFirst().lineId());
		assertEquals(1L, lines.getLast().lineId());
	}
}

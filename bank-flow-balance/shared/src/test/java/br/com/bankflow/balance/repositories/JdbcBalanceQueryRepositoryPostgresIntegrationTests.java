package br.com.bankflow.balance.repositories;

import br.com.bankflow.balance.domain.AccountStatementLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class JdbcBalanceQueryRepositoryPostgresIntegrationTests {
	private static final UUID DIGITAL_ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("postgres:15-alpine")
	)
			.withDatabaseName("bank_flow")
			.withUsername("myuser")
			.withPassword("mysecretpassword");

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
	}

	@Autowired
	private JdbcBalanceQueryRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void readsStatementWithStableCursorFromPostgres() {
		insertFixture();

		List<AccountStatementLine> firstPage = repository.findStatementLines(DIGITAL_ACCOUNT_ID, 2, null);
		List<AccountStatementLine> secondPage = repository.findStatementLines(
				DIGITAL_ACCOUNT_ID,
				2,
				new BalanceQueryRepository.StatementCursor(
						firstPage.getLast().occurredAt(),
						firstPage.getLast().lineId()
				)
		);

		assertEquals(List.of(4L, 3L), firstPage.stream().map(AccountStatementLine::lineId).toList());
		assertEquals(List.of(2L, 1L), secondPage.stream().map(AccountStatementLine::lineId).toList());
	}

	private void insertFixture() {
		jdbcTemplate.update("DELETE FROM account_holds");
		jdbcTemplate.update("DELETE FROM account_balance_entries");
		jdbcTemplate.update("DELETE FROM account_balances");
		jdbcTemplate.update("""
				INSERT INTO account_balances (account_id, digital_account_id, currency, posted_minor, updated_at)
				VALUES (1001, ?, 'BRL', 12500, 1777777777000)
				""", DIGITAL_ACCOUNT_ID);
		insertLine(1L, 9L, 100L);
		insertLine(2L, 10L, 200L);
		insertLine(3L, 11L, 200L);
		insertLine(4L, 12L, 300L);
	}

	private void insertLine(long lineId, long entryId, long occurredAt) {
		jdbcTemplate.update("""
					INSERT INTO account_balance_entries (
						line_id, entry_id, account_id, digital_account_id, ledger_account_id, external_id, entry_type, direction, amount_minor,
						signed_amount_minor, currency, description, occurred_at, created_at
					) VALUES (?, ?, 1001, ?, 1001, ?, 'TRANSFER', 'CREDIT', 1000, 1000, 'BRL', 'Postgres IT', ?, ?)
					""",
				lineId,
				entryId,
				DIGITAL_ACCOUNT_ID,
				"postgres-it-" + lineId,
				occurredAt,
				occurredAt
		);
	}
}

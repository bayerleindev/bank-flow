package br.com.bankflow.ledger.repositories;

import br.com.bankflow.ledger.configs.ImmuDbClientFactory;
import br.com.bankflow.ledger.configs.ImmuDbConfig;
import br.com.bankflow.ledger.domain.LedgerEntry;
import br.com.bankflow.ledger.domain.LedgerEntryLine;
import br.com.bankflow.ledger.domain.LedgerPosting;
import io.codenotary.immudb4j.ImmuClient;
import io.codenotary.immudb4j.sql.SQLException;
import io.codenotary.immudb4j.sql.SQLQueryResult;
import io.codenotary.immudb4j.sql.SQLValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class ImmuDbLedgerPostingRepositoryIntegrationTests {
	private static final int IMMUDB_PORT = 3322;
	private static final String DATABASE = "defaultdb";
	private static final String USERNAME = "immudb";
	private static final String PASSWORD = "immudb";

	@Container
	private static final GenericContainer<?> IMMUDB = new GenericContainer<>(
			DockerImageName.parse("codenotary/immudb:latest")
	)
			.withExposedPorts(IMMUDB_PORT);

	private ImmuClient immuClient;
	private ImmuDbLedgerPostingRepository repository;

	@BeforeEach
	void setUp() throws SQLException {
		immuClient = ImmuClient.newBuilder()
				.withServerUrl(IMMUDB.getHost())
				.withServerPort(IMMUDB.getMappedPort(IMMUDB_PORT))
				.build();
		openSessionWithRetry();
		prepareSchema();
		resetSession();
		repository = new ImmuDbLedgerPostingRepository(clientFactory());
	}

	@AfterEach
	void tearDown() {
		closeClient();
	}

	@Test
	void insertsEntryAndLinesInOnePosting() throws Exception {
		TestIds ids = TestIds.create();
		LedgerPosting posting = posting(ids, "it-posting-" + UUID.randomUUID(), ids.entryId(), ids.firstLineId());

		boolean created = repository.saveIfNotExists(posting);

		assertTrue(created);
		assertEquals(1, countRows("SELECT entry_id FROM ledger_entries WHERE external_id = $1", posting.entry().externalId()));
		assertEquals(2, countRows("SELECT line_id FROM ledger_entry_lines WHERE entry_id = $1", posting.entry().entryId()));
	}

	@Test
	void doesNotInsertDuplicateExternalId() throws Exception {
		TestIds ids = TestIds.create();
		String externalId = "it-idempotent-" + UUID.randomUUID();
		LedgerPosting firstPosting = posting(ids, externalId, ids.entryId(), ids.firstLineId());
		LedgerPosting duplicatePosting = posting(ids, externalId, ids.entryId() + 10L, ids.firstLineId() + 10L);

		assertTrue(repository.saveIfNotExists(firstPosting));
		assertFalse(repository.saveIfNotExists(duplicatePosting));

		assertEquals(1, countRows("SELECT entry_id FROM ledger_entries WHERE external_id = $1", externalId));
		assertEquals(0, countRows("SELECT line_id FROM ledger_entry_lines WHERE entry_id = $1", duplicatePosting.entry().entryId()));
	}

	@Test
	void treatsExternalIdUniqueViolationAsAlreadyProcessed() throws Exception {
		TestIds ids = TestIds.create();
		String externalId = "it-unique-race-" + UUID.randomUUID();
		LedgerPosting firstPosting = posting(ids, externalId, ids.entryId(), ids.firstLineId());
		LedgerPosting racingPosting = posting(ids, externalId, ids.entryId() + 10L, ids.firstLineId() + 10L);

		assertTrue(repository.saveIfNotExists(firstPosting));
		repository = new RaceConditionLedgerPostingRepository(clientFactory());

		assertFalse(repository.saveIfNotExists(racingPosting));
		resetSession();
		repository = new ImmuDbLedgerPostingRepository(clientFactory());

		assertEquals(1, countRows("SELECT entry_id FROM ledger_entries WHERE external_id = $1", externalId));
		assertEquals(0, countRows("SELECT line_id FROM ledger_entry_lines WHERE entry_id = $1", racingPosting.entry().entryId()));
	}

	@Test
	void rollsBackEntryWhenLineInsertFails() throws Exception {
		TestIds ids = TestIds.create();
		LedgerPosting existingPosting = posting(ids, "it-existing-line-" + UUID.randomUUID(), ids.entryId(), ids.firstLineId());
		LedgerPosting failingPosting = posting(
				ids,
				"it-rollback-" + UUID.randomUUID(),
				ids.entryId() + 20L,
				ids.firstLineId()
		);

		assertTrue(repository.saveIfNotExists(existingPosting));
		assertThrows(ImmuDbPersistenceException.class, () -> repository.saveIfNotExists(failingPosting));
		resetSession();
		repository = new ImmuDbLedgerPostingRepository(clientFactory());

		assertEquals(0, countRows("SELECT entry_id FROM ledger_entries WHERE external_id = $1", failingPosting.entry().externalId()));
		assertEquals(0, countRows("SELECT line_id FROM ledger_entry_lines WHERE entry_id = $1", failingPosting.entry().entryId()));
	}

	private LedgerPosting posting(TestIds ids, String externalId, long entryId, long firstLineId) {
		LedgerEntry entry = new LedgerEntry(
				entryId,
				externalId,
				"TRANSFER",
				"POSTED",
				"Integration test transfer",
				ids.createdAt(),
				ids.createdAt(),
				0L,
				"{\"source\":\"integration-test\"}"
		);
		LedgerEntryLine debitLine = LedgerEntryLine.debit(
				firstLineId,
				entry.entryId(),
				ids.sourceAccountId(),
				1_500L,
				"BRL",
				"Integration test debit",
				ids.createdAt()
		);
		LedgerEntryLine creditLine = LedgerEntryLine.credit(
				firstLineId + 1L,
				entry.entryId(),
				ids.destinationAccountId(),
				1_500L,
				"BRL",
				"Integration test credit",
				ids.createdAt()
		);

		return LedgerPosting.of(entry, List.of(debitLine, creditLine));
	}

	private int countRows(String query, Object value) throws SQLException {
		boolean transactionStarted = false;
		SQLQueryResult result = null;
		try {
			immuClient.beginTransaction();
			transactionStarted = true;
			result = immuClient.sqlQuery(query, sqlValue(value));
			int count = 0;
			while (result.next()) {
				count++;
			}
			immuClient.commitTransaction();
			return count;
		} catch (SQLException | RuntimeException exception) {
			if (transactionStarted) {
				rollback();
			}
			throw exception;
		} finally {
			if (result != null) {
				result.close();
			}
		}
	}

	private SQLValue sqlValue(Object value) {
		if (value instanceof Long longValue) {
			return new SQLValue(longValue);
		}
		return new SQLValue(value.toString());
	}

	private void prepareSchema() throws SQLException {
		execDdl("""
				CREATE TABLE IF NOT EXISTS ledger_entries (
				    entry_id             INTEGER,
				    external_id          VARCHAR[128] NOT NULL,
				    entry_type           VARCHAR[64] NOT NULL,
				    status               VARCHAR[32] NOT NULL,
				    description          VARCHAR[512] NOT NULL,
				    occurred_at          INTEGER NOT NULL,
				    created_at           INTEGER NOT NULL,
				    reversal_of_entry_id INTEGER,
				    metadata             JSON[4096] NOT NULL,
				    PRIMARY KEY (entry_id)
				)
				""");
		execDdl("""
				CREATE TABLE IF NOT EXISTS ledger_entry_lines (
				    line_id             INTEGER,
				    entry_id            INTEGER NOT NULL,
				    account_id          INTEGER NOT NULL,
				    direction           VARCHAR[6] NOT NULL,
				    amount_minor        INTEGER NOT NULL,
				    signed_amount_minor INTEGER NOT NULL,
				    currency            VARCHAR[3] NOT NULL,
				    line_memo           VARCHAR[512] NOT NULL,
				    created_at          INTEGER NOT NULL,
				    PRIMARY KEY (line_id)
				)
				""");
		createIndexIfPossible("CREATE UNIQUE INDEX ON ledger_entries(external_id)");
		createIndexIfPossible("CREATE INDEX ON ledger_entry_lines(entry_id)");
	}

	private void execDdl(String statement) throws SQLException {
		boolean transactionStarted = false;
		try {
			immuClient.beginTransaction();
			transactionStarted = true;
			immuClient.sqlExec(statement);
			immuClient.commitTransaction();
		} catch (SQLException | RuntimeException exception) {
			if (transactionStarted) {
				rollback();
			}
			throw exception;
		}
	}

	private void createIndexIfPossible(String statement) {
		boolean transactionStarted = false;
		try {
			immuClient.beginTransaction();
			transactionStarted = true;
			immuClient.sqlExec(statement);
			immuClient.commitTransaction();
		} catch (SQLException | RuntimeException ignored) {
			if (transactionStarted) {
				rollback();
			}
		}
	}

	private void rollback() {
		try {
			immuClient.rollbackTransaction();
		} catch (SQLException | RuntimeException ignored) {
		}
	}

	private void closeClient() {
		if (immuClient == null) {
			return;
		}
		try {
			immuClient.closeSession();
		} catch (RuntimeException ignored) {
		}
		try {
			immuClient.shutdown();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	private ImmuDbClientFactory clientFactory() {
		ImmuDbConfig.ImmuDbProperties properties = new ImmuDbConfig.ImmuDbProperties();
		properties.setHost(IMMUDB.getHost());
		properties.setPort(IMMUDB.getMappedPort(IMMUDB_PORT));
		properties.setDatabase(DATABASE);
		properties.setUsername(USERNAME);
		properties.setPassword(PASSWORD);
		return new ImmuDbClientFactory(properties);
	}

	private void resetSession() {
		immuClient.closeSession();
		openSessionWithRetry();
	}

	private void openSessionWithRetry() {
		RuntimeException lastException = null;
		for (int attempt = 0; attempt < 20; attempt++) {
			try {
				immuClient.openSession(DATABASE, USERNAME, PASSWORD);
				return;
			} catch (RuntimeException exception) {
				lastException = exception;
				sleepBeforeRetry();
			}
		}
		throw lastException;
	}

	private void sleepBeforeRetry() {
		try {
			Thread.sleep(500L);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while waiting for immudb testcontainer", exception);
		}
	}

	private static class RaceConditionLedgerPostingRepository extends ImmuDbLedgerPostingRepository {
		private RaceConditionLedgerPostingRepository(ImmuDbClientFactory clientFactory) {
			super(clientFactory);
		}

		@Override
		boolean entryAlreadyExists(ImmuClient immuClient, String externalId) {
			return false;
		}
	}

	private record TestIds(
			long entryId,
			long firstLineId,
			long sourceAccountId,
			long destinationAccountId,
			long createdAt
	) {
		private static TestIds create() {
			long base = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
			return new TestIds(
					base,
					base + 100L,
					base + 200L,
					base + 300L,
					System.currentTimeMillis()
			);
		}
	}
}

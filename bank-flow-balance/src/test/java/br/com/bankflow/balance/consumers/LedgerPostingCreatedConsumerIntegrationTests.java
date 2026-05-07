package br.com.bankflow.balance.consumers;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
		"spring.kafka.listener.auto-startup=true",
		"bank-flow.kafka.topics.ledger-posting-created=ledger-posting-created-it",
		"spring.kafka.consumer.group-id=bank-flow-balance-it",
		"spring.kafka.consumer.auto-offset-reset=earliest",
		"spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
		"spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"
})
@Testcontainers
	class LedgerPostingCreatedConsumerIntegrationTests {
		private static final String TOPIC = "ledger-posting-created-it";
		private static final UUID SOURCE_DIGITAL_ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
		private static final UUID DESTINATION_DIGITAL_ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
			DockerImageName.parse("postgres:15-alpine")
	)
			.withDatabaseName("bank_flow")
			.withUsername("myuser")
			.withPassword("mysecretpassword");

	@Container
	private static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
			DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
	);

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
		registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@BeforeEach
	void setUp() throws Exception {
		createTopicIfNeeded();
		jdbcTemplate.update("DELETE FROM account_holds");
		jdbcTemplate.update("DELETE FROM account_balance_entries");
		jdbcTemplate.update("DELETE FROM processed_ledger_entries");
		jdbcTemplate.update("DELETE FROM account_balances");
	}

	@Test
	void consumesLedgerPostingAndProjectsBalancesIdempotently() throws Exception {
		String externalId = UUID.randomUUID().toString();
		String payload = ledgerPostingCreatedPayload(externalId);

		kafkaTemplate.send(TOPIC, externalId, payload).get();
		awaitProjection();

		assertEquals(1, count("SELECT COUNT(*) FROM processed_ledger_entries WHERE external_id = ?", externalId));
		assertEquals(2, count("SELECT COUNT(*) FROM account_balance_entries WHERE external_id = ?", externalId));
			assertEquals(-1500L, balance(SOURCE_DIGITAL_ACCOUNT_ID));
			assertEquals(1500L, balance(DESTINATION_DIGITAL_ACCOUNT_ID));

		kafkaTemplate.send(TOPIC, externalId, payload).get();
		Thread.sleep(500L);

		assertEquals(1, count("SELECT COUNT(*) FROM processed_ledger_entries WHERE external_id = ?", externalId));
		assertEquals(2, count("SELECT COUNT(*) FROM account_balance_entries WHERE external_id = ?", externalId));
			assertEquals(-1500L, balance(SOURCE_DIGITAL_ACCOUNT_ID));
			assertEquals(1500L, balance(DESTINATION_DIGITAL_ACCOUNT_ID));
	}

	private void createTopicIfNeeded() throws Exception {
		try (AdminClient adminClient = AdminClient.create(Map.of(
				"bootstrap.servers",
				KAFKA.getBootstrapServers()
		))) {
			adminClient.createTopics(java.util.List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
		} catch (Exception ignored) {
			// Topic creation is idempotent for the test setup.
		}
	}

	private void awaitProjection() throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		while (System.nanoTime() < deadline) {
			if (count("SELECT COUNT(*) FROM processed_ledger_entries") == 1) {
				return;
			}
			Thread.sleep(100L);
		}
		throw new AssertionError("ledger posting was not projected");
	}

	private int count(String sql, Object... args) {
		Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
		return count == null ? 0 : count;
	}

	private long balance(UUID digitalAccountId) {
		Long balance = jdbcTemplate.queryForObject(
				"SELECT posted_minor FROM account_balances WHERE digital_account_id = ?",
				Long.class,
				digitalAccountId
		);
		return balance == null ? 0L : balance;
	}

	private String ledgerPostingCreatedPayload(String externalId) {
		return """
				{
				  "entry_id": 9001,
				  "external_id": "%s",
				  "entry_type": "TRANSFER",
				  "status": "POSTED",
				  "description": "Integration transfer",
				  "occurred_at": 1778000000000,
				  "created_at": 1778000000100,
				  "reversal_of_entry_id": 0,
				  "metadata": "{}",
				  "lines": [
				    {
					      "line_id": 9101,
					      "entry_id": 9001,
					      "account_id": 1001,
					      "digital_account_id": "%s",
					      "direction": "DEBIT",
				      "amount_minor": 1500,
				      "signed_amount_minor": -1500,
				      "currency": "BRL",
				      "line_memo": "source",
				      "created_at": 1778000000100
				    },
				    {
					      "line_id": 9102,
					      "entry_id": 9001,
					      "account_id": 2002,
					      "digital_account_id": "%s",
					      "direction": "CREDIT",
				      "amount_minor": 1500,
				      "signed_amount_minor": 1500,
				      "currency": "BRL",
				      "line_memo": "destination",
				      "created_at": 1778000000100
				    }
				  ]
				}
					""".formatted(externalId, SOURCE_DIGITAL_ACCOUNT_ID, DESTINATION_DIGITAL_ACCOUNT_ID);
	}
}

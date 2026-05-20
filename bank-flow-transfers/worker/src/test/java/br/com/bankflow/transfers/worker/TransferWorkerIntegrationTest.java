package br.com.bankflow.transfers.worker;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.bankflow.transfers.shared.domain.TransferParty;
import br.com.bankflow.transfers.shared.kafka.AccountValidatedEvent;
import br.com.bankflow.transfers.shared.kafka.AccountValidationStatus;
import br.com.bankflow.transfers.shared.kafka.BalanceHeldEvent;
import br.com.bankflow.transfers.shared.kafka.BalanceHoldStatus;
import br.com.bankflow.transfers.shared.kafka.TransferRequestedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@SuppressWarnings({
    "PMD.TooManyMethods",
    "PMD.UnitTestContainsTooManyAsserts",
    "PMD.ExcessiveImports",
    "PMD.SignatureDeclareThrowsException",
    "PMD.DoNotUseThreads",
    "PMD.UseExplicitTypes",
    "PMD.AvoidBranchingStatementAsLastInLoop"
})
class TransferWorkerIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final UUID DEBIT_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID CREDIT_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final TransferParty DEBIT_PARTY =
            new TransferParty("13935893", "10000-1", "0001");
    private static final TransferParty EXTERNAL_CREDIT_PARTY =
            new TransferParty("260", "12345-6", "0001");
    private static final TransferParty INTERNAL_CREDIT_PARTY =
            new TransferParty("13935893", "20000-2", "0001");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("bank_flow_transfers")
                    .withUsername("backend")
                    .withPassword("backend");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

    private static HttpServer baasServer;
    private static final AtomicReference<String> BAAS_REQUEST_BODY = new AtomicReference<>();

    @BeforeAll
    static void startBaas() throws IOException {
        baasServer = HttpServer.create(new InetSocketAddress(0), 0);
        baasServer.createContext(
                "/pix/payments",
                exchange -> {
                    BAAS_REQUEST_BODY.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    byte[] response =
                            "{\"transferId\":\"00000000-0000-0000-0000-000000000202\",\"status\":\"REQUESTED\"}"
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(202, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        baasServer.start();
    }

    @AfterAll
    static void stopBaas() {
        baasServer.stop(0);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add(
                "spring.kafka.consumer.group-id", () -> "transfer-worker-it-" + UUID.randomUUID());
        registry.add(
                "app.baas.base-url", () -> "http://localhost:" + baasServer.getAddress().getPort());
        registry.add("app.baas.timeout", () -> "2s");
    }

    @Test
    void shouldRequestAccountValidationWhenTransferRequestedEventIsConsumed() throws Exception {
        UUID transferId = UUID.randomUUID();
        insertTransfer(transferId, "PIX_REQUESTED", EXTERNAL_CREDIT_PARTY, null, null);

        kafkaTemplate
                .send("transfer.requested", transferId.toString(), requestedEvent(transferId))
                .join();

        awaitStatus(transferId, "ACCOUNT_VALIDATION_REQUESTED");
        JsonNode command = readKafkaValue("account.validate.command", "worker-account-validate");
        assertThat(command.get("transferId").asText()).isEqualTo(transferId.toString());
        assertThat(command.get("debitAccountId").asText()).isEqualTo(DEBIT_ACCOUNT_ID.toString());
    }

    @Test
    void shouldPersistDebitPartySnapshotAndRequestBalanceHoldWhenAccountIsValidated()
            throws Exception {
        UUID transferId = UUID.randomUUID();
        insertTransfer(
                transferId, "ACCOUNT_VALIDATION_REQUESTED", EXTERNAL_CREDIT_PARTY, null, null);

        kafkaTemplate
                .send("account.validated.event", transferId.toString(), approvedEvent(transferId))
                .join();

        awaitStatus(transferId, "BALANCE_HOLD_REQUESTED");
        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "select debit_bank, debit_account, debit_branch from transfers.transfers where id = ?",
                        transferId);
        assertThat(row.get("debit_bank")).isEqualTo("13935893");
        assertThat(row.get("debit_account")).isEqualTo("10000-1");
        assertThat(
                        readKafkaValue("balance.hold.command", "worker-balance-hold")
                                .get("transferId")
                                .asText())
                .isEqualTo(transferId.toString());
    }

    @Test
    void shouldRejectTransferWhenAccountValidationIsRejected() {
        UUID transferId = UUID.randomUUID();
        insertTransfer(
                transferId, "ACCOUNT_VALIDATION_REQUESTED", EXTERNAL_CREDIT_PARTY, null, null);
        AccountValidatedEvent event =
                new AccountValidatedEvent(
                        transferId,
                        AccountValidationStatus.REJECTED,
                        "debit_account_not_found_or_inactive",
                        null,
                        null,
                        null,
                        Instant.now());

        kafkaTemplate.send("account.validated.event", transferId.toString(), event).join();

        awaitStatus(transferId, "REJECTED");
        assertThat(rejectionReason(transferId)).isEqualTo("debit_account_not_found_or_inactive");
    }

    @Test
    void shouldSendPixPaymentToBaasWhenBalanceIsHeldForExternalTransfer() throws Exception {
        UUID transferId = UUID.randomUUID();
        BAAS_REQUEST_BODY.set(null);
        insertTransfer(
                transferId, "BALANCE_HOLD_REQUESTED", EXTERNAL_CREDIT_PARTY, DEBIT_PARTY, null);

        kafkaTemplate
                .send(
                        "balance.held.event",
                        transferId.toString(),
                        heldEvent(transferId, BalanceHoldStatus.HELD, null))
                .join();

        awaitStatus(transferId, "BAAS_REQUESTED");
        JsonNode request = awaitBaasRequest();
        assertThat(request.get("transferId").asText()).isEqualTo(transferId.toString());
        assertThat(request.get("debitParty").get("bank").asText()).isEqualTo("13935893");
        assertThat(request.get("debitParty").get("account").asText()).isEqualTo("10000-1");
    }

    @Test
    void shouldCompleteInternalTransferWhenBalanceIsHeldForInternalTransfer() throws Exception {
        UUID transferId = UUID.randomUUID();
        insertTransfer(
                transferId,
                "BALANCE_HOLD_REQUESTED",
                INTERNAL_CREDIT_PARTY,
                DEBIT_PARTY,
                CREDIT_ACCOUNT_ID);

        kafkaTemplate
                .send(
                        "balance.held.event",
                        transferId.toString(),
                        heldEvent(transferId, BalanceHoldStatus.HELD, null))
                .join();

        awaitStatus(transferId, "COMPLETED");
        assertThat(readKafkaValue("movement.created", "worker-movement").get("transferId").asText())
                .isEqualTo(transferId.toString());
        assertThat(
                        readKafkaValue("balance.capture.command", "worker-capture")
                                .get("transferId")
                                .asText())
                .isEqualTo(transferId.toString());
    }

    @Test
    void shouldRejectTransferWhenBalanceHoldIsRejected() {
        UUID transferId = UUID.randomUUID();
        insertTransfer(
                transferId, "BALANCE_HOLD_REQUESTED", EXTERNAL_CREDIT_PARTY, DEBIT_PARTY, null);

        kafkaTemplate
                .send(
                        "balance.held.event",
                        transferId.toString(),
                        heldEvent(transferId, BalanceHoldStatus.REJECTED, "insufficient_balance"))
                .join();

        awaitStatus(transferId, "REJECTED");
        assertThat(rejectionReason(transferId)).isEqualTo("insufficient_balance");
    }

    private TransferRequestedEvent requestedEvent(UUID transferId) {
        return new TransferRequestedEvent(
                transferId,
                DEBIT_ACCOUNT_ID,
                EXTERNAL_CREDIT_PARTY,
                "E2E-" + transferId,
                1000,
                "pix",
                "BRL",
                br.com.bankflow.transfers.shared.domain.TransferType.PIX,
                "PIX_REQUESTED",
                Instant.now());
    }

    private AccountValidatedEvent approvedEvent(UUID transferId) {
        return new AccountValidatedEvent(
                transferId,
                AccountValidationStatus.APPROVED,
                null,
                DEBIT_ACCOUNT_ID,
                DEBIT_PARTY,
                null,
                Instant.now());
    }

    private BalanceHeldEvent heldEvent(UUID transferId, BalanceHoldStatus status, String reason) {
        return new BalanceHeldEvent(
                transferId, status, reason, DEBIT_ACCOUNT_ID, 1000, "BRL", Instant.now());
    }

    private void insertTransfer(
            UUID transferId,
            String status,
            TransferParty creditParty,
            TransferParty debitParty,
            UUID creditAccountId) {
        jdbcTemplate.update(
                """
				insert into transfers.transfers (
					id, debit_bank, debit_account, debit_branch, credit_bank, credit_account, credit_branch,
					idempotency_key, amount_minor, description, currency, type, status, rejection_reason,
					debit_account_id, credit_account_id, created_at, updated_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
                transferId,
                debitParty == null ? null : debitParty.bank(),
                debitParty == null ? null : debitParty.account(),
                debitParty == null ? null : debitParty.branch(),
                creditParty.bank(),
                creditParty.account(),
                creditParty.branch(),
                "E2E-" + transferId,
                1000,
                "pix",
                "BRL",
                "PIX",
                status,
                null,
                DEBIT_ACCOUNT_ID,
                creditAccountId,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
    }

    private JsonNode awaitBaasRequest() throws Exception {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            if (BAAS_REQUEST_BODY.get() != null) {
                return OBJECT_MAPPER.readTree(BAAS_REQUEST_BODY.get());
            }
            Thread.sleep(100);
        }
        throw new AssertionError("BAAS request was not received");
    }

    private void awaitStatus(UUID transferId, String expectedStatus) {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            if (expectedStatus.equals(status(transferId))) {
                return;
            }
            sleep();
        }
        throw new AssertionError("Transfer did not reach status " + expectedStatus);
    }

    private JsonNode readKafkaValue(String topic, String groupId) throws Exception {
        try (KafkaConsumer<String, String> consumer = kafkaConsumer(groupId)) {
            consumer.subscribe(java.util.List.of(topic));
            Instant deadline = Instant.now().plusSeconds(10);
            while (Instant.now().isBefore(deadline)) {
                for (var record : consumer.poll(Duration.ofMillis(250))) {
                    return OBJECT_MAPPER.readTree(record.value());
                }
            }
        }
        throw new AssertionError("No Kafka record received from " + topic);
    }

    private KafkaConsumer<String, String> kafkaConsumer(String groupId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private String status(UUID transferId) {
        return jdbcTemplate.queryForObject(
                "select status from transfers.transfers where id = ?", String.class, transferId);
    }

    private String rejectionReason(UUID transferId) {
        return jdbcTemplate.queryForObject(
                "select rejection_reason from transfers.transfers where id = ?",
                String.class,
                transferId);
    }

    private static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}

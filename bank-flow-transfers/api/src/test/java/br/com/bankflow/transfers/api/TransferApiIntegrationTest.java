package br.com.bankflow.transfers.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.bankflow.transfers.api.dto.response.PixAccountResponse;
import br.com.bankflow.transfers.api.dto.response.PixKeyResponse;
import br.com.bankflow.transfers.api.dto.response.PixOwnerResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings({
    "PMD.TooManyMethods",
    "PMD.UnitTestContainsTooManyAsserts",
    "PMD.ExcessiveImports",
    "PMD.CouplingBetweenObjects",
    "PMD.SignatureDeclareThrowsException",
    "PMD.LambdaCanBeMethodReference",
    "PMD.UseExplicitTypes",
    "PMD.AvoidBranchingStatementAsLastInLoop"
})
class TransferApiIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ISSUER = "http://bank-flow-auth.test";
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID EXTERNAL_SETTLEMENT_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BAAS_DICT_END_TO_END_ID = "E2E-dict-lookup";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("bank_flow_transfers")
                    .withUsername("backend")
                    .withPassword("backend");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @LocalServerPort private int port;

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static RsaFixture rsaFixture;
    private static HttpServer baasServer;
    private static final AtomicReference<String> BAAS_DICT_QUERY = new AtomicReference<>();

    @BeforeAll
    static void startExternalServers() throws Exception {
        rsaFixture = RsaFixture.start();
        baasServer = HttpServer.create(new InetSocketAddress(0), 0);
        baasServer.createContext(
                "/dict",
                exchange -> {
                    BAAS_DICT_QUERY.set(exchange.getRequestURI().getRawQuery());
                    byte[] body =
                            """
							{
							  "account": {
							    "bank": "260",
							    "account": "12345-6",
							    "branch": "0001"
							  },
							  "owner": {
							    "name": "Ada",
							    "maskedDocument": "***.123.456-**"
							  },
							  "endToEndId": "E2E-dict-lookup"
							}
							"""
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        baasServer.start();
    }

    @AfterAll
    static void stopExternalServers() {
        rsaFixture.stop();
        baasServer.stop(0);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> rsaFixture.jwksUri());
        registry.add(
                "app.ledger.external-settlement-account-id",
                () -> EXTERNAL_SETTLEMENT_ACCOUNT_ID.toString());
        registry.add(
                "app.baas.base-url", () -> "http://localhost:" + baasServer.getAddress().getPort());
        registry.add("app.baas.timeout", () -> "2s");
    }

    @Test
    void shouldCreateTransferWhenBearerTokenIsValidAndPixKeyCacheMatches() throws Exception {
        String endToEndId = "E2E-create";
        cachePixKey(endToEndId, "260", "12345-6", "0001");

        HttpResponse<String> response =
                post("/transfers", transferRequest("260", "12345-6", "0001"), endToEndId, token());

        assertThat(response.statusCode()).isEqualTo(201);
        UUID transferId = UUID.fromString(json(response).get("id").asText());
        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "select debit_account_id, debit_bank, debit_account, debit_branch from transfers.transfers where id = ?",
                        transferId);
        assertThat(row.get("debit_account_id")).isEqualTo(ACCOUNT_ID);
        assertThat(row.get("debit_bank")).isNull();
        assertThat(
                        readKafkaValue("transfer.requested", "api-create")
                                .get("debitAccountId")
                                .asText())
                .isEqualTo(ACCOUNT_ID.toString());
    }

    @Test
    void shouldNotCreateTransferWithoutBearerToken() throws Exception {
        String endToEndId = "E2E-no-token";
        cachePixKey(endToEndId, "260", "12345-6", "0001");

        HttpResponse<String> response =
                post("/transfers", transferRequest("260", "12345-6", "0001"), endToEndId, null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(countTransfersByIdempotencyKey(endToEndId)).isZero();
    }

    @Test
    void shouldNotCreateTransferWhenJwtSignatureIsInvalid() throws Exception {
        String endToEndId = "E2E-bad-signature";
        cachePixKey(endToEndId, "260", "12345-6", "0001");

        RsaFixture invalidFixture = RsaFixture.start();
        HttpResponse<String> response =
                post(
                        "/transfers",
                        transferRequest("260", "12345-6", "0001"),
                        endToEndId,
                        invalidFixture.token(ACCOUNT_ID));
        invalidFixture.stop();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(countTransfersByIdempotencyKey(endToEndId)).isZero();
    }

    @Test
    void shouldNotCreateTransferWhenPixKeyCacheIsMissing() throws Exception {
        String endToEndId = "E2E-missing-cache";

        HttpResponse<String> response =
                post("/transfers", transferRequest("260", "12345-6", "0001"), endToEndId, token());

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(countTransfersByIdempotencyKey(endToEndId)).isZero();
    }

    @Test
    void shouldNotCreateTransferWhenCreditPartyDiffersFromCachedPixKey() throws Exception {
        String endToEndId = "E2E-mismatch";
        cachePixKey(endToEndId, "260", "12345-6", "0001");

        HttpResponse<String> response =
                post("/transfers", transferRequest("260", "99999-9", "0001"), endToEndId, token());

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(countTransfersByIdempotencyKey(endToEndId)).isZero();
    }

    @Test
    void shouldStorePixKeyResponseInRedisWhenKeyLookupSucceeds() throws Exception {
        HttpResponse<String> response = get("/keys/user@example.com", token());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("endToEndId").asText()).isEqualTo(BAAS_DICT_END_TO_END_ID);
        assertThat(BAAS_DICT_QUERY.get()).isEqualTo("key=user@example.com");

        String cachedValue = redisTemplate.opsForValue().get(BAAS_DICT_END_TO_END_ID);
        assertThat(cachedValue).isNotNull();
        JsonNode cached = OBJECT_MAPPER.readTree(cachedValue);
        assertThat(cached.get("endToEndId").asText()).isEqualTo(BAAS_DICT_END_TO_END_ID);
        assertThat(cached.get("account").get("bank").asText()).isEqualTo("260");
    }

    @Test
    void shouldCompleteExternalTransferWhenBaasWebhookSucceeds() throws Exception {
        UUID transferId = insertTransfer("BAAS_REQUESTED");

        HttpResponse<String> response =
                post(
                        "/baas/webhooks/transfers",
                        Map.of("transferId", transferId.toString(), "status", "COMPLETED"),
                        null,
                        null);

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(status(transferId)).isEqualTo("COMPLETED");
        assertThat(readKafkaValue("movement.created", "api-movement").get("transferId").asText())
                .isEqualTo(transferId.toString());
        assertThat(
                        readKafkaValue("balance.capture.command", "api-capture")
                                .get("transferId")
                                .asText())
                .isEqualTo(transferId.toString());
    }

    @Test
    void shouldRejectExternalTransferWhenBaasWebhookRejects() throws Exception {
        UUID transferId = insertTransfer("BAAS_REQUESTED");

        HttpResponse<String> response =
                post(
                        "/baas/webhooks/transfers",
                        Map.of(
                                "transferId",
                                transferId.toString(),
                                "status",
                                "REJECTED",
                                "reason",
                                "bank_rejected"),
                        null,
                        null);

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(status(transferId)).isEqualTo("REJECTED");
        assertThat(rejectionReason(transferId)).isEqualTo("bank_rejected");
        assertThat(
                        readKafkaValue("balance.release.command", "api-release")
                                .get("transferId")
                                .asText())
                .isEqualTo(transferId.toString());
    }

    @Test
    void shouldNotReprocessBaasWebhookWhenTransferIsAlreadyFinal() throws Exception {
        UUID transferId = insertTransfer("COMPLETED");

        HttpResponse<String> response =
                post(
                        "/baas/webhooks/transfers",
                        Map.of("transferId", transferId.toString(), "status", "COMPLETED"),
                        null,
                        null);

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(status(transferId)).isEqualTo("COMPLETED");
    }

    private void cachePixKey(String endToEndId, String bank, String account, String branch) {
        try {
            PixKeyResponse response =
                    new PixKeyResponse(
                            new PixAccountResponse(bank, account, branch),
                            new PixOwnerResponse("Ada", "***.123.456-**"),
                            endToEndId);
            redisTemplate
                    .opsForValue()
                    .set(
                            endToEndId,
                            OBJECT_MAPPER.writeValueAsString(response),
                            Duration.ofHours(12));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private UUID insertTransfer(String status) {
        UUID transferId = UUID.randomUUID();
        jdbcTemplate.update(
                """
				insert into transfers.transfers (
					id, debit_bank, debit_account, debit_branch, credit_bank, credit_account, credit_branch,
					idempotency_key, amount_minor, description, currency, type, status, rejection_reason,
					debit_account_id, credit_account_id, created_at, updated_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
                transferId,
                "13935893",
                "10000-1",
                "0001",
                "260",
                "12345-6",
                "0001",
                "E2E-" + transferId,
                1000,
                "pix",
                "BRL",
                "PIX",
                status,
                null,
                ACCOUNT_ID,
                null,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
        return transferId;
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

    private String token() throws Exception {
        return rsaFixture.token(ACCOUNT_ID);
    }

    private HttpResponse<String> post(
            String path, Map<String, Object> body, String idempotencyKey, String token)
            throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(url(path)))
                        .header("Content-Type", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        OBJECT_MAPPER.writeValueAsString(body)));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return get(path, null);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path))).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpRequest request = builder.build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws IOException {
        return OBJECT_MAPPER.readTree(response.body());
    }

    private static Map<String, Object> transferRequest(String bank, String account, String branch) {
        return Map.of(
                "creditParty", Map.of("bank", bank, "account", account, "branch", branch),
                "amountMinor", 1000,
                "description", "pix",
                "currency", "BRL",
                "type", "PIX");
    }

    private int countTransfersByIdempotencyKey(String idempotencyKey) {
        return jdbcTemplate.queryForObject(
                "select count(*) from transfers.transfers where idempotency_key = ?",
                Integer.class,
                idempotencyKey);
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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static final class RsaFixture {
        private final HttpServer server;
        private final RSAKey key;

        private RsaFixture(HttpServer server, RSAKey key) {
            this.server = server;
            this.key = key;
        }

        private static RsaFixture start() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAKey key =
                    new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                            .privateKey((RSAPrivateKey) keyPair.getPrivate())
                            .keyUse(KeyUse.SIGNATURE)
                            .algorithm(com.nimbusds.jose.Algorithm.parse("RS256"))
                            .keyID("test-key")
                            .build();
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext(
                    "/.well-known/jwks.json",
                    exchange -> {
                        byte[] body =
                                new JWKSet(key.toPublicJWK())
                                        .toString()
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                        exchange.close();
                    });
            server.start();
            return new RsaFixture(server, key);
        }

        private String jwksUri() {
            return "http://localhost:" + server.getAddress().getPort() + "/.well-known/jwks.json";
        }

        private String token(UUID accountId) throws Exception {
            Instant now = Instant.now();
            JWTClaimsSet claims =
                    new JWTClaimsSet.Builder()
                            .issuer(ISSUER)
                            .subject(accountId.toString())
                            .issueTime(Date.from(now))
                            .expirationTime(Date.from(now.plus(Duration.ofMinutes(15))))
                            .claim("account_id", accountId.toString())
                            .claim("customer_id", accountId.toString())
                            .claim("scope", "transfers:create")
                            .build();
            SignedJWT jwt =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.RS256)
                                    .keyID(key.getKeyID())
                                    .type(JOSEObjectType.JWT)
                                    .build(),
                            claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        }

        private void stop() {
            server.stop(0);
        }
    }
}

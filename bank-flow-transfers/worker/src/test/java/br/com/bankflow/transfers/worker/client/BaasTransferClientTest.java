package br.com.bankflow.transfers.worker.client;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.domain.TransferParty;
import br.com.bankflow.transfers.shared.domain.TransferType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class BaasTransferClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendDebitPartyToBaasWhenExternalTransferIsHeld() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/pix/payments",
                exchange -> {
                    requestBody.set(
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
        server.start();
        BaasTransferClient client =
                new BaasTransferClient(
                        WebClient.builder(),
                        "http://localhost:" + server.getAddress().getPort(),
                        Duration.ofSeconds(2));

        client.requestPixPayment(transfer(), Instant.parse("2026-05-20T12:00:00Z"));

        JsonNode json = OBJECT_MAPPER.readTree(requestBody.get());
        assertThat(json.get("debitAccountId").asText())
                .isEqualTo("00000000-0000-0000-0000-000000000101");
        assertThat(json.get("debitParty").get("bank").asText()).isEqualTo("13935893");
        assertThat(json.get("debitParty").get("account").asText()).isEqualTo("10000-1");
        assertThat(json.get("debitParty").get("branch").asText()).isEqualTo("0001");
    }

    private static Transfer transfer() {
        Instant now = Instant.parse("2026-05-20T11:59:00Z");
        return new Transfer(
                UUID.fromString("00000000-0000-0000-0000-000000000202"),
                new TransferParty("13935893", "10000-1", "0001"),
                new TransferParty("260", "12345-6", "0001"),
                "E2E123",
                1000,
                "pix",
                "BRL",
                TransferType.PIX,
                "BALANCE_HOLD_REQUESTED",
                null,
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                null,
                now,
                now);
    }
}

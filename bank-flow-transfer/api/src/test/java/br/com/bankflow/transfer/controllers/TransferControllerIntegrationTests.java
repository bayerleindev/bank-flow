package br.com.bankflow.transfer.controllers;

import br.com.bankflow.transfer.controllers.dtos.CreateTransferRequest;
import br.com.bankflow.transfer.domain.Transfer;
import br.com.bankflow.transfer.domain.TransferStatus;
import br.com.bankflow.transfer.services.TransferOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TransferControllerIntegrationTests {

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @MockitoBean
    private TransferOrchestrationService transferOrchestrationService;

    @Test
    void createsTransferSuccessfully() {
        UUID transferId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();
        CreateTransferRequest request = new CreateTransferRequest(
                sourceId, destId, 1000, "BRL", "test transfer"
        );

        Transfer mockTransfer = new Transfer(
                transferId, "idem-123", sourceId, "1234-5", destId, "6789-0",
                1000, "BRL", "test transfer", null, null, TransferStatus.RECEIVED,
                null, System.currentTimeMillis(), System.currentTimeMillis()
        );

        when(transferOrchestrationService.createTransfer(any(), any())).thenReturn(mockTransfer);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "idem-123");
        HttpEntity<CreateTransferRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/transfers", entity, String.class);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void getsTransferById() {
        UUID transferId = UUID.randomUUID();
        Transfer mockTransfer = new Transfer(
                transferId, "idem-456", UUID.randomUUID(), "1234-5", UUID.randomUUID(), "6789-0",
                500, "BRL", "get test", null, null, TransferStatus.COMPLETED,
                null, System.currentTimeMillis(), System.currentTimeMillis()
        );

        when(transferOrchestrationService.getTransfer(eq(transferId))).thenReturn(mockTransfer);

        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:" + port + "/transfers/" + transferId, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}

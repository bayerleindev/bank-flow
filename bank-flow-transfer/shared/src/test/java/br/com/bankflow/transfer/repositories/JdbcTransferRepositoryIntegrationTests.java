package br.com.bankflow.transfer.repositories;

import br.com.bankflow.transfer.domain.CreateTransferCommand;
import br.com.bankflow.transfer.domain.Transfer;
import br.com.bankflow.transfer.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class JdbcTransferRepositoryIntegrationTests {

    @Autowired
    private JdbcTransferRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM transfers");
    }

    @Test
    void createsAndFindsTransferByTransferId() {
        UUID transferId = UUID.randomUUID();
        CreateTransferCommand command = new CreateTransferCommand(
                "key-1", UUID.randomUUID(), UUID.randomUUID(), 1000, "BRL", "test"
        );
        long now = System.currentTimeMillis();

        Transfer created = repository.create(transferId, command, "1234-5", "6789-0", now);
        assertNotNull(created);
        assertEquals(transferId, created.transferId());
        assertEquals(TransferStatus.RECEIVED, created.status());

        Optional<Transfer> found = repository.findByTransferId(transferId);
        assertTrue(found.isPresent());
        assertEquals("key-1", found.get().idempotencyKey());
    }

    @Test
    void findsTransferByIdempotencyKey() {
        UUID transferId = UUID.randomUUID();
        String idempotencyKey = "idem-key-2";
        CreateTransferCommand command = new CreateTransferCommand(
                idempotencyKey, UUID.randomUUID(), UUID.randomUUID(), 500, "BRL", "test 2"
        );
        repository.create(transferId, command, "source", "dest", System.currentTimeMillis());

        Optional<Transfer> found = repository.findByIdempotencyKey(idempotencyKey);
        assertTrue(found.isPresent());
        assertEquals(transferId, found.get().transferId());
    }

    @Test
    void updatesStatus() {
        UUID transferId = UUID.randomUUID();
        CreateTransferCommand command = new CreateTransferCommand(
                "key-3", UUID.randomUUID(), UUID.randomUUID(), 100, "BRL", "test 3"
        );
        repository.create(transferId, command, "s", "d", System.currentTimeMillis());

        Transfer updated = repository.updateStatus(transferId, TransferStatus.COMPLETED, null, System.currentTimeMillis());
        assertEquals(TransferStatus.COMPLETED, updated.status());

        Optional<Transfer> found = repository.findByTransferId(transferId);
        assertEquals(TransferStatus.COMPLETED, found.get().status());
    }

    @Test
    void updatesHold() {
        UUID transferId = UUID.randomUUID();
        CreateTransferCommand command = new CreateTransferCommand(
                "key-4", UUID.randomUUID(), UUID.randomUUID(), 100, "BRL", "test 4"
        );
        repository.create(transferId, command, "s", "d", System.currentTimeMillis());

        String holdId = "hold-123";
        Transfer updated = repository.updateHold(transferId, holdId, TransferStatus.HOLD_CREATED, System.currentTimeMillis());
        assertEquals(holdId, updated.holdId());
        assertEquals(TransferStatus.HOLD_CREATED, updated.status());
    }

    @Test
    void updatesPspPayment() {
        UUID transferId = UUID.randomUUID();
        CreateTransferCommand command = new CreateTransferCommand(
                "key-5", UUID.randomUUID(), UUID.randomUUID(), 100, "BRL", "test 5"
        );
        repository.create(transferId, command, "s", "d", System.currentTimeMillis());

        String pspPaymentId = "psp-pay-789";
        Transfer updated = repository.updatePspPayment(transferId, pspPaymentId, TransferStatus.PSP_PENDING, System.currentTimeMillis());
        assertEquals(pspPaymentId, updated.pspPaymentId());
        assertEquals(TransferStatus.PSP_PENDING, updated.status());
    }
}

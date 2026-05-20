package br.com.bankflow.transfers.api.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.bankflow.transfers.api.dto.request.BaasTransferWebhookRequest;
import br.com.bankflow.transfers.api.dto.request.BaasTransferWebhookStatus;
import br.com.bankflow.transfers.api.producer.TransferSettlementProducer;
import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.domain.TransferParty;
import br.com.bankflow.transfers.shared.domain.TransferType;
import br.com.bankflow.transfers.shared.repository.TransferRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class BaasTransferWebhookServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-20T12:00:00Z");
    private static final UUID TRANSFER_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Mock private TransferRepository transferRepository;
    @Mock private TransferSettlementProducer transferSettlementProducer;

    @Test
    void shouldCompleteTransferAndPublishCaptureWhenBaasWebhookSucceeds() {
        BaasTransferWebhookService service = service();
        Transfer transfer = transfer("BAAS_REQUESTED");
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(transferRepository.complete(TRANSFER_ID, "BAAS_REQUESTED", NOW)).thenReturn(true);

        service.handle(
                new BaasTransferWebhookRequest(
                        TRANSFER_ID, BaasTransferWebhookStatus.COMPLETED, null));

        verify(transferSettlementProducer).publishSuccessfulSettlement(transfer, NOW);
        verify(transferRepository).complete(TRANSFER_ID, "BAAS_REQUESTED", NOW);
    }

    @Test
    void shouldRejectTransferAndPublishReleaseWhenBaasWebhookRejects() {
        BaasTransferWebhookService service = service();
        Transfer transfer = transfer("BAAS_REQUESTED");
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(transferRepository.reject(TRANSFER_ID, "BAAS_REQUESTED", "bank_rejected", NOW))
                .thenReturn(true);

        service.handle(
                new BaasTransferWebhookRequest(
                        TRANSFER_ID, BaasTransferWebhookStatus.REJECTED, "bank_rejected"));

        verify(transferSettlementProducer).publishRejectedSettlement(transfer, NOW);
        verify(transferRepository).reject(TRANSFER_ID, "BAAS_REQUESTED", "bank_rejected", NOW);
    }

    @Test
    void shouldNotProcessBaasWebhookWhenTransferIsAlreadyFinal() {
        BaasTransferWebhookService service = service();
        Transfer transfer = transfer("COMPLETED");
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));

        service.handle(
                new BaasTransferWebhookRequest(
                        TRANSFER_ID, BaasTransferWebhookStatus.COMPLETED, null));

        verify(transferSettlementProducer, never()).publishSuccessfulSettlement(transfer, NOW);
        verify(transferSettlementProducer, never()).publishRejectedSettlement(transfer, NOW);
    }

    private BaasTransferWebhookService service() {
        return new BaasTransferWebhookService(
                transferRepository, transferSettlementProducer, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Transfer transfer(String status) {
        Instant createdAt = Instant.parse("2026-05-20T11:59:00Z");
        return new Transfer(
                TRANSFER_ID,
                new TransferParty("13935893", "10000-1", "0001"),
                new TransferParty("260", "12345-6", "0001"),
                "E2E123",
                1000,
                "pix",
                "BRL",
                TransferType.PIX,
                status,
                null,
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                null,
                createdAt,
                createdAt);
    }
}

package br.com.bankflow.transfers.api.service;

import br.com.bankflow.transfers.api.dto.request.BaasTransferWebhookRequest;
import br.com.bankflow.transfers.api.dto.request.BaasTransferWebhookStatus;
import br.com.bankflow.transfers.api.producer.TransferSettlementProducer;
import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.repository.TransferRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BaasTransferWebhookService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaasTransferWebhookService.class);
    private static final String BAAS_REQUESTED_STATUS = "BAAS_REQUESTED";
    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final String REJECTED_STATUS = "REJECTED";
    private static final String DEFAULT_REJECTION_REASON = "baas_rejected";

    private final TransferRepository transferRepository;
    private final TransferSettlementProducer transferSettlementProducer;
    private final Clock clock;

    public BaasTransferWebhookService(
            TransferRepository transferRepository,
            TransferSettlementProducer transferSettlementProducer,
            Clock clock) {
        this.transferRepository = transferRepository;
        this.transferSettlementProducer = transferSettlementProducer;
        this.clock = clock;
    }

    @Transactional
    public void handle(BaasTransferWebhookRequest request) {
        Transfer transfer =
                transferRepository
                        .findById(request.transferId())
                        .orElseThrow(() -> new TransferNotFoundException(request.transferId()));

        if (isFinalStatus(transfer.status())) {
            LOGGER.info(
                    "baas transfer webhook ignored because transfer is already final transferId={} status={}",
                    transfer.id(),
                    transfer.status());
            return;
        }

        if (!BAAS_REQUESTED_STATUS.equals(transfer.status())) {
            LOGGER.info(
                    "baas transfer webhook ignored because transfer is not in baas requested status transferId={} currentStatus={}",
                    transfer.id(),
                    transfer.status());
            return;
        }

        Instant now = Instant.now(clock);
        if (request.status() == BaasTransferWebhookStatus.COMPLETED) {
            completeTransfer(transfer, now);
            return;
        }

        rejectTransfer(transfer, rejectionReason(request), now);
    }

    private void completeTransfer(Transfer transfer, Instant now) {
        transferSettlementProducer.publishSuccessfulSettlement(transfer, now);
        boolean transitioned =
                transferRepository.complete(transfer.id(), BAAS_REQUESTED_STATUS, now);
        if (!transitioned) {
            throw new IllegalStateException("Transfer could not move to " + COMPLETED_STATUS);
        }
        LOGGER.info("baas transfer completed transferId={}", transfer.id());
    }

    private void rejectTransfer(Transfer transfer, String reason, Instant now) {
        transferSettlementProducer.publishRejectedSettlement(transfer, now);
        boolean transitioned =
                transferRepository.reject(transfer.id(), BAAS_REQUESTED_STATUS, reason, now);
        if (!transitioned) {
            throw new IllegalStateException("Transfer could not move to " + REJECTED_STATUS);
        }
        LOGGER.info("baas transfer rejected transferId={} reason={}", transfer.id(), reason);
    }

    private boolean isFinalStatus(String status) {
        return COMPLETED_STATUS.equals(status) || REJECTED_STATUS.equals(status);
    }

    private String rejectionReason(BaasTransferWebhookRequest request) {
        if (request.reason() == null || request.reason().isBlank()) {
            return DEFAULT_REJECTION_REASON;
        }
        return request.reason();
    }
}

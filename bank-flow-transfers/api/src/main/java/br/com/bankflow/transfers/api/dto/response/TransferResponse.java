package br.com.bankflow.transfers.api.dto.response;

import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.domain.TransferType;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        TransferPartyResponse debitParty,
        TransferPartyResponse creditParty,
        String idempotencyKey,
        long amountMinor,
        String description,
        String currency,
        TransferType type,
        String status,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.id(),
                TransferPartyResponse.from(transfer.debitParty()),
                TransferPartyResponse.from(transfer.creditParty()),
                transfer.idempotencyKey(),
                transfer.amountMinor(),
                transfer.description(),
                transfer.currency(),
                transfer.type(),
                transfer.status(),
                transfer.rejectionReason(),
                transfer.createdAt(),
                transfer.updatedAt());
    }
}

package br.com.bankflow.transfers.api.dto.response;

import br.com.bankflow.transfers.shared.domain.Transfer;
import java.util.UUID;

public record CreateTransferResponse(UUID id, String status) {

    public static CreateTransferResponse from(Transfer transfer) {
        return new CreateTransferResponse(transfer.id(), transfer.status());
    }
}

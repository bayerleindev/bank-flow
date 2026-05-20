package br.com.bankflow.transfers.api.dto.request;

import br.com.bankflow.transfers.api.service.CreateTransferCommand;
import br.com.bankflow.transfers.shared.domain.TransferParty;
import br.com.bankflow.transfers.shared.domain.TransferType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateTransferRequest(
        @Valid @NotNull TransferPartyRequest creditParty,
        @Positive long amountMinor,
        @Size(max = 255) String description,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull TransferType type) {

    public CreateTransferCommand toCommand(String idempotencyKey, UUID debitAccountId) {
        return new CreateTransferCommand(
                debitAccountId,
                creditParty.toDomain(),
                idempotencyKey,
                amountMinor,
                description,
                currency,
                type);
    }

    public record TransferPartyRequest(
            @NotBlank @Size(max = 32) String bank,
            @NotBlank @Size(max = 64) String account,
            @NotBlank @Size(max = 32) String branch) {

        public TransferParty toDomain() {
            return new TransferParty(bank, account, branch);
        }
    }
}

package br.com.bankflow.transfers.api.dto.request;

import br.com.bankflow.transfers.api.service.CreateTransferCommand;
import br.com.bankflow.transfers.shared.domain.TransferParty;
import br.com.bankflow.transfers.shared.domain.TransferType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTransferRequest(
        @Valid @NotNull TransferPartyRequest debitParty,
        @Valid @NotNull TransferPartyRequest creditParty,
        @Positive long amountMinor,
        @Size(max = 255) String description,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull TransferType type) {

    private static final String BANK_FLOW_ISPB = "13935893";

    public CreateTransferCommand toCommand(String idempotencyKey) {
        return new CreateTransferCommand(
                debitParty.toDebitPartyDomain(),
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

        public TransferParty toDebitPartyDomain() {
            return new TransferParty(BANK_FLOW_ISPB, account, branch);
        }
    }
}

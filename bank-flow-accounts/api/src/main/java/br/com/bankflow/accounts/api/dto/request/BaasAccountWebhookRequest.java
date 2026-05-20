package br.com.bankflow.accounts.api.dto.request;

import br.com.bankflow.accounts.shared.domain.AccountStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BaasAccountWebhookRequest(
        @NotNull UUID accountId,
        @NotNull AccountStatus status,
        String branchNumber,
        String accountNumber,
        String accountDigit,
        String rejectionReason) {

    @AssertTrue(message = "status must be ACTIVE or REJECTED") public boolean isSupportedStatus() {
        return status == AccountStatus.ACTIVE || status == AccountStatus.REJECTED;
    }

    @AssertTrue(
            message =
                    "branchNumber, accountNumber and accountDigit are required when status is ACTIVE")
    public boolean isActivePayloadValid() {
        if (status != AccountStatus.ACTIVE) {
            return true;
        }
        return hasText(branchNumber) && hasText(accountNumber) && hasText(accountDigit);
    }

    @AssertTrue(message = "rejectionReason is required when status is REJECTED") public boolean isRejectedPayloadValid() {
        return status != AccountStatus.REJECTED || hasText(rejectionReason);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

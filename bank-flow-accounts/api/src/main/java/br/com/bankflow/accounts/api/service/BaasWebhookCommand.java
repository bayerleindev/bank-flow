package br.com.bankflow.accounts.api.service;

import br.com.bankflow.accounts.api.dto.request.BaasAccountWebhookRequest;
import br.com.bankflow.accounts.shared.domain.AccountStatus;
import java.util.UUID;

public record BaasWebhookCommand(
        UUID accountId,
        AccountStatus status,
        String branchNumber,
        String accountNumber,
        String accountDigit,
        String rejectionReason) {

    public static BaasWebhookCommand from(BaasAccountWebhookRequest request) {
        return new BaasWebhookCommand(
                request.accountId(),
                request.status(),
                request.branchNumber(),
                request.accountNumber(),
                request.accountDigit(),
                request.rejectionReason());
    }
}

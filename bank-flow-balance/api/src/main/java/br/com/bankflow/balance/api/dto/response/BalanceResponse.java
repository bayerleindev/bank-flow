package br.com.bankflow.balance.api.dto.response;

import br.com.bankflow.balance.shared.domain.Balance;
import java.time.Instant;
import java.util.UUID;

public record BalanceResponse(
        UUID accountId,
        String currency,
        long totalBalance,
        long availableBalance,
        long holdBalance,
        Instant updatedAt) {

    public static BalanceResponse from(Balance balance) {
        return new BalanceResponse(
                balance.accountId(),
                balance.currency(),
                balance.totalAmountMinor(),
                balance.availableAmountMinor(),
                balance.heldAmountMinor(),
                balance.updatedAt());
    }
}

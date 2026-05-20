package br.com.bankflow.balance.shared.domain;

import java.time.Instant;
import java.util.UUID;

public record Balance(
        UUID accountId,
        String currency,
        long totalAmountMinor,
        long heldAmountMinor,
        Instant updatedAt) {

    public long availableAmountMinor() {
        return totalAmountMinor - heldAmountMinor;
    }
}

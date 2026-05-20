package br.com.bankflow.balance.shared.kafka;

import br.com.bankflow.balance.shared.domain.BalanceHoldStatus;
import java.time.Instant;
import java.util.UUID;

public record BalanceHeldEvent(
        UUID transferId,
        BalanceHoldStatus status,
        String reason,
        UUID accountId,
        long amountMinor,
        String currency,
        Instant heldAt) {}

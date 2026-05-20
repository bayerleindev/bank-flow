package br.com.bankflow.transfers.shared.kafka;

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

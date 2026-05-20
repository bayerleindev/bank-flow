package br.com.bankflow.balance.shared.kafka;

import java.time.Instant;
import java.util.UUID;

public record BalanceReleaseCommand(
        UUID transferId, UUID accountId, long amountMinor, String currency, Instant requestedAt) {}

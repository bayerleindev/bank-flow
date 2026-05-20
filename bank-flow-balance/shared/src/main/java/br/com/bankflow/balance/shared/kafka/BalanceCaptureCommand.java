package br.com.bankflow.balance.shared.kafka;

import java.time.Instant;
import java.util.UUID;

public record BalanceCaptureCommand(
        UUID transferId, UUID accountId, long amountMinor, String currency, Instant requestedAt) {}

package br.com.bankflow.transfers.shared.kafka;

import java.time.Instant;
import java.util.UUID;

public record MovementCreatedEvent(
        UUID movementId,
        UUID transferId,
        UUID debitAccountId,
        UUID creditAccountId,
        long amountMinor,
        String currency,
        String type,
        Instant createdAt) {}

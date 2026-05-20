package br.com.bankflow.transfers.shared.kafka;

import java.time.Instant;
import java.util.UUID;

public record AccountValidatedEvent(
        UUID transferId,
        AccountValidationStatus status,
        String reason,
        UUID debitAccountId,
        UUID creditAccountId,
        Instant validatedAt) {}

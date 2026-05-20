package br.com.bankflow.transfers.shared.domain;

import java.time.Instant;
import java.util.UUID;

public record Transfer(
        UUID id,
        TransferParty debitParty,
        TransferParty creditParty,
        String idempotencyKey,
        long amountMinor,
        String description,
        String currency,
        TransferType type,
        String status,
        String rejectionReason,
        UUID debitAccountId,
        UUID creditAccountId,
        Instant createdAt,
        Instant updatedAt) {}

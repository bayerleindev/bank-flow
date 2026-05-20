package br.com.bankflow.transfers.shared.kafka;

import br.com.bankflow.transfers.shared.domain.TransferParty;
import br.com.bankflow.transfers.shared.domain.TransferType;
import java.time.Instant;
import java.util.UUID;

public record TransferRequestedEvent(
        UUID id,
        UUID debitAccountId,
        TransferParty creditParty,
        String idempotencyKey,
        long amountMinor,
        String description,
        String currency,
        TransferType type,
        String status,
        Instant createdAt) {}

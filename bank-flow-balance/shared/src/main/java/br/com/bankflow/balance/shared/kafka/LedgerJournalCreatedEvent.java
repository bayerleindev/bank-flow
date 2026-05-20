package br.com.bankflow.balance.shared.kafka;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LedgerJournalCreatedEvent(
        UUID movementId,
        UUID transferId,
        long amountMinor,
        String currency,
        String type,
        Instant createdAt,
        List<LedgerJournalEntryCreatedEvent> entries) {}

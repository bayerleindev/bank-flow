package br.com.bankflow.ledger.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Journal(
        UUID movementId,
        UUID transferId,
        long amountMinor,
        String currency,
        String type,
        Instant createdAt,
        List<JournalEntry> entries) {}

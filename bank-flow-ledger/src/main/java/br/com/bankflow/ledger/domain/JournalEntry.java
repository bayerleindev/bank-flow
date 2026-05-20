package br.com.bankflow.ledger.domain;

import java.util.UUID;

public record JournalEntry(
        UUID movementId,
        UUID accountId,
        JournalEntrySide side,
        long amountMinor,
        String currency) {}

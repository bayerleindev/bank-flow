package br.com.bankflow.ledger.kafka;

import br.com.bankflow.ledger.domain.JournalEntrySide;
import java.util.UUID;

public record LedgerJournalEntryCreatedEvent(
        UUID movementId,
        UUID accountId,
        JournalEntrySide side,
        long amountMinor,
        String currency) {}

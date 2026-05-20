package br.com.bankflow.balance.shared.kafka;

import br.com.bankflow.balance.shared.domain.JournalEntrySide;
import java.util.UUID;

public record LedgerJournalEntryCreatedEvent(
        UUID movementId,
        UUID accountId,
        JournalEntrySide side,
        long amountMinor,
        String currency) {}

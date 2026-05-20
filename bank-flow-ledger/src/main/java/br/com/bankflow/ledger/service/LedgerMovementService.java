package br.com.bankflow.ledger.service;

import br.com.bankflow.ledger.domain.Journal;
import br.com.bankflow.ledger.domain.JournalEntry;
import br.com.bankflow.ledger.domain.JournalEntrySide;
import br.com.bankflow.ledger.kafka.MovementCreatedEvent;
import br.com.bankflow.ledger.producer.LedgerEventProducer;
import br.com.bankflow.ledger.repository.LedgerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LedgerMovementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LedgerMovementService.class);

    private final LedgerRepository ledgerRepository;
    private final LedgerEventProducer ledgerEventProducer;
    private final Clock clock;

    public LedgerMovementService(
            LedgerRepository ledgerRepository,
            LedgerEventProducer ledgerEventProducer,
            Clock clock) {
        this.ledgerRepository = ledgerRepository;
        this.ledgerEventProducer = ledgerEventProducer;
        this.clock = clock;
    }

    public void createJournal(MovementCreatedEvent event) {
        if (event.amountMinor() <= 0) {
            LOGGER.warn(
                    "movement ignored because amount is not positive movementId={} amountMinor={}",
                    event.movementId(),
                    event.amountMinor());
            return;
        }

        if (ledgerRepository.movementAlreadyPosted(event.movementId())) {
            LOGGER.info("duplicated movement ignored movementId={}", event.movementId());
            return;
        }

        if (!ledgerRepository.existsAccount(event.debitAccountId())) {
            LOGGER.warn(
                    "movement ignored because debit account does not exist movementId={} accountId={}",
                    event.movementId(),
                    event.debitAccountId());
            return;
        }

        if (!ledgerRepository.existsAccount(event.creditAccountId())) {
            LOGGER.warn(
                    "movement ignored because credit account does not exist movementId={} accountId={}",
                    event.movementId(),
                    event.creditAccountId());
            return;
        }

        Journal journal =
                new Journal(
                        event.movementId(),
                        event.transferId(),
                        event.amountMinor(),
                        event.currency(),
                        event.type(),
                        resolvedCreatedAt(event),
                        List.of(
                                new JournalEntry(
                                        event.movementId(),
                                        event.debitAccountId(),
                                        JournalEntrySide.DEBIT,
                                        event.amountMinor(),
                                        event.currency()),
                                new JournalEntry(
                                        event.movementId(),
                                        event.creditAccountId(),
                                        JournalEntrySide.CREDIT,
                                        event.amountMinor(),
                                        event.currency())));

        ledgerRepository.saveJournal(journal);
        ledgerEventProducer.publishJournalCreated(journal);
        LOGGER.info("double-entry journal created movementId={}", event.movementId());
    }

    private Instant resolvedCreatedAt(MovementCreatedEvent event) {
        if (event.createdAt() == null) {
            return Instant.now(clock);
        }
        return event.createdAt();
    }
}

package br.com.bankflow.ledger.producer;

import br.com.bankflow.ledger.domain.Journal;
import br.com.bankflow.ledger.kafka.LedgerJournalCreatedEvent;
import br.com.bankflow.ledger.kafka.LedgerJournalEntryCreatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class LedgerEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String ledgerJournalCreatedTopic;

    public LedgerEventProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.ledger-journal-created}") String ledgerJournalCreatedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.ledgerJournalCreatedTopic = ledgerJournalCreatedTopic;
    }

    public void publishJournalCreated(Journal journal) {
        LedgerJournalCreatedEvent event =
                new LedgerJournalCreatedEvent(
                        journal.movementId(),
                        journal.transferId(),
                        journal.amountMinor(),
                        journal.currency(),
                        journal.type(),
                        journal.createdAt(),
                        journal.entries().stream()
                                .map(
                                        entry ->
                                                new LedgerJournalEntryCreatedEvent(
                                                        entry.movementId(),
                                                        entry.accountId(),
                                                        entry.side(),
                                                        entry.amountMinor(),
                                                        entry.currency()))
                                .toList());

        kafkaTemplate
                .send(ledgerJournalCreatedTopic, journal.transferId().toString(), event)
                .join();
    }
}

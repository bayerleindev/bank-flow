package br.com.bankflow.balance.worker.consumer;

import br.com.bankflow.balance.shared.kafka.LedgerJournalCreatedEvent;
import br.com.bankflow.balance.worker.service.BalanceProjectionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LedgerJournalCreatedConsumer {

    private final BalanceProjectionService balanceProjectionService;

    public LedgerJournalCreatedConsumer(BalanceProjectionService balanceProjectionService) {
        this.balanceProjectionService = balanceProjectionService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.ledger-journal-created}",
            containerFactory = "ledgerJournalCreatedKafkaListenerContainerFactory")
    public void consume(LedgerJournalCreatedEvent event) {
        balanceProjectionService.applyJournal(event);
    }
}
